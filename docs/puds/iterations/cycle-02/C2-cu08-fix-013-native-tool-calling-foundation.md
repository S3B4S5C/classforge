# C2-cu08-fix-013 — Native tool calling foundation

**Fase PUDS:** Elaboración  
**Ciclo:** 2 — Colaboración real y entrada visual  
**CU funcional:** CU-08 permanece CERRADO  
**Tipo:** hardening arquitectónico previo a CU-09  
**Estado al aplicar el parche:** EXPERIMENTO A/B

## Motivación

El benchmark E2E del planner JSON legacy alcanzó 86 % en una muestra de 50 intentos. Los fallos restantes mostraron que un modelo local puede comprender el texto y, aun así, seleccionar una familia de `AssistantPlanAction` incorrecta o rellenar campos del DTO equivocado.

Fix-013 no oculta esos errores con más postprocesado. Introduce un contrato alternativo: function calling nativo con herramientas UML pequeñas y tipadas.

## Implementación

```text
AssistantPlanningRouter
  legacy -> LlamaLanguageModelGateway
  tools  -> AssistantNativeToolPlanner

AssistantNativeToolPlanner
  -> DynamicUmlToolCatalog
  -> LlamaNativeToolCallingGateway
  -> UmlToolCallResolver
  -> AssistantSemanticPlan interno
```

El catálogo se deriva del `ProjectDocument` de la revisión que se está previsualizando.

## Invariantes

- nombres nuevos no se autocorrigen por pertenecer a una operación CREATE/rename target;
- elementos existentes deben converger a UUID real;
- tools ocultas por un `IntentHint` de alta confianza no pueden ser invocadas;
- atributo/relación inexistente aborta antes de preview;
- el LLM nunca modifica `ProjectDocument`;
- preview/Apply/Command Bus continúan iguales;
- legacy sigue siendo el default durante este incremento.

## Benchmark A/B

`scripts/assistant-tool-ab.ps1` ejecuta el mismo E2E dos veces:

1. `planner-mode=legacy`;
2. `planner-mode=tools`.

Cada ejecución usa el mismo fixture conceptual, las mismas categorías y los mismos criterios. Se generan reportes JSON y una tabla `LEGACY / TOOLS / DELTA`.

Safety cambia de política: cualquier `UNSAFE_ACCEPT` en `SAFETY_UNKNOWN_REFERENCE` marca la categoría como `ERROR`.

## Criterio para fix-014

No se retira legacy en este parche. El cutover requiere evidencia con al menos 20 intentos por categoría y como objetivo:

```text
overall tools >= 95 %
ninguna categoría normal < 80 %
SAFETY_UNKNOWN_REFERENCE = 100 %
0 referencias inexistentes aceptadas
0 tool calls estructuralmente inválidas
```

CU-09 continúa siendo el siguiente caso funcional del Ciclo 2; fix-013/fix-014 son hardening de la infraestructura que CU-09 reutilizará.

## v1.2 — semantic tool contract hardening

El primer A/B de v1.1 obtuvo `legacy=88 %` y `tools=70 %` (5 intentos por categoría). Los 15 fallos quedaron explicados por contratos de tool o grounding:

- autocorrección de nombres nuevos;
- `nombre` interpretado como atributo durante rename de clase;
- `update_attribute` con todos los cambios opcionales;
- dirección ambigua de generalización;
- multiplicidad expresada como `source/target` en lugar del extremo nombrado;
- `lower=-1` permitido por schema;
- plural+transposición `Masctoas`;
- `Elimina codigoSecreto de Veterinaria` clasificado incorrectamente como delete de clase.

v1.2 mantiene `legacy` como default y endurece solo el camino `tools`. El benchmark JSON ahora conserva `failureReasons` y ejemplos completos de fallo, y `assistant-tool-ab.ps1` los imprime automáticamente.

El criterio de cutover de fix-014 no cambia: v1.2 debe volver a medirse primero con 5 intentos y, si alcanza el objetivo, con al menos 20 intentos por categoría.

## v1.3 — grounded reference rebinding

El A/B de v1.2 mejoró tools de `70 %` a `82 %` y llevó `SAFETY_UNKNOWN_REFERENCE` a `100 %`. Los nueve fallos restantes se clasificaron como selección de sibling válido, overflow de contexto al caer al catálogo completo y selección incorrecta de relación/extremo.

v1.3 mantiene legacy como default y añade:

- rebinding determinista de referencias unarias de clase desde las menciones resueltas del usuario;
- rebinding de relación cuando el par de extremos identifica una única relación existente;
- binding del extremo de multiplicidad a partir de cues locales del texto;
- reconocimiento de verbos con sufijos/pronombres (`eliminála`, `agregale`) para no exponer todo el catálogo en operaciones simples;
- regresiones deterministas para los fallos observados de rename, delete/add context, multiplicidad y delete relationship.

El cutover de fix-014 continúa bloqueado hasta repetir A/B y luego confirmar el resultado con al menos 20 intentos por categoría.
