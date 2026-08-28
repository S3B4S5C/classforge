# ClassForge — Sincronización cliente STOMP

## Estado

CU06-002 conecta Angular con el servidor autoritativo implementado en CU06-001.

## Dependencia

Frontend:

```text
@stomp/stompjs 7.3.0
```

No se utiliza SockJS.

## Conexión

El navegador abre:

```text
/ws
```

usando el mismo host desde el que se cargó Angular.

En desarrollo `proxy.conf.json` reenvía `/ws` a Spring Boot `localhost:8082` con soporte WebSocket.

El JWT se envía en el frame STOMP CONNECT.

## Destinos

```text
SEND
/app/projects/{projectId}/operations

SUBSCRIBE
/topic/projects/{projectId}/operations

SUBSCRIBE privado
/user/queue/projects/{projectId}/operations
```

## Estado confirmado vs draft

El cliente mantiene conceptualmente dos estados:

```text
confirmedDocument
        |
        | servidor aceptó
        v
ProjectDocument autoritativo conocido

commandBus.document()
        |
        | puede incluir comandos optimistas
        v
draft visible
```

`confirmedRevision` sólo avanza con `OPERATION_APPLIED`.

## Operación local optimista

```text
UmlCommand
   ↓
Command Bus local
   ↓
canvas cambia inmediatamente
   ↓
ProjectOperation
   ↓
STOMP SEND
   ↓
pendingOperations
```

La revisión base de una nueva operación local es:

```text
confirmedRevision + cantidad de operaciones pendientes
```

Esto permite varios comandos consecutivos del mismo cliente sin esperar cada ACK.

Si una operación remota se intercala, el servidor puede rechazar una operación posterior por revisión obsoleta. El cliente no intenta merge automático: hace resync.

## ACK propio

El servidor difunde también al emisor.

Cuando `operationId` pertenece a `pendingOperations`:

1. se aplica el comando al `confirmedDocument`;
2. no se vuelve a aplicar al draft optimista;
3. se elimina de pending;
4. se actualiza la revisión confirmada;
5. cuando pending llega a cero se marca el documento como persistido.

Esto evita aplicar dos veces el mismo comando.

## Operación remota

Si no existen operaciones locales pendientes:

1. validar continuidad de revisión;
2. aplicar command al estado confirmado;
3. reemplazar el editor por el estado confirmado;
4. limpiar historial local Undo/Redo;
5. JointJS se reconstruye desde `ProjectDocument`.

La limpieza del historial es deliberadamente conservadora hasta CU06-003.

## Remote + pending local

CU06-002 no implementa rebase automático.

Si llega una operación remota mientras existen comandos optimistas pendientes:

```text
remote + pending
      ↓
resync REST
```

Las operaciones ya enviadas al servidor pueden aparecer luego mediante broadcast o rechazo.

## Gap de revisiones

Condición normal:

```text
incomingRevision == confirmedRevision + 1
```

Duplicado/tardío:

```text
incomingRevision <= confirmedRevision
→ ignorar
```

Gap:

```text
incomingRevision > confirmedRevision + 1
→ resync
```

## Resync

1. mantener la suscripción STOMP;
2. `GET /api/projects/{id}`;
3. instalar `ProjectDocument` y revisión autoritativos;
4. aplicar broadcasts recibidos durante el GET que todavía sean posteriores;
5. volver a `Sincronizado`.

Esto cierra la carrera entre REST y WebSocket.

## Desconexión

### Sin operaciones pending

El editor sigue disponible localmente.

Los nuevos comandos quedan `dirty` y se guardan mediante REST.

### Con operaciones pending

El cliente no sabe cuáles alcanzaron a persistirse.

Se marca `mustResyncOnReconnect`.

Al reconectar se recupera primero el estado del servidor.

### Cambios locales offline

Si existen cambios locales no persistidos cuando STOMP vuelve:

- no se reactiva la edición colaborativa;
- se muestra `Cambios locales`;
- el usuario debe guardar por REST;
- después del guardado se vuelve a conectar.

No existe merge silencioso.

## Save

Mientras colaboración está activa:

```text
Guardar = deshabilitado
```

porque cada operación aceptada ya se persiste.

Cuando STOMP no está disponible, CU-02 continúa funcionando como fallback:

```text
command local
→ dirty
→ Guardar REST
```

## Undo/Redo

CU06-002 no envía snapshots históricos por WebSocket.

Mientras realtime está activo, Undo/Redo se deshabilita.

CU06-003 implementará comandos inversos y `RESTORE_CLASS`.

## Estados UX

- Conectando...
- Sincronizado
- Sincronizando...
- Sin conexión
- Resincronizando...
- Cambios locales
- Conflicto
- Error de conexión

Material Symbols se sirve localmente.

<!-- CU06-003-INVERSE-COMMANDS-V1 -->
## CU06-003 — Undo/Redo sobre STOMP

Undo y Redo ya no están deshabilitados mientras el proyecto está sincronizado.

Se habilitan solamente cuando:

- no hay operaciones pending;
- el estado no está conectando;
- no hay resync en curso;
- no existe conflicto pendiente.

Undo genera el comando inverso y lo aplica optimistamente antes de enviarlo por STOMP.

Redo vuelve a emitir el comando forward con un `commandId` nuevo.

Un rechazo o desconexión durante Undo/Redo sigue la misma política que cualquier otra operación pending: resync autoritativo y limpieza del historial si ya no puede demostrarse continuidad.

Las operaciones remotas continúan invalidando el historial local.
