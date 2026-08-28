# Iteración E3 — CU06-001 Backend STOMP autoritativo

## Objetivo

Resolver primero el servidor de colaboración antes de conectar Angular.

## Implementado

- `/ws`;
- STOMP;
- broker simple Spring;
- JWT en CONNECT;
- autorización SEND/SUBSCRIBE;
- canales por proyecto;
- `ProjectOperation`;
- Java `ProjectCommandExecutor`;
- revisión autoritativa;
- lock pesimista;
- validación final de `ProjectDocument`;
- persistencia inmediata;
- broadcast `OPERATION_APPLIED`;
- rechazo privado `OPERATION_REJECTED`;
- prueba real con dos clientes STOMP.

## Escenario probado

```text
Cliente A revision 0
Cliente B revision 0

A SEND CREATE_CLASS Animal
→ servidor acepta
→ revision 1
→ A recibe OPERATION_APPLIED
→ B recibe OPERATION_APPLIED

B SEND CREATE_CLASS Cita baseRevision 0
→ servidor detecta stale
→ B recibe REVISION_CONFLICT
→ revision permanece 1
```

## Seguridad

- handshake HTTP abierto exclusivamente para permitir alcanzar STOMP;
- CONNECT exige Bearer JWT;
- identidad se deriva del token;
- owner se verifica en mensaje y nuevamente en la transacción;
- SEND directo a broker se bloquea.

## Definition of Done CU06-001

- servidor compila;
- prueba de dos clientes converge;
- stale writer se rechaza;
- operación inválida no persiste;
- proyecto ajeno no se modifica;
- una operación aceptada incrementa exactamente una revisión;
- frontend existente sigue compilando.

## Pendiente CU06-002

- `@stomp/stompjs`;
- conexión automática Angular;
- optimistic local command;
- pending operation correlation;
- remote command application;
- revisión cliente;
- gap detection;
- resync;
- UX de estados de conexión.

## Pendiente CU06-003

- Undo/Redo colaborativo mediante comandos inversos;
- RESTORE_CLASS;
- reconnect/conflict hardening.