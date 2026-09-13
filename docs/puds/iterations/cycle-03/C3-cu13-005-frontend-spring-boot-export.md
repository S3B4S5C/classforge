# C3-cu13-005 - Frontend Spring Boot export UX

**Estado:** VALIDADO / CERRADO

## Objetivo

Cerrar la interfaz mínima de CU-13 para exportar el backend Spring Boot/JPA desde el workspace sin exponer la IR relacional ni decisiones internas del generador.

## Implementado

- Acción `Generar Spring Boot` en la cabecera del workspace.
- Bloqueo preventivo mientras existen cambios locales, guardado activo, operaciones colaborativas pendientes o resincronización/conflicto.
- Diálogo compacto con `artifactName` y `basePackage`; `baseRevision` se toma automáticamente de la revisión autoritativa actual y no es editable.
- Defaults deterministas derivados del nombre del proyecto y validación cliente alineada con `SpringGenerationConfig`.
- Cliente HTTP para `POST /api/projects/{projectId}/generation/spring-boot` usando respuesta `Blob`.
- Nombre de descarga tomado de `Content-Disposition`, con fallback `<artifactName>-backend.zip`.
- Descarga del ZIP en navegador sin persistir contenido en el frontend.
- Traducción de las categorías estables de CU-13 a mensajes concisos y accionables; `STALE_PROJECT_REVISION` guía a sincronizar/actualizar y volver a generar.
- Los detalles diagnósticos completos permanecen disponibles para desarrollo/logging, pero no se muestran como internals técnicos al usuario.

## Verificación

- Tests focales de utilidades: defaults, validación de package, filename de `Content-Disposition`, stale revision y sanitización de fallos internos.
- Build completo del frontend GREEN.
- No se añadieron controles de Security/login, switches de versión, RelationalModel, tablas/FKs ni detalles FreeMarker/JPA planner a la UX.
- No se modificó backend en este incremento.

## Fuera de alcance

La validación estructural adicional de `GeneratedProject`, materialización temporal, compilación/arranque real de los proyectos generados y evidencia final SHA-256 quedan para `C3-cu13-006`.