# Matriz final de trazabilidad académica

**Corte:** 14 de septiembre de 2026. La autoridad de estado es `../puds/current-status.md`.

| CU | Ciclo/corte | Arquitectura principal | Código principal | Prueba/evidencia principal |
|---|---|---|---|---|
| CU-01 | C1 | project lifecycle | `project/` | tests proyecto |
| CU-02 | C1 | project-document | `project/`, frontend workspace | save/revision tests |
| CU-03 | C1 | uml-domain-model | `projects/commands`, `project/` | command tests |
| CU-04 | C1 | UML validation | `project/validation` | validator tests |
| CU-05 | C1 | Command Bus | frontend `commands/` | characterization command tests |
| CU-06 | C1 | realtime-collaboration-protocol | `collaboration/` | STOMP/E2E |
| CU-07 | C1 | realtime-presence | collaboration presence | presence tests |
| CU-08 | C2 | assistant-command-pipeline | `assistant/` | assistant regression/E2E |
| CU-09 | C2 | vision-input-pipeline | `assistant/vision/` | benchmark + vision E2E |
| CU-10 | C9 | xmi-interchange | `integration/xmi/` | XMI acceptance |
| CU-11 | C9 | xmi-interchange | `integration/xmi/` | round-trip acceptance |
| CU-12 | C3 | relational-generation | `generation/relational/` | mapping tests |
| CU-13 | C3 | spring-boot-generation | `generation/spring/` | Spring generation acceptance |
| CU-14 | C3 | spring-boot-generation | Spring API/Auth generation | CRUD/Auth acceptance |
| CU-15 | C4 | api-artifact-generation | OpenAPI/Postman renderers | contract acceptance |
| CU-16 | C5 | domain-manifest-generation | manifest planner/renderer | manifest acceptance |
| CU-17 | C6 | angular-frontend-generation | Angular renderer | generated Angular build |
| CU-18 | C7 | flutter-mobile-generation | Flutter renderer | generated Flutter build |
| CU-19 | C8 | generated-assistant-generation | generated Assistant renderer | generated Assistant acceptance |
| CU-20..23 | C8 | generated-assistant-generation | absorbidos por CU-19 | CU-19 acceptance |
| CU-24 | infra | assistant runtime | Whisper gateway/runtime | runtime smoke |
| CU-25 | infra | assistant runtime | llama.cpp gateways | runtime smoke |
| CU-26 | fuera de alcance | — | — | decisión de producto |
| CU-27 | C10 | runtime/demo | `scripts/demo-*`, fixture veterinaria | demo acceptance |
| CU-28 | C1/C2 | auth | `auth/` | auth tests |
| CU-29 | C1/C2 | auth | `auth/` | login/JWT tests |
| CU-30 | C1/C2 | ownership | `project/` | access tests |
| CU-31 | C2 | membership/realtime | collaboration + membership | invitation/access/E2E |

## Uso para la defensa

Para cada CU, explicar en este orden: **actor/objetivo -> documento normativo -> componente -> clase/fachada -> prueba que congela comportamiento**. Los diagramas formales definidos en `../uml/diagram-catalog.md` deben derivarse de esta matriz y del mapa de código.
