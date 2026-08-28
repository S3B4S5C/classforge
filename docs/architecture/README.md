# Arquitectura de ClassForge

**Estado:** arquitectura implementada al cierre del Ciclo 1 de Elaboración.

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
| `api-validation-errors.md` | contrato de errores | implementado |
| `command-bus.md` | comandos y Undo/Redo | implementado |
| `authentication-and-ownership.md` | auth y aislamiento | implementado |
| `realtime-collaboration-protocol.md` | protocolo STOMP | implementado |
| `realtime-client-sync.md` | pending/resync/convergencia | implementado |
| `realtime-presence.md` | presencia efímera | implementado |
| `assistant-command-pipeline.md` | texto/voz/IA/BATCH | implementado |
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
12. UML -> relacional será determinista y no dependerá de IA.

## Arquitectura todavía no implementada

- adaptador imagen -> UML;
- XMI 2.1 / Enterprise Architect;
- `RelationalModel`;
- generador Spring Boot/JPA;
- OpenAPI/Postman generado;
- Domain Manifest de aplicación generada;
- frontend Angular generado;
- Capacitor/Android generado;
- asistente de la aplicación generada;
- membresías colaborativas;
- auditoría persistente.

## UML académico

Los diagramas para presentación se controlan desde `../uml/diagram-catalog.md`.
