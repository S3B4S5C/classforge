# ClassForge — Autenticacion y ownership

## Decision

ClassForge utiliza autenticacion local con JWT.

### Backend

- Spring Security.
- BCrypt para contrasenas.
- JWT HMAC firmado.
- API stateless.
- `ownerId` persistido en cada Project.

### Frontend

- Angular 22.
- Angular Material.
- JWT en `localStorage` como dato de sesion.
- Interceptor agrega `Authorization: Bearer`.
- Guard protege `/projects` y `/projects/:id`.

## Regla de persistencia

La propiedad del proyecto nunca depende del navegador.

```text
Cuenta
   |
   +-- JWT -> identifica la sesion
   |
   +-- User.id
         |
         +-- Project.ownerId
```

Si se borra el token:

```text
se pierde sesion
   ↓
login nuevamente
   ↓
mismo User.id
   ↓
mismos proyectos
```

## Colaboracion futura

```text
User (owner)
   |
Project
   |
   +-- ProjectMembership -- User (collaborator)
   |
   +-- ProjectInvitation -- token/url
```

## Seguridad

- Correo unico normalizado.
- Password hash BCrypt.
- JWT con expiracion.
- Secret configurable por `CLASSFORGE_JWT_SECRET`.
- Endpoints de proyecto autenticados.
- Acceso a proyecto filtrado por usuario.
- 404 para UUID de proyecto ajeno.
- CSRF deshabilitado porque la API usa Bearer token stateless, no cookies de sesion.

## Nota H2

Proyectos creados antes de autenticacion pueden quedar con `owner_id = null`.

No se asignan automaticamente a una cuenta porque no existe evidencia de propiedad. Los proyectos nuevos siempre se crean con owner.