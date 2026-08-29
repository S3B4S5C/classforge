# Assistant — native tool calling y resolución semántica

**Estado:** EXPERIMENTAL / CANDIDATO durante `C2-cu08-fix-013`.

Este documento describe el camino candidato para sustituir la generación directa de `AssistantSemanticPlan` por function calling nativo de llama.cpp con un modelo tool-aware (baseline de migración validado en hardware de desarrollo: Qwen2.5-3B-Instruct Q4_K_M GGUF).

## Objetivo

El LLM deja de construir el DTO polimórfico interno de ClassForge. En su lugar selecciona herramientas pequeñas del dominio UML:

```text
create_class
rename_class
delete_class
add_attributes
update_attribute
delete_attribute
create_relationship
update_relationship
delete_relationship
```

El flujo candidato es:

```text
texto / transcript
 -> IntentHint conservador
 -> DynamicUmlToolCatalog
 -> llama.cpp /v1/chat/completions + tools
 -> native tool_calls
 -> UmlToolCallResolver
 -> referencias canónicas / UUID reales
 -> AssistantSemanticPlan interno
 -> grounding / normalizer
 -> UmlAssistantCommandResolver
 -> preview
 -> Apply
 -> Command Bus / STOMP
```

`AssistantSemanticPlan` continúa existiendo como representación interna compatible con preview/Apply, pero deja de ser el contrato que el LLM debe serializar en modo `tools`.

## Modos

```text
classforge.assistant.planner-mode=legacy  # default durante fix-013
classforge.assistant.planner-mode=tools
classforge.assistant.planner-mode=compare
```

- `legacy`: mantiene el planner JSON Schema previo.
- `tools`: usa function calling nativo y catálogo dinámico.
- `compare`: ejecuta ambos para diagnóstico y entrega el plan de tools; no es modo recomendado para uso normal porque duplica inferencias.

El cutover definitivo queda reservado a `C2-cu08-fix-014` después del benchmark A/B.

## Referencias existentes vs nombres nuevos

La separación es obligatoria:

```text
referencia existente -> catálogo canónico -> UUID real -> fail closed
nombre nuevo          -> string libre -> se preserva literalmente
```

Ejemplos:

```text
"Renombra 4nimal..."      -> referencia a Animal existente
"Crea la clase 4nimal"    -> nombre nuevo "4nimal"
```

Las tools de clases existentes reciben enums dinámicos con los nombres actuales. Los atributos existentes se publican como `Clase.atributo`. Las relaciones existentes reciben referencias enumeradas derivadas del `ProjectDocument` actual.

## Seguridad

Una tool call no tiene autoridad de escritura y tampoco convierte una referencia inventada en válida.

Antes del preview:

1. la tool debe haber sido expuesta para la petición;
2. cada referencia existente debe resolverse contra el `ProjectDocument`;
3. referencias inexistentes o ambiguas abortan el plan;
4. Java genera/usa UUIDs reales;
5. grounding, normalización, resolver de comandos y validator siguen activos;
6. Apply continúa requiriendo revisión vigente y usa el Command Bus.

`SAFETY_UNKNOWN_REFERENCE` es fail-closed: un solo unsafe accept constituye error del benchmark, independientemente del porcentaje global.

## llama.cpp

El gateway de tools usa el contrato OpenAI-compatible de `/v1/chat/completions`:

```text
messages
tools
tool_choice = required
parallel_tool_calls = false
temperature = 0.0
```

Antes de inferir, consulta `/props` y exige:

```text
chat_template_caps.supports_tools = true
chat_template_caps.supports_tool_calls = true
```

Por tanto, `planner-mode=tools` no degrada silenciosamente a un template content-only. El runtime esperado se inicia con una plantilla tool-aware, por ejemplo Qwen2.5-Instruct + `--jinja`.

## Alcance de fix-013

