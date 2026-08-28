# ClassForge — Protocolo de colaboración en tiempo real

## Estado

CU06-001 implementa la infraestructura autoritativa del servidor.

El frontend automático se integra en CU06-002.

## Transporte

```text
WebSocket endpoint: /ws

SEND
/app/projects/{projectId}/operations

SUBSCRIBE aceptadas
/topic/projects/{projectId}/operations

SUBSCRIBE rechazos privados
/user/queue/projects/{projectId}/operations
```

Se utiliza STOMP sobre WebSocket nativo.

No se utiliza SockJS en esta iteración.

## Autenticación

El handshake HTTP `/ws` se permite sin sesión porque los navegadores no pueden añadir libremente el Bearer token al handshake WebSocket.

La autenticación real ocurre en el frame STOMP `CONNECT`:

```text
Authorization: Bearer <jwt>
```

`StompJwtAuthenticationInterceptor` verifica el mismo JWT utilizado por REST y crea `CollaborationPrincipal`.

No se utiliza JWT en query string.

## Autorización

`ProjectStompAuthorizationInterceptor` valida `SEND` y `SUBSCRIBE`.

En CU06-001 el acceso permitido es el owner del proyecto.

Más adelante `ProjectAccessService` podrá ampliarse con `ProjectMembership` sin cambiar el protocolo.

Reglas adicionales:

- un cliente no puede hacer `SEND` directamente a `/topic`, `/queue` o `/user`;
- un cliente no puede suscribirse a `/app`;
- un usuario autenticado tampoco puede operar sobre un proyecto ajeno.

## ProjectOperation

```json
{
  "operationId": "uuid",
  "projectId": "uuid",
  "clientId": "uuid",
  "baseRevision": 41,
  "command": {
    "commandId": "uuid",
    "issuedAt": "2026-08-28T06:10:00Z",
    "type": "CREATE_CLASS",
    "umlClass": {},
    "layout": {}
  }
}
```

`operationId` identifica el envío de red.

`commandId` identifica el comando UML.

`clientId` identifica la instancia/pestaña cliente y no sustituye la identidad autenticada.

La identidad del usuario siempre proviene del JWT validado por Spring.

## Comandos admitidos

- CREATE_CLASS
- RENAME_CLASS
- DELETE_CLASS
- ADD_ATTRIBUTE
- UPDATE_ATTRIBUTE
- DELETE_ATTRIBUTE
- CREATE_RELATIONSHIP
- UPDATE_RELATIONSHIP
- DELETE_RELATIONSHIP
- MOVE_CLASS

El contrato refleja CU-05.

## Flujo autoritativo

```text
ProjectOperation
      ↓
JWT / ownership
      ↓
lock pesimista del proyecto
      ↓
baseRevision == currentRevision ?
      ↓
ProjectCommandExecutor
      ↓
ProjectDocumentValidator
      ↓
persistir JPA
      ↓
revision + 1
      ↓
OPERATION_APPLIED broadcast
```

Cada operación aceptada se persiste inmediatamente.

No existe un segundo modelo autoritativo solamente en memoria.

## Aceptación

```json
{
  "type": "OPERATION_APPLIED",
  "operationId": "uuid",
  "projectId": "uuid",
  "clientId": "uuid",
  "revision": 42,
  "command": {},
  "actor": {
    "id": "uuid",
    "displayName": "Usuario"
  },
  "appliedAt": "..."
}
```

Se difunde a todos los suscriptores del proyecto, incluido el emisor.

CU06-002 utilizará `operationId` para evitar aplicar dos veces una operación optimista propia.

## Rechazo

```json
{
  "type": "OPERATION_REJECTED",
  "operationId": "uuid",
  "projectId": "uuid",
  "clientId": "uuid",
  "code": "REVISION_CONFLICT",
  "message": "...",
  "currentRevision": 42,
  "rejectedAt": "..."
}
```

Los rechazos se envían al destino privado del usuario.

## Concurrencia

No se implementan OT, CRDT, Yjs, Redis ni Kafka.

Se utiliza revisión optimista sobre un lock de persistencia:

```text
A base=41 → acepta → revision=42
B base=41 → rechaza → currentRevision=42
```

CU06-002 realizará resync HTTP después de un rechazo o de detectar un gap de revisiones.

## Persistencia

La operación aceptada incrementa la misma revisión que CU-02.

REST y WebSocket convergen sobre el mismo `ProjectDocument` persistido.

## LAN / offline

La arquitectura no depende de Internet.

El endpoint puede operar entre dispositivos conectados a la misma LAN/hotspot cuando el backend Spring es accesible.

## Orígenes

CU06-001 permite cualquier Origin en `/ws` para facilitar desarrollo y LAN.

Esto no implica acceso anónimo: el frame STOMP CONNECT requiere JWT y cada proyecto exige autorización.

Antes de un despliegue público se deberá parametrizar/restringir `allowedOriginPatterns`.

<!-- RESTORE-CLASS-PROTOCOL-V1 -->
## RESTORE_CLASS

CU06-003 amplía el protocolo con `RESTORE_CLASS`.

Payload conceptual:

```json
{
  "type": "RESTORE_CLASS",
  "umlClass": {},
  "layout": {},
  "relationships": []
}
```

Su propósito es compensar `DELETE_CLASS` sin transmitir el documento entero.

Spring procesa `RESTORE_CLASS` bajo el mismo lock, revisión y `ProjectDocumentValidator` que el resto de comandos.

La restauración es una nueva revisión normal y se difunde a todos los clientes.
