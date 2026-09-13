# Assistant — native tool calling oficial

**Estado:** OFICIAL desde `C2-cu08-fix-014`.

El Assistant de ClassForge usa Qwen2.5-3B-Instruct Q4_K_M mediante llama.cpp native function calling. El antiguo planner que pedía al LLM serializar directamente `AssistantSemanticPlan` fue retirado del runtime.

## Pipeline oficial

```text
texto / transcript de Whisper
 -> AssistantNativeToolPlanner
 -> route_uml_request (catalogo ligero)
 -> AssistantToolRouteAdjudicator (invariantes del proyecto)
 -> DynamicUmlToolCatalog (una tool pesada por step)
 -> llama.cpp /v1/chat/completions + tools
 -> native tool_call
 -> UmlToolCallResolver
 -> referencias existentes grounded -> UUID
 -> nombres nuevos literales
 -> AssistantSemanticPlan interno
 -> compiler de defaults + grounding final + normalizer
 -> UmlAssistantCommandResolver
 -> preview
 -> Apply
 -> Command Bus / STOMP
```

`AssistantSemanticPlan` y `AssistantPlanAction` permanecen como IR interna; ya no son un protocolo que deba generar el LLM.

## Responsabilidades

- Qwen decide la operación semántica y los argumentos libres de la tool.
- ClassForge resuelve referencias existentes, typos, provenance y UUIDs.
- Los nombres nuevos se recuperan literalmente del texto del usuario.
- El LLM nunca recibe autoridad de escritura.
- Preview/validator/Apply/Command Bus siguen siendo la única ruta de mutación.

## Tools

Las interfaces externas son pequeñas y semánticas: `create_class`, `rename_class`, `delete_class`, `add_attributes`, `rename_attribute`, `update_attribute_properties`, `delete_attribute`, `create_association`, `create_aggregation`, `create_composition`, `create_generalization`, `set_relationship_multiplicity`, `change_relationship_type`, `delete_relationship`.

`route_uml_request` es exclusivamente control de routing y nunca genera una acción UML. `finish_plan` queda como compatibilidad histórica del enum, pero no participa en el flujo oficial de fix-014.

## Peticiones compuestas

Una petición como:

```text
Crea Cliente, agregale email STRING y relaciona Cliente con Factura
```

primero se clasifica en una ruta pequeña y ordenada (`create_class -> add_attributes -> create_association`). Cada step expone una sola tool pesada. El resultado aceptado se proyecta sobre un `ProjectDocument` efímero usando el mismo `UmlAssistantCommandResolver`; los UUIDs creados por preview funcionan como identidades temporales del plan. El proyecto persistido no cambia.

Si Qwen devuelve varias llamadas durante un step, ClassForge conserva únicamente la primera llamada compatible con la tool esperada. Desde fix-014 v1.5 los steps no reenvían historial `assistant/tool`: el estado entre rondas vive exclusivamente en el `ProjectDocument` efímero y en el catálogo dinámico reconstruido a partir de él. Esto reduce contexto y evita incompatibilidades del chat template sin perder identidades creadas en steps previos.

La ruta final se resuelve de nuevo como un único BATCH desde el documento original antes de devolver el preview al usuario.

## Texto y voz

Voz solo añade la etapa `Whisper -> transcript`. `AssistantPlanService.plan` y `planVoice` convergen en la misma instancia de `AssistantNativeToolPlanner`; no existe un planner alternativo para audio.

## Fail closed

Las referencias existentes solo se aceptan si el backend puede ligarlas al texto original y a un elemento real del `ProjectDocument`. Safety mantiene la regla: un solo `UNSAFE_ACCEPT` es ERROR.

## Runtime

Baseline de desarrollo:

```powershell
llama-server.exe `
  -hf bartowski/Qwen2.5-3B-Instruct-GGUF:Q4_K_M `
  --device Vulkan0 `
  --parallel 1 `
  --host 127.0.0.1 `
  --port 8092 `
  --alias local-model `
  -c 4096 `
  --jinja
```

