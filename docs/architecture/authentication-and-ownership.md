# ClassForge — Autenticación, ownership y membresía

## Decisión

ClassForge utiliza autenticación local con JWT.

### Backend

- Spring Security.
- BCrypt para contraseñas.
- JWT HMAC firmado.
- API stateless.
- `ownerId` persistido en cada Project.
- `ProjectMembership` persistente para EDITOR.
- `ProjectInvitation` interna por correo normalizado.

### Frontend

- Angular 22.
- Angular Material.
- JWT en `localStorage` como dato de sesión.
- Interceptor agrega `Authorization: Bearer`.
- Guard protege `/projects` y `/projects/:id`.

## Regla de persistencia

La propiedad del proyecto nunca depende del navegador.

```text
Cuenta
   |
   +-- JWT -> identifica la sesión
   |
   +-- User.id
         |
         +-- Project.ownerId -> OWNER
         |
         +-- ProjectMembership.userId -> EDITOR
```

Si se borra el token, se pierde la sesión local. Al iniciar sesión nuevamente con la misma cuenta, el backend reconstruye los proyectos accesibles desde ownership + membership.

## Invitaciones — Ciclo 2

La invitación no es un mecanismo de autenticación ni un permiso temporal.

```text
ProjectInvitation PENDING por invitedEmail
    -> usuario autenticado con el mismo correo
    -> Accept
    -> ProjectMembership EDITOR
```

No existe envío real de email, link público ni token de invitación. Esto conserva el funcionamiento local/offline de ClassForge.

## Seguridad

- correo de usuario único y normalizado;
- password hash BCrypt;
- JWT con expiración;
- secret configurable por `CLASSFORGE_JWT_SECRET`;
- endpoints de proyecto autenticados;
- acceso a proyecto resuelto por política central OWNER/EDITOR/NONE;
- renombrar e invitar requieren OWNER;
- aceptar invitación exige coincidencia con el correo autenticado;
- UUID de proyecto/invitación no concede acceso por sí mismo;
- CSRF deshabilitado porque la API usa Bearer token stateless, no cookies de sesión.

## Nota H2

Proyectos creados antes de autenticación pueden quedar con `owner_id = null` en bases históricas. No se asignan automáticamente a una cuenta porque no existe evidencia de propiedad. Los proyectos nuevos siempre se crean con owner.

Las tablas `project_memberships` y `project_invitations` son aditivas y no requieren borrar la H2 existente.
