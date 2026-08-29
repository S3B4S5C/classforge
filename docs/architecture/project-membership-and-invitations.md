# Membresías e invitaciones de proyecto

## Estado

C2-cu31-001 implementa membership persistente y política central de acceso. Las invitaciones llegan en C2-cu31-002.

## Decisión

`Project.ownerId` no se reemplaza.

```text
Project.ownerId -> OWNER implícito
ProjectMembership -> EDITOR persistido
```

## Política central

`com.classforge.project.access.ProjectAccessService` resuelve OWNER, EDITOR o NONE para `(userId, projectId)`.

OWNER puede leer, editar, validar, guardar y renombrar. EDITOR puede leer, editar, validar y guardar. NONE no obtiene acceso.

## Persistencia

```text
project_memberships
- id UUID PK
- project_id UUID
- user_id UUID
- role EDITOR
- created_at
- UNIQUE(project_id, user_id)
```

La incorporación es aditiva y compatible con la H2 existente.

## Biblioteca

`GET /api/projects` devuelve propios y compartidos. `ProjectResponse` incluye `accessRole`.

## Realtime

El adaptador de colaboración delega en la política central para evitar reglas duplicadas. C2-cu31-003 completará las pruebas multi-cuenta.

## Invitaciones

C2-cu31-002 agregará `ProjectInvitation` por correo normalizado sin requerir SMTP ni Internet.