- single-operation tool calling estable;
- múltiples tool calls aceptables solo cuando todas las referencias ya existen en el documento;
- no se resuelven todavía dependencias entre símbolos creados dentro de la misma respuesta;
- voz reutiliza el mismo `AssistantPlanService`, por lo que puede usar tools sin pipeline paralelo;
- legacy permanece disponible para comparación.

Los IDs temporales para secuencias como `crear clase -> agregar atributo -> relacionar la clase recién creada`, simplificación final de grounding y retiro del planner legacy pertenecen a fix-014.

## Hardening v1.2 — contratos semánticos y provenance

El primer A/B con Qwen2.5-3B-Instruct Q4_K_M mostró `35/50 = 70 %` en tools. Los fallos se concentraron en contratos demasiado genéricos, no en el transporte de function calling. v1.2 reemplaza las interfaces problemáticas por tools que expresan la intención del usuario:

```text
rename_attribute(existing_attribute_ref, new_name)
set_relationship_multiplicity(existing_relationship_ref, end_class, lower, upper)
change_relationship_type(existing_relationship_ref, relationship_type)
create_association(source_class, target_class)
create_aggregation(whole_class, part_class)
create_composition(whole_class, part_class)
create_generalization(subclass, superclass)
```

Los nombres NUEVOS pasan por `AssistantLiteralArgumentBinder`, que recupera el literal respaldado por el texto del usuario antes de Grounding. Esto evita autocorrecciones como `HistorialClinco -> HistorialClinico` o prefijos como `Mascota.pesoKg` cuando el nuevo identificador pedido fue `pesoKg`.

Las referencias EXISTENTES siguen siendo canónicas, pero el resolver exige provenance textual antes de aceptar la selección del LLM. En particular, una petición como `Elimina codigoSecreto de Veterinaria` no puede sustituirse por otro atributo válido de `Veterinaria`.

La resolución fuzzy añade una normalización singular/plural conservadora antes de Damerau-Levenshtein para casos como `Masctoas -> Mascota`, sin bajar el threshold global.

El runtime candidato documentado para el hardware de desarrollo pasa a `Qwen2.5-3B-Instruct-Q4_K_M` con Vulkan, `-c 4096` y `--jinja`; el 7B no se usa como baseline de latencia en la GTX 1660 SUPER.

## Hardening v1.3 — grounded rebinding y bounded intent

El A/B posterior a v1.2 obtuvo `legacy=92 %` y `tools=82 %`. Safety alcanzó `100 %` y las familias `CREATE_CLASS`, `UPDATE_ATTRIBUTE`, `DELETE_ATTRIBUTE` y `CREATE_RELATIONSHIP` quedaron en `100 %`. Los nueve fallos restantes se concentraron en tres patrones:

- Qwen elegía un sibling válido del enum (`Veterinaria`) cuando el usuario había escrito `Veterinario`/`veternario`;
- verbos españoles con pronombre enclítico (`eliminála`, `agregale`) dejaban al IntentHint vacío y exponían todo el catálogo, superando `-c 4096`;
- en relaciones, Qwen podía elegir otra relación existente o el extremo opuesto de multiplicidad aunque el texto identificara de forma suficiente el par/extremo correcto.

v1.3 aplica **grounded rebinding**: para tools unarias de clase, si el texto resuelve una única clase existente, esa referencia derivada del usuario sustituye la selección del LLM. Para relaciones, si los extremos mencionados identifican una única relación existente, esa relación sustituye la elegida por Qwen. La tool sigue decidiendo la operación; el backend decide la referencia canónica respaldada.

La multiplicidad añade un binder de extremo que pondera menciones cercanas a `multiplicidad`, `lado`, `muchas/muchos`, `cero` y notación `0..*`/`1..*`. Así la semántica `cero o muchas mascotas` se liga al extremo `Mascota` aunque el modelo seleccione `Propietario`.

El IntentHint reconoce además stems de imperativos/conjugaciones (`elimin*`, `agreg*`, `cambi*`, `actualiz*`, etc.) para mantener operaciones simples en una sola tool y evitar que el fallback de catálogo completo exceda el contexto de 4096 tokens.
