# C2-cu08-fix-014 — Native tools cutover y limpieza legacy

## Objetivo
Convertir el camino native tool calling validado en fix-013 en arquitectura oficial del Assistant antes de CU-09.

## Evidencia de entrada
- benchmark A/B, 20 intentos por categoría;
- legacy 94 %;
- tools 100 %;
- safety 100 %;
- Qwen2.5-3B Q4_K_M con latencia compatible con UX local.

## Cambios
1. `AssistantPlanService` depende directamente de `AssistantNativeToolPlanner`.
2. Se eliminan planner/router/schema legacy y tests específicos.
3. Peticiones compuestas se resuelven por una ruta ordenada de steps; cada step usa preview efímero y la ruta determina el fin del plan.
4. Cada paso se proyecta sobre un `ProjectDocument` efímero; los UUIDs generados por preview actúan como identidades temporales y no se persisten.
5. Voz y texto comparten planner.
6. E2E pasa a tools-only y añade suites `regression` y `holdout`.
7. Se mantienen compiler/grounding/normalizer como barreras deterministas posteriores a tool resolution.

## Criterio de cierre
`clean build` verde, tests focalizados de wiring/multi-tool/texto-voz y ejecución manual recomendada de:

```powershell
pwsh -NoProfile -File .\scripts\assistant-tool-regression.ps1 -Attempts 20 -VerboseAttempts
pwsh -NoProfile -File .\scripts\assistant-tool-holdout.ps1 -Attempts 3 -VerboseAttempts
```

CU-08 no se reabre funcionalmente; este incremento cierra su hardening arquitectónico y habilita CU-09 para converger en las mismas tools/IR/Command Bus.


<!-- CU08-FIX-014-V1.3-HIERARCHICAL-ROUTING -->
## Correccion posterior al primer holdout

El primer holdout de 33 intentos descubrio una diferencia entre regresion y generalizacion: las frases conocidas seguian cerca de 95-100 %, pero redacciones nuevas sin `IntentHint` activaban el catalogo completo y superaban el contexto local (`~5179-5183 > 4096`). Tambien se observo que Qwen puede devolver 2-3 `tool_calls` aun con `parallel_tool_calls=false`.

La correccion v1.3 introduce un router native pequeno que devuelve `steps` semanticos ordenados y nunca contiene enums del `ProjectDocument`. Luego se ejecuta exactamente una tool UML pesada por step sobre un documento efimero actualizado. Las llamadas adicionales de una ronda se marcan `DEFERRED_REPLAN` en lugar de convertir una salida util del modelo en error. Se agrega binding textual de multiplicidad para que `0..*` o `ninguna/varias` no dependan del upper elegido por Qwen.

Criterio actualizado: no iniciar CU-09 hasta que `assistant-tool-regression` conserve el baseline y `assistant-tool-holdout` deje de presentar errores de contexto/safety y alcance el umbral acordado.


<!-- CU08-FIX-014-V1.4-ROUTE-ADJUDICATION -->
## Revision v1.4 — route adjudication y burst tolerance

La corrida holdout posterior a v1.3 alcanzo 57.6 % (19/33), sin overflows de contexto y con safety 100 %. El analisis mostro que varios fallos compartian una causa: una peticion simple podia recibir pasos extra (`create_class` antes de rename/add/relationship), contaminando el `ProjectDocument` efimero; ademas Qwen podia repetir 20-25 llamadas de la misma tool.

La revision v1.4:

1. introduce `AssistantToolRouteAdjudicator` para que las peticiones simples tengan una sola operacion compatible con evidencia del proyecto;
2. usa semantica explicita de atributo/relacion/multiplicidad para corregir rutas incompatibles sin conceder autoridad de escritura al router;
3. deriva una ruta dependency-ordered para compuestos detectados;
4. procesa solo la primera tool call compatible de cada step y guarda solo esa llamada en historial;
5. hace un unico retry si una respuesta obligatoria llega sin `tool_calls`;
6. conserva fail-closed para referencias desconocidas y safety como criterio absoluto.

La suite holdout debe repetirse antes de considerar el hardening final cerrado y antes de iniciar CU-09.

<!-- CU08-FIX-014-V1.5-STATELESS-COMPOUND -->
## Revisión v1.5 — estado compuesto sin historial de chat

La tercera corrida holdout alcanzó `84.8 % (28/33)` y mantuvo safety en `100 %`. La evidencia aisló tres defectos finales: falso compound para `Añade al modelo una nueva clase...`, endpoint de `ninguna o varias mascotas`, y errores LLM genéricos en los tres escenarios multi-tool.

La revisión v1.5:

1. evita clasificar el verbo que crea una `nueva clase` como un segundo `ADD_ATTRIBUTES`;
2. reconoce `ningun*/vari*` como cues de cardinalidad y favorece el extremo lingüísticamente cuantificado;
3. deja de reenviar historial native `assistant/tool` entre steps; el `ProjectDocument` efímero y su catálogo reconstruido constituyen todo el estado entre rondas;
4. conserva el filtrado de bursts y mejora el diagnóstico de cualquier excepción residual del gateway.

El holdout y la regression deben volver a ejecutarse antes de iniciar CU-09.

<!-- CU08-FIX-014-V1.6-LAZY-TOOL-PARSE -->
## Revision v1.6 — lazy parse de bursts truncados

La cuarta corrida holdout alcanzo `90.9 % (30/33)` con todas las categorias simples y safety en `100 %`. Solo `MULTI_TOOL_COMPOUND` fallo, y los tres casos reportaron `UnexpectedEndOfInputException` dentro del gateway.

La revision v1.6 no modifica routing, grounding ni resolucion UML. Corrige exclusivamente el protocolo del gateway: procesa la primera tool call compatible y deja de parsear llamadas repetidas posteriores; si la primera llamada o el envelope llegan truncados, hace un unico retry con 512 completion tokens y una instruccion anti-burst. Esto conserva el presupuesto normal de 256 tokens y el contexto `-c 4096`.

Criterio inmediato: repetir primero `assistant-tool-holdout`; si `MULTI_TOOL_COMPOUND` queda verde y safety permanece en 100 %, ejecutar `assistant-tool-regression -Attempts 20` como confirmacion final antes de habilitar CU-09.
