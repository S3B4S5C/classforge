# C2-cu31-002 — Invitaciones y UI

**Ciclo:** 2  
**Caso:** CU-31  
**Incremento:** 002  
**Estado:** COMPLETADO

## Objetivo

Convertir la membership EDITOR introducida en C2-cu31-001 en un flujo funcional entre cuentas reales mediante invitaciones internas persistentes por correo, sin SMTP, enlaces externos ni tokens de invitación.

## Flujo

```text
OWNER escribe correo
    -> ProjectInvitation PENDING
    -> el correo puede no tener cuenta todavía
    -> el usuario se registra/inicia sesión con ese correo
    -> GET /api/project-invitations
    -> aceptar
    -> crear ProjectMembership EDITOR
    -> ProjectInvitation ACCEPTED
    -> proyecto aparece como Compartido · Editor
```

La aceptación es transaccional. La invitación no concede acceso mientras permanece `PENDING`.

## Persistencia

Se agrega únicamente `project_invitations`:

```text
id UUID PK
project_id UUID
invited_email VARCHAR(160) normalizado
invited_by_user_id UUID
status PENDING | ACCEPTED | DECLINED | CANCELLED
created_at
responded_at nullable mientras PENDING
```

Se conservan sin cambios `Project.ownerId`, `projects.uml_model`, `ProjectDocument` y la revisión UML.

## Reglas

- solo OWNER invita y cancela invitaciones pendientes;
- OWNER no puede invitarse a sí mismo;
- no se crea una segunda invitación PENDING para el mismo proyecto/correo;
- no se invita a un usuario que ya tiene membership EDITOR;
- aceptar exige que el correo autenticado coincida con `invitedEmail` normalizado;
- aceptar crea exactamente una membership EDITOR y marca la invitación ACCEPTED en la misma transacción;
- rechazar marca DECLINED y no crea membership;
- cancelar marca CANCELLED y no concede acceso;
- EDITOR puede ver owner y editores, pero no las invitaciones pendientes del proyecto;
- no existe eliminación de membership activa en este incremento.

## Endpoints

```text
POST   /api/projects/{projectId}/invitations
GET    /api/projects/{projectId}/invitations
DELETE /api/projects/{projectId}/invitations/{invitationId}
GET    /api/projects/{projectId}/collaborators

GET  /api/project-invitations
POST /api/project-invitations/{invitationId}/accept
POST /api/project-invitations/{invitationId}/decline
```

## Frontend

Biblioteca:

- mantiene la grilla de proyectos propios/compartidos;
- añade una bandeja compacta de invitaciones pendientes;
- permite Aceptar/Rechazar;
- al aceptar se recarga la biblioteca y aparece el proyecto compartido.

Workspace:

- el control visual `Colaboradores` abre un dialog Material;
- OWNER ve propietario, editores, pendientes y el formulario de invitación;
- EDITOR ve la información de miembros sin controles administrativos;
- el mismo dialog conserva la visualización de presencia conectada de CU-07;
- no se agrega una card permanente al workspace.

## Pruebas

La integración cubre:

1. invitar un correo antes de que exista la cuenta;
2. registrar luego esa cuenta;
3. ver la invitación pendiente;
4. aceptar y obtener membership EDITOR;
5. comprobar que la revisión UML sigue en 0;
6. self-invite rechazado;
7. pending duplicado rechazado;
8. usuario con correo ajeno no puede aceptar;
9. decline no crea membership;
10. una invitación puede recrearse después de decline/cancel;
11. miembro activo no puede ser invitado otra vez;
12. EDITOR no puede invitar, listar pendientes administrativos ni cancelar.

## Frontera con C2-cu31-003

Este incremento no declara cerrada la seguridad multi-cuenta realtime. C2-cu31-003 debe validar de extremo a extremo:

```text
OWNER A + EDITOR B + NONE C
REST + STOMP operations + presence + Assistant
```

y endurecer carreras/duplicados que dependan de concurrencia real.

## Estado PUDS

```text
CU-31 = EN PROGRESO
Ciclo 2 = ABIERTO
Siguiente incremento = C2-cu31-003
CU-09 = PLANIFICADO EN ESTE CICLO
```
