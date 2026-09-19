# ClassForge — Presencia colaborativa efímera

## Estado

CU07-001 implementa y cierra CU-07.

## Separación arquitectónica

```text
OPERACIONES DE DOMINIO
UmlCommand
→ ProjectDocument
→ persistencia
→ revision + 1

PRESENCIA
usuario/cursor/seleccion
→ memoria
→ broadcast
→ revision + 0
```

Los eventos de presencia nunca son fuente de verdad UML.

## Transporte

Se reutiliza la conexión STOMP `/ws` de CU-06.

```text
SEND
/app/projects/{projectId}/presence

SUBSCRIBE
/topic/projects/{projectId}/presence

SNAPSHOT
/user/queue/projects/{projectId}/presence
```

## Seguridad

Los destinos `/presence` pasan por `ProjectStompAuthorizationInterceptor`.

El cliente envía eventId, clientId, tipo y selección/cursor.

El servidor obtiene userId y displayName desde la identidad autenticada; el cliente no controla esos datos.

## Registro efímero

`ProjectPresenceRegistry` vive solamente en memoria y se indexa por STOMP sessionId.

No existe entidad ni tabla JPA de presencia.

Reiniciar Spring vacía la presencia y es comportamiento correcto.

## Eventos

- USER_JOINED
- USER_LEFT
- USER_SELECTED_ELEMENT
- USER_MOVED_CURSOR
- PRESENCE_SNAPSHOT

## Desconexión

Angular intenta USER_LEFT en una salida normal.

Spring usa `SessionDisconnectEvent` como mecanismo autoritativo para limpiar pestañas cerradas, pérdida de red o cierre inesperado.

## Selección

La selección remota contiene `elementId` y `elementType = CLASS | RELATIONSHIP`.

No modifica la selección local ni `ProjectDocument`.

## Cursor

El frontend convierte PointerEvent a coordenadas locales JointJS mediante `clientToLocalPoint()`.

Los clientes remotos proyectan esas coordenadas a su viewport con `localToClientPoint()`.

Por ello zoom y pan pueden diferir entre clientes.

## Throttling

`USER_MOVED_CURSOR` utiliza `auditTime(67)`, aproximadamente 15 eventos por segundo.

Join, leave y selección se transmiten inmediatamente.

## UI

El workspace muestra sesiones conectadas, selección remota y cursores remotos.

Los cursores son overlays visuales, nunca JointJS cells.

## Revisión

La prueba E2E comprueba que los eventos de presencia no incrementan la revisión.

## Estado final de permisos y alcance

La política central de proyecto ya reconoce OWNER/EDITOR y C2-cu31-002 ya crea memberships mediante invitaciones reales.

C2-cu31-003 completa la validación: un EDITOR con cuenta/JWT distintos al OWNER publica presencia, el OWNER la recibe y la revisión UML permanece intacta. NONE es rechazado por la política STOMP. Además, `ProjectPresenceController` ejecuta `requireEdit` nuevamente como defensa en profundidad.
