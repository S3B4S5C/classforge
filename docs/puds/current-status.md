# Estado actual PUDS — hasta CU-05

Fecha de corte: 28 de agosto de 2026.

## CU-01

Estado: **cerrado**.

Proyecto persistible, UUID, ownership y UI.

## CU-02

Estado: **cerrado**.

`ProjectDocument`, `UmlModel`, `DiagramLayout`, revision, guardado y conflicto 409.

## CU-03

Estado: **cerrado**.

Clases, atributos, JointJS, layout, relaciones, multiplicidades e inspector.

## CU-04

Estado: **cerrado**.

Validador unico, validacion explicita sin persistencia, severidades y diagnosticos navegables.

## CU-05

Estado: **cerrado**.

```text
UI
 ↓
UmlCommand
 ↓
UmlCommandBus
 ↓
UmlCommandExecutor
 ↓
ProjectDocument
```

Incluye:

- Undo;
- Redo;
- maximo inicial de 100 operaciones;
- dirty contra baseline persistido;
- Guardar conserva historial;
- reload reinicia historial;
- comando nuevo despues de Undo limpia Redo;
- Move Class produce una entrada al terminar el drag;
- shortcuts de teclado.

## Persistencia

- JPA/Hibernate;
- H2 archivo en desarrollo;
- H2 memoria en tests;
- PostgreSQL futuro.

`*.classforge` queda como formato portable futuro.

## Siguiente caso

**CU-06 — Colaboracion en tiempo real.**

Debe transportar operaciones/comandos y mantener al servidor como autoridad.

## Diagramas UML

Los diagramas se elaboraran separadamente en `docs/uml/`.