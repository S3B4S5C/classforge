# Iteración E3 — CU06-003 Undo/Redo colaborativo

## Objetivo

Cerrar CU-06 haciendo compatible el historial de CU-05 con el modelo autoritativo STOMP.

## Problema resuelto

Restaurar snapshots locales directamente sería inseguro:

```text
A crea Animal
B crea Cita
A restaura snapshot viejo
```

podría eliminar accidentalmente trabajo de B.

CU06-003 reemplaza esa estrategia durante colaboración por comandos compensatorios.

## Implementado

- `inverseCommand` por entrada de historial;
- remint de `commandId` al ejecutar Undo/Redo;
- Undo colaborativo;
- Redo colaborativo;
- `RESTORE_CLASS`;
- restauración atómica de clase/layout/relaciones;
- misma revisión autoritativa del servidor;
- misma persistencia y broadcast;
- bloqueo de historial mientras hay pending/resync/conflicto;
- invalidación del historial ante operación remota;
- tests backend de restauración y revisiones.

## Inversos

```text
CREATE_CLASS        → DELETE_CLASS
DELETE_CLASS        → RESTORE_CLASS
RENAME_CLASS        → RENAME_CLASS anterior
ADD_ATTRIBUTE       → DELETE_ATTRIBUTE
DELETE_ATTRIBUTE    → ADD_ATTRIBUTE anterior
UPDATE_ATTRIBUTE    → UPDATE_ATTRIBUTE anterior
CREATE_RELATIONSHIP → DELETE_RELATIONSHIP
DELETE_RELATIONSHIP → CREATE_RELATIONSHIP anterior
UPDATE_RELATIONSHIP → UPDATE_RELATIONSHIP anterior
MOVE_CLASS          → MOVE_CLASS al layout anterior
```

## Flujo Undo

```text
history
 ↓
inverse command
 ↓
optimistic draft
 ↓
STOMP
 ↓
Spring authority
 ↓
revision + 1
 ↓
broadcast
```

## Flujo Redo

El `forwardCommand` se vuelve a emitir con metadata de comando nueva.

## Definition of Done de CU-06

- dos clientes sincronizan cambios sin refresh;
- servidor es autoritativo;
- operaciones, no documentos completos, cruzan STOMP;
- revisión obsoleta se rechaza;
- gap provoca resync;
- fallback offline conserva Guardar REST;
- Undo/Redo colaborativo usa comandos;
- borrar/restaurar clase conserva relaciones y layout;
- operación remota invalida historial local inseguro;
- backend y frontend compilan.

## Resultado

**CU-06 — Colaboración en tiempo real: CERRADO.**

CU-07 podrá añadir presencia efímera sin modificar este protocolo de dominio.