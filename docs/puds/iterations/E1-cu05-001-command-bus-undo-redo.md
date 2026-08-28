# Iteración E1 — CU05-001 Command Bus + Undo/Redo

## Objetivo

Implementar CU-05 y resolver la deuda de Command Bus identificada al auditar CU-03/CU-04.

## Incluido

- `UmlCommand`;
- `UmlCommandExecutor`;
- `UmlCommandBus`;
- historial before/after;
- migración del workspace store;
- Undo/Redo;
- baseline persistido para dirty;
- shortcuts;
- máximo inicial de 100 operaciones;
- invalidación de resultados CU-04 al modificar el draft.

## Definition of Done

- clases, atributos y relaciones usan comandos;
- Move Class usa un comando al terminar drag;
- borrar una clase es una sola operación deshacible;
- comando nuevo tras Undo limpia Redo;
- Guardar conserva historial;
- reload crea historial nuevo;
- backend sigue validando al persistir;
- backend tests y frontend build verdes.

## Fuera de alcance

- historial persistente;
- colaboración;
- OT;
- CRDT;
- branching history.