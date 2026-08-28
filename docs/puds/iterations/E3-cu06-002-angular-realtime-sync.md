# Iteración E3 — CU06-002 Angular realtime sync

## Objetivo

Conectar el editor Angular al servidor STOMP de CU06-001 sin cambiar la fuente de verdad.

## Implementado

- `@stomp/stompjs`;
- `/ws` mediante proxy en desarrollo;
- JWT en CONNECT;
- suscripción pública y privada;
- conexión automática al abrir proyecto;
- command local optimista;
- `ProjectOperation`;
- pending operation correlation;
- ACK propio sin doble aplicación;
- remote command application;
- revisión cliente confirmada;
- gap detection;
- resync por REST;
- buffering de broadcasts durante resync;
- fallback offline a CU-02 Save;
- UX de estados de conexión.

## Flujo local

```text
Dialog/Canvas
    ↓
UmlCommandBus
    ↓
draft optimista
    ↓
STOMP
    ↓
Spring
    ↓
OPERATION_APPLIED
    ↓
confirmedDocument/revision
```

## Flujo remoto

```text
OPERATION_APPLIED
    ↓
revision continua?
    ↓
confirmedDocument
    ↓
CommandBus.load()
    ↓
JointJS
```

## Reglas de seguridad

El cliente nunca envía userId como autoridad.

La identidad continúa viniendo del JWT validado por Spring.

## Concurrencia

CU06-002 no implementa OT/CRDT ni merge automático.

Conflicto, gap o intercalación remota con pending producen resync.

## Undo/Redo

Deshabilitado mientras realtime está activo.

Se completa colaborativamente en CU06-003.

## Definition of Done

- dos pestañas del mismo owner pueden ver cambios sin refresh;
- emisor no aplica dos veces su propio command;
- operación remota modifica el canvas;
- revision visible avanza;
- Save queda deshabilitado mientras realtime está activo;
- desconexión permite edición local;
- cambios offline requieren Guardar antes de volver a colaborar;
- gap/rechazo ejecuta resync;
- backend tests continúan verdes;
- frontend compila.