El gateway exige `supports_tools=true` y `supports_tool_calls=true`, usa temperatura 0 y `parallel_tool_calls=false`.

## Evidencia de cutover

Benchmark A/B previo al cutover, 20 intentos por categoría:

```text
legacy overall: 94.0 %
tools overall:  100.0 %
safety tools:   100.0 %
```

Las diez categorías conocidas quedaron en 100 % con tools. Desde fix-014 se conservan dos suites: `regression`, para evitar regresiones de esas operaciones, y `holdout`, con redacciones nuevas más un escenario compuesto.

## Código legacy retirado

Se eliminaron del runtime `LlamaLanguageModelGateway`, `LanguageModelGateway`, `AssistantPlanningRouter` y `AssistantPlannerMode`, además de sus tests/prompts específicos. El E2E ya no acepta `plannerMode`: siempre prueba la arquitectura oficial.


<!-- CU08-FIX-014-V1.3-HIERARCHICAL-ROUTING -->
## Hardening holdout: routing jerarquico antes del catalogo pesado

La primera suite holdout de fix-014 detecto una debilidad que el benchmark de regresion no mostraba: cuando una redaccion nueva no producia un `IntentHint` determinista, el fallback exponia todas las tools con todos los enums del proyecto. En el fixture medio eso generaba prompts de ~5.1k tokens y excedia `-c 4096` antes de inferir.

Desde fix-014 v1.3 el runtime usa dos niveles de native function calling:

```text
usuario
  -> route_uml_request                 # schema pequeno, sin clases/atributos/relaciones
  -> steps ordenados                   # p.ej. create_class, add_attributes, create_association
  -> una tool UML pesada por step      # catalogo dinamico del ProjectDocument actual
  -> resolve/provenance/UUID
  -> compiler + grounding + normalizer
  -> preview efimero
  -> siguiente step
  -> BATCH final / preview / Apply
```

El router no recibe enums del proyecto y por eso su costo de contexto es acotado aunque la redaccion sea nueva. El segundo nivel expone exactamente una tool UML por step. `parallel_tool_calls=false` sigue activo, pero si Qwen devuelve varias llamadas de la misma tool ClassForge ejecuta la primera y responde `DEFERRED_REPLAN` a las restantes en vez de abortar la planificacion.

`finish_plan` deja de ser necesario para completar el flujo oficial: la lista ordenada devuelta por `route_uml_request` determina cuantos steps deben resolverse. Los previews efimeros siguen siendo la fuente de identidad para simbolos creados en steps anteriores.

Las multiplicidades escritas explicitamente por el usuario (`0..*`, `1..*`) y expresiones inequívocas como `cero/ninguna + muchas/varias` prevalecen sobre un upper/lower distinto propuesto por el LLM.


<!-- CU08-FIX-014-V1.4-ROUTE-ADJUDICATION -->
## Hardening holdout v1.4: adjudicacion project-aware y tolerancia a burst

La segunda corrida holdout posterior a v1.3 subio de 39.4 % a 57.6 % y elimino los overflows de contexto; safety permanecio en 100 %. Los fallos restantes revelaron que el router podia insertar pasos extra en peticiones simples y que Qwen2.5-3B podia repetir 20+ llamadas de la unica tool expuesta o, excepcionalmente, responder texto aun con `tool_choice=required`.

Fix-014 v1.4 introduce `AssistantToolRouteAdjudicator`:

- una peticion simple termina en exactamente una familia de operacion;
- referencias/atributos ya grounded y semantica explicita de relaciones corrigen rutas incompatibles;
- peticiones compuestas conservan una ruta dependency-ordered;
- una referencia desconocida con lenguaje de relacion permanece en la familia relationship para que `UmlToolCallResolver` falle cerrado;
- llamadas repetidas del mismo step se descartan y no entran al historial;
- si llama.cpp devuelve contenido conversacional sin `tool_calls`, el gateway hace un unico reintento native-tools mas estricto;
- `max_tokens` del step baja a 256 para acotar bursts sin limitar los argumentos esperados.

