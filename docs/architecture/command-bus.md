# ClassForge — Command Bus y Undo/Redo

## Objetivo

CU-05 formaliza la capa de comandos prevista desde CU-03 y la utiliza como base de Undo/Redo.

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
 ↓
JointJS
```

## Comandos iniciales

- `CREATE_CLASS`
- `RENAME_CLASS`
- `DELETE_CLASS`
- `ADD_ATTRIBUTE`
- `UPDATE_ATTRIBUTE`
- `DELETE_ATTRIBUTE`
- `CREATE_RELATIONSHIP`
- `UPDATE_RELATIONSHIP`
- `DELETE_RELATIONSHIP`
- `MOVE_CLASS`

Guardar, validar, seleccionar, zoom y pan no forman parte del historial UML.

## Historial

Cada operación aplicada conserva:

```text
UmlHistoryEntry
├── command
├── before
└── after
```

Los snapshots son internos a Undo/Redo. El contrato reutilizable sigue siendo `UmlCommand`.

El historial local tiene un máximo inicial de 100 operaciones.

## Dirty

El bus mantiene como baseline el último documento persistido.

Por ello:

```text
editar
guardar
undo
```

vuelve a dejar cambios sin guardar.

Guardar no elimina historial. Cargar nuevamente el proyecto sí inicia un historial nuevo.

## Shortcuts

- Ctrl/Cmd + Z: Undo.
- Ctrl/Cmd + Shift + Z: Redo.
- Ctrl + Y: Redo.

No se interceptan dentro de formularios o dialogs.

## Preparación CU-06

CU-06 podrá transportar comandos/operaciones en vez de documentos JointJS completos.

El backend seguirá siendo autoritativo.