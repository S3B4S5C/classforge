# C2-cu31-001 — Membership y política de acceso

**Ciclo:** 2  
**Caso:** CU-31  
**Incremento:** 001

## Objetivo

Introducir membership `EDITOR` persistente sin reemplazar `Project.ownerId`, centralizar OWNER/EDITOR/NONE y permitir que REST y biblioteca trabajen con proyectos compartidos.

## Modelo

```text
Project.ownerId
    -> OWNER

ProjectMembership
    -> EDITOR
```

## Matriz

| Operación | OWNER | EDITOR | NONE |
|---|:---:|:---:|:---:|
| leer/listar | sí | sí | no |
| validar | sí | sí | no |
| guardar | sí | sí | no |
| renombrar | sí | no | no |

## Persistencia

Se agrega únicamente `project_memberships`. No se modifica `projects`, `owner_id`, `uml_model` ni revisiones existentes.

## Frontend

`Project` expone `accessRole`. La biblioteca diferencia Propietario y Compartido · Editor. Renombrar queda visible solo para OWNER.

## Realtime

STOMP delega en la política central. La prueba multi-cuenta integral y el hardening quedan para C2-cu31-003.

## Pendiente

C2-cu31-002: invitaciones y UI.  
C2-cu31-003: seguridad realtime/presencia/Assistant y cierre CU-31.
