# Catálogo de diagramas UML para la documentación oficial

**Estado al cierre del Ciclo 1:** estructura preparada; diagramas pendientes de elaboración.

| ID | Diagrama | Objetivo | CU principales | Estado |
|---|---|---|---|---|
| UML-01 | Casos de uso de ClassForge | actores y frontera del sistema | CU01..31 | PENDIENTE |
| UML-02 | Clases del modelo canónico | `ProjectDocument`, UML y layout | CU02..04 | PENDIENTE |
| UML-03 | Componentes de ClassForge | frontend/backend/runtime/adaptadores | transversal | PENDIENTE |
| UML-04 | Secuencia abrir/guardar | revisión y persistencia | CU02 | PENDIENTE |
| UML-05 | Secuencia operación colaborativa | STOMP, revisión, broadcast | CU06 | PENDIENTE |
| UML-06 | Secuencia presencia | eventos efímeros | CU07 | PENDIENTE |
| UML-07 | Secuencia voz a UML | Whisper, Qwen native tools, plan, BATCH, apply | CU08 | PENDIENTE |
| UML-08 | Despliegue offline/LAN | navegador, Spring, llama, whisper | CU06, CU08, CU24, CU25 | PENDIENTE |
| UML-09 | Componentes de generación | pipeline de generadores | CU12..18 | FUTURO |
| UML-10 | Secuencia voz app generada | Whisper/Qwen native tools, DomainManifest, preview/apply | CU19 | PENDIENTE |

## Prioridad

Antes de cerrar un futuro Ciclo 2 conviene elaborar primero UML-01, UML-02, UML-03, UML-05, UML-07 y UML-08 porque ya pueden contrastarse contra código existente.

Los diagramas UML-09 y UML-10 deben esperar a que los correspondientes componentes estén implementados.

## Fuentes técnicas

- `../architecture/project-document.md`;
- `../architecture/uml-domain-model.md`;
- `../architecture/realtime-collaboration-protocol.md`;
- `../architecture/realtime-presence.md`;
- `../architecture/assistant-command-pipeline.md`;
- `../puds/use-cases.md`;
- `../puds/cycles/cycle-01-elaboration.md`.

## Destino en Word

Los diagramas aprobados se insertarán en los capítulos de casos de uso, arquitectura, diseño y despliegue. Este catálogo funcionará como control para evitar que falte un diagrama o que el Word use una versión obsoleta.

## Nota C2-cu31-001

El actor Colaborador deja de ser solamente conceptual: `ProjectMembership` EDITOR ya tiene representación persistente. UML-01 continúa PENDIENTE hasta dibujar y verificar el diagrama académico.

## Nota C2-cu31-002

El actor Colaborador ya dispone de flujo real de invitación por correo, aceptación y `ProjectMembership` EDITOR. El catálogo académico sigue sin marcar UML-01 como terminado hasta que el diagrama se dibuje y verifique contra esta implementación.



## Nota C2-cu31-003

CU-31 queda cerrado en código y pruebas: el actor **Colaborador** corresponde a una cuenta con `ProjectMembership` EDITOR y participa en REST, operaciones STOMP, presencia y Assistant bajo la misma política de acceso. El actor sin membership queda fuera. Esta evidencia no marca automáticamente los diagramas académicos como dibujados; el catálogo mantiene esa distinción.
