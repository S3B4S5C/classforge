# Membresías e invitaciones de proyecto

## Estado

C2-cu31-001 implementó membership persistente y política central de acceso. C2-cu31-002 implementó invitaciones internas y su UI. C2-cu31-003 cerró el hardening multi-cuenta de STOMP, presencia y Assistant.

## Decisión

`Project.ownerId` no se reemplaza.

```text
Project.ownerId      -> OWNER implícito
ProjectMembership    -> EDITOR persistido
ProjectInvitation    -> solicitud persistente, no acceso
```

Solo existen dos roles efectivos en CU-31: OWNER y EDITOR. No se introduce VIEWER ni ACL genérica.

## Política central

`com.classforge.project.access.ProjectAccessService` resuelve OWNER, EDITOR o NONE para `(userId, projectId)`.

OWNER puede leer, editar, validar, guardar, renombrar y administrar invitaciones. EDITOR puede leer, editar, validar y guardar. NONE no obtiene acceso.

## Membership

```text
project_memberships
- id UUID PK
- project_id UUID
- user_id UUID
- role EDITOR
- created_at
- UNIQUE(project_id, user_id)
```

## Invitación interna

```text
project_invitations
- id UUID PK
- project_id UUID
- invited_email VARCHAR(160)
- invited_by_user_id UUID
- status PENDING | ACCEPTED | DECLINED | CANCELLED
- created_at
- responded_at nullable mientras PENDING
```

El correo se normaliza con `trim + lowercase(Locale.ROOT)`, igual que las cuentas locales. No existe SMTP, token compartible ni dependencia de Internet.

La invitación puede existir antes de la cuenta:

```text
OWNER invita correo B
B aún no existe
B se registra más tarde con el mismo correo normalizado
GET /api/project-invitations encuentra PENDING
B acepta
se crea ProjectMembership EDITOR
```

## Aceptación transaccional

```text
lock ProjectInvitation
 -> comprobar PENDING
 -> comprobar correo autenticado == invitedEmail
 -> comprobar que el usuario no es OWNER
 -> comprobar que no existe membership
 -> crear ProjectMembership EDITOR
 -> marcar ProjectInvitation ACCEPTED
```

La transacción no toca `ProjectDocument` ni incrementa `Project.revision`.

## Reglas de integridad funcional

- owner no se invita a sí mismo;
- no se permite PENDING duplicado para el mismo proyecto/correo;
- no se invita a un usuario ya miembro;
- aceptar una invitación de otro correo responde como recurso no disponible;
- decline/cancel no crean membership;
- solo OWNER ve pendientes administrativos del proyecto;
- no se elimina membership activa en CU-31 para evitar una revocación STOMP incompleta.

C2-cu31-003 serializa mutaciones de invitaciones mediante lock pesimista del proyecto; accept/decline/cancel bloquean además la invitación. Dos invites concurrentes conservan un solo PENDING y dos accepts concurrentes crean una sola membership.

## Biblioteca

`GET /api/projects` devuelve propios y compartidos. `ProjectResponse.accessRole` distingue OWNER/EDITOR.

`GET /api/project-invitations` usa el correo de la cuenta autenticada y devuelve solo invitaciones PENDING destinadas a esa cuenta.

## Workspace

`GET /api/projects/{id}/collaborators` devuelve owner y editores a OWNER/EDITOR. Las invitaciones PENDING solo se incluyen cuando el viewer es OWNER.

El dialog Material `Colaboradores` reutiliza el control compacto existente del workspace y mantiene también la presencia efímera de CU-07.

## Realtime

C2-cu31-003 conecta directamente STOMP con la política central: SUBSCRIBE requiere lectura y SEND edición. Presencia revalida edición dentro del controller. Los tests cubren OWNER/EDITOR/NONE y Assistant texto/voz membership-aware.

<!-- C2-CU31-FIX-001-INVITATION-FEEDBACK -->
## Corrección post-cierre CU-31 — feedback y refresco de invitaciones

La creación de una invitación devuelve feedback explícito al OWNER usando la respuesta real del backend. En éxito se muestra el correo normalizado persistido; en conflicto se conserva el `message` estructurado enviado por Spring (`OWNER_CANNOT_BE_INVITED`, `INVITATION_ALREADY_PENDING`, `USER_ALREADY_MEMBER`, etc.). Un fallo de red se distingue de un rechazo funcional.

La bandeja de `/projects` continúa consultando `GET /api/project-invitations`, pero ahora hace un refresco silencioso cada 10 segundos mientras la página está abierta. Esto cubre el caso en el que la cuenta invitada ya tenía la biblioteca abierta cuando otro usuario creó la invitación. No se introduce un nuevo canal STOMP para invitaciones.

La prueba de integración `ProjectInvitationIntegrationTests.invitationCanPrecedeRegistrationAndAcceptCreatesPersistentEditorWithoutChangingRevision()` sigue siendo la evidencia backend de que una invitación PENDING es visible por correo autenticado y puede preceder al registro de la cuenta.
