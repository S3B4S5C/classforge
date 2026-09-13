# Arquitectura de ClassForge

**Estado:** arquitectura ejecutable en fase de Construcción; Ciclos 1–6 cerrados; CU-17 cerrado.

## Fuente de verdad

```text
ProjectDocument
├── UmlModel
└── DiagramLayout
```

`UmlModel` contiene semántica. `DiagramLayout` contiene presentación. JointJS es una proyección.

## Ruta de mutación

```text
UI manual
colaboración
Assistant
    |
    v
UmlCommand / BATCH
    |
    v
UmlCommandBus
    |
    v
UmlCommandExecutor
    |
    v
ProjectDocument
```

## Documentos vigentes

| Documento | Tema | Estado |
|---|---|---|
| `project-document.md` | raíz documental/versionado | implementado |
| `uml-domain-model.md` | dominio UML tipado | implementado |
| `uml-relationships.md` | relaciones y dirección | implementado |
| `jointjs-canvas.md` | proyección visual | implementado |
| `uml-validation.md` | reglas UML | implementado |
| `relational-model.md` | IR relacional interna | CU-12 cerrado |
| `spring-boot-generation.md` | generación/export Spring Boot/JPA + API CRUD Simple/Auth | CU-13 y CU-14 cerrados |
| `openapi-postman-generation.md` | contrato HTTP + OpenAPI/Postman deterministas | CU-15 implementado y aceptado |
| `domain-manifest-generation.md` | contrato semántico `domain-manifest.json` | CU-16 implementado y aceptado |
| `angular-frontend-generation.md` | frontend Angular específico por entidad + dashboard/Auth/theme | CU-17 implementado y aceptado |
| `api-validation-errors.md` | contrato de errores | implementado |
| `command-bus.md` | comandos y Undo/Redo | implementado |
| `authentication-and-ownership.md` | auth y aislamiento | implementado |
| `project-membership-and-invitations.md` | membership + invitaciones internas | CU-31 implementado y endurecido en C2-cu31-003 |
| `realtime-collaboration-protocol.md` | protocolo STOMP | implementado |
| `realtime-client-sync.md` | pending/resync/convergencia | implementado |
| `realtime-presence.md` | presencia efímera | implementado |
| `assistant-command-pipeline.md` | texto/voz/IA/BATCH | implementado |
| `vision-input-pipeline.md` | imagen -> VLM/OpenCV/Java -> proposal -> BATCH/Apply | CU-09 cerrado; arquitectura vigente |
| `ui-button-system.md` | convenciones de botones | vigente |
| `ui-icons.md` | Material Symbols local | vigente |

## Principios

1. `ProjectDocument` es la raíz documental.
2. `UmlModel` es la fuente de verdad semántica.
3. Layout no forma parte de la semántica UML.
4. JointJS no es dominio.
5. UUID es identidad estable.
6. Command Bus es la ruta común de mutación.
7. Backend es autoridad final de revisión, validación y persistencia.
8. Colaboración transporta operaciones.
9. Presencia es efímera.
10. IA genera propuestas estructuradas, no mutaciones directas.
11. LLM no genera UUID.
12. UML -> relacional es determinista, interno y no depende de IA.
13. Spring Boot/JPA se genera desde `RelationalModel` mediante una IR de generación, proyecto virtual validado y ZIP determinista; la exportación es read-only.
14. OpenAPI y Postman son proyecciones deterministas del mismo `SpringApiContract`; no se derivan por introspección runtime.
15. CU-16 genera `domain-manifest.json` como proyección semántica de los planes canónicos, preservando UUIDs y operationIds sin parsear artefactos generados.
16. CU-17 genera componentes Angular específicos desde Domain Manifest/contrato API; el theme primario es una opción de exportación, no estado UML.

## Arquitectura todavía no implementada


- XMI 2.1 / Enterprise Architect;
- Capacitor/Android generado;
- asistente de la aplicación generada;
- auditoría persistente.

## UML académico

Los diagramas para presentación se controlan desde `../uml/diagram-catalog.md`.

<!-- CU08-FIX-013-NATIVE-TOOLS -->
- `assistant-native-tool-calling.md`: catálogo dinámico de tools UML, function calling nativo, resolución canónica/fail-closed y estrategia de migración legacy -> tools.
