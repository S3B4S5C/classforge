# Arquitectura

## Implementado hasta CU-05

- `uml-domain-model.md`
- `uml-relationships.md`
- `jointjs-canvas.md`
- `api-validation-errors.md`
- `uml-validation.md`
- `command-bus.md`

## Flujo vigente

```text
UI / Canvas
    ↓
UmlCommand
    ↓
UmlCommandBus
    ↓
UmlCommandExecutor
    ↓
ProjectDocument
    ↓
JointJS
```

## Principios

1. `ProjectDocument` es la raiz documental.
2. `UmlModel` es la fuente de verdad semantica.
3. `DiagramLayout` esta separado.
4. JointJS no es dominio.
5. UUID es identidad.
6. Command Bus gobierna mutaciones manuales.
7. Backend valida y persiste como autoridad final.
8. CU-06 debe transportar operaciones, no JSON de JointJS.

## Pendiente

- WebSocket/STOMP;
- presencia;
- XMI;
- modelo relacional;
- generadores;
- IA/STT.

Los diagramas UML academicos se mantienen en `../uml/`.