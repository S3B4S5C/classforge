# C2-cu31-003 — Realtime membership hardening

**Estado:** COMPLETADO.

## Objetivo

Cerrar CU-31 demostrando que una membership EDITOR aceptada funciona sobre todas las fronteras colaborativas y que un usuario NONE no obtiene acceso.

## Implementación

- `ProjectStompAuthorizationInterceptor` depende directamente de `project/access/ProjectAccessService`.
- `SUBSCRIBE` exige `requireRead`; `SEND` exige `requireEdit`.
- Se elimina el adaptador temporal `collaboration.security.ProjectAccessService`.
- `ProjectPresenceController` vuelve a ejecutar `requireEdit` como defensa en profundidad antes de modificar el registro efímero.
- Assistant texto, voz y runtime health utilizan semántica `userId` y heredan la política central mediante `ProjectService.get`.
- Las mutaciones de invitaciones adquieren lock pesimista del proyecto antes de comprobar/crear PENDING o responder, evitando duplicados por carrera.

## Evidencia automática

- E2E de operaciones: OWNER A y EDITOR B usan cuentas/JWT distintos; B crea una clase y ambos reciben el broadcast; el rechazo por revisión sigue siendo privado.
- E2E de presencia: OWNER A observa presencia emitida por EDITOR B sin cambiar la revisión UML.
- Acceso realtime: OWNER y EDITOR pueden SUBSCRIBE/SEND a operations/presence; NONE es rechazado.
- Assistant: EDITOR alcanza texto y voz; NONE falla antes de invocar LLM/STT.
- Concurrencia: dos invites simultáneos dejan un solo PENDING y dos accepts simultáneos crean exactamente una membership.
- `clean build` backend y `npm run build` frontend son obligatorios para aceptar el parche.

## Decisiones conservadas

No se introduce VIEWER, ACL genérica, SMTP, links/tokens públicos, transferencia de ownership ni eliminación de memberships activas. Esta última sigue fuera de alcance porque una revocación correcta requiere invalidar sesiones STOMP ya conectadas.

## Resultado PUDS

```text
CU-31 = CERRADO
Ciclo 2 = ABIERTO
CU-09 = SIGUIENTE
```