El objetivo no es codificar frases del benchmark, sino impedir efectos laterales no solicitados: Qwen propone semantica; ClassForge adjudica la familia compatible con el texto y el `ProjectDocument`, y el resolver sigue siendo la autoridad de referencias/UUID/provenance.

<!-- CU08-FIX-014-V1.5-STATELESS-COMPOUND -->
## Hardening holdout v1.5: compound stateless y semántica explícita

La corrida holdout posterior a v1.4 alcanzó `28/33 = 84.8 %`, con `SAFETY_UNKNOWN_REFERENCE=100 %`. Ocho de once categorías quedaron en 100 %. Los cinco fallos restantes se concentraron en tres causas: una frase de creación (`Añade ... una nueva clase`) era detectada falsamente como `CREATE_CLASS + ADD_ATTRIBUTES`; la selección de extremo de multiplicidad no puntuaba `ninguna/varias`; y las tres peticiones compuestas fallaban dentro del gateway al reutilizar historial native tool entre steps.

Fix-014 v1.5 mantiene el router jerárquico pero hace cada step compuesto stateless a nivel de chat: `ProjectDocument` efímero + catálogo dinámico son la única memoria entre rondas. También distingue el verbo `añade/agrega` que gobierna una `nueva clase` de un verdadero agregado de atributo, y amplía las cues de multiplicidad con `ningun*/vari*`. El gateway conserva un diagnóstico de excepción más preciso si llama.cpp vuelve a fallar. En ese punto histórico CU-09 seguía condicionado a una nueva corrida holdout y regression; esa condición fue satisfecha después y CU-09 está CERRADO.

<!-- CU08-FIX-014-V1.6-LAZY-TOOL-PARSE -->
## Hardening holdout v1.6: parse perezoso de bursts y retry de truncamiento

La corrida holdout posterior a v1.5 alcanzo `30/33 = 90.9 %`: las diez categorias simples, incluida `SAFETY_UNKNOWN_REFERENCE`, quedaron en `100 %`; el unico ERROR restante fue `MULTI_TOOL_COMPOUND`. Los tres fallos compartieron `UnexpectedEndOfInputException` al parsear JSON de tool arguments.

La causa estaba en el gateway: aunque el planner solo necesitaba la primera llamada compatible de cada step, `LlamaNativeToolCallingGateway` parseaba todas las `tool_calls` antes de devolverlas. Qwen puede emitir una rafaga repetida aun con `parallel_tool_calls=false`; con `max_tokens=256`, el tail de esa rafaga puede quedar cortado dentro del string `function.arguments`. Una primera llamada completa quedaba inutilizada porque Jackson intentaba parsear tambien una llamada sobrante truncada.

Desde v1.6 el gateway:

1. calcula las tools realmente expuestas por el catalogo actual;
2. recorre la respuesta hasta encontrar la primera llamada a una tool expuesta;
3. parsea solo esa llamada y retorna inmediatamente, sin tocar el tail repetido;
4. si precisamente esa primera llamada o el envelope HTTP llegan truncados, reintenta una sola vez con `max_tokens=512` y una instruccion estricta de una unica tool call completa;
5. si el retry tambien llega truncado, falla explicitamente y conserva fail-closed.

El presupuesto normal sigue en 256 tokens; 512 se usa solamente como recuperacion de truncamiento. No se aumenta `-c 4096` ni se reintroduce historial entre steps.

<!-- CU08-FIX-014-V1.6-FINAL-HOLDOUT -->
## Validación holdout final

Después de v1.6 la suite holdout alcanzó `33/33 = 100.0 %`: todas las operaciones simples, referencias desconocidas y `MULTI_TOOL_COMPOUND` aprobaron. Safety permaneció en `100 %` y la ejecución terminó con build verde. Esta es la evidencia vigente de generalización del planner native tools; la regression post-v1.6 se conserva como checkpoint final de no regresión antes de abrir CU-09.
