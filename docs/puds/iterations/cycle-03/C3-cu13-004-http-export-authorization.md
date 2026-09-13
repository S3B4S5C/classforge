# C3-cu13-004 - HTTP export and authorization

**Estado:** VALIDADO / CERRADO

`POST /api/projects/{projectId}/generation/spring-boot` receives `baseRevision`, `artifactName` and `basePackage`. It resolves identity through `CurrentUser`, calls `ProjectAccessService.requireEdit` before generation, and delegates only to `SpringBootGenerationService`. Real roles are OWNER and EDITOR, both allowed; NONE follows the existing 404 policy. Success returns 200 `application/zip`, the artifact filename in `Content-Disposition`, and unmodified binary bytes. Revision conflict remains the service-authoritative `ProjectRevisionConflictException` mapped to 409. The controller neither renders, archives, persists nor mutates projects.

Frontend UX, generated-project acceptance, CRUD/security in generated backends and OpenAPI/Postman remain out of scope.

## Stable generation diagnostics

La frontera HTTP de CU-13 es fail-closed: cualquier rechazo de mapping, planning, rendering, validacion del proyecto generado o archivado termina en JSON de error y nunca devuelve un ZIP parcial.

| Categoria estable | HTTP | Origen |
|---|---:|---|
| `INVALID_GENERATION_CONFIGURATION` | 400 | `SpringGenerationException` compuesto solo por `INVALID_ARTIFACT_NAME` / `INVALID_BASE_PACKAGE` |
| `STALE_PROJECT_REVISION` | 409 | `ProjectRevisionConflictException` |
| `RELATIONAL_MAPPING_REJECTED` | 400 | `RelationalMappingException`; preserva `code`, `elementId`, `path` y `message` de CU-12 |
| `SPRING_MODEL_REJECTED` | 400 | `SpringGenerationException` con cualquier diagnostico no perteneciente a configuracion |
| `TEMPLATE_RENDER_FAILED` | 500 | `GeneratedProjectException` originada en template/static resource |
| `GENERATED_PROJECT_INVALID` | 500 | `GeneratedProjectException` de validacion del arbol virtual |
| `ARCHIVE_FAILED` | 500 | `SpringArchiveException` |

`PROJECT_NOT_FOUND` permanece como contrato de acceso 404 fuera de las siete categorias de generacion. Los fallos internos 500 usan mensaje publico generico y no exponen detalles de FreeMarker, `IOException`, classpath ni stack traces. Solo el happy path entrega `Content-Type: application/zip` y `Content-Disposition`.
