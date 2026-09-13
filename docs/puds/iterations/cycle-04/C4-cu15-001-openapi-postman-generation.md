# C4-cu15-001 — OpenAPI/Postman generation and acceptance

**Fecha:** 13 de septiembre de 2026
**Estado:** CLOSED
**Caso:** CU-15
**Depende de:** CU-12, CU-13 y CU-14 CERRADOS

## Resultado

CU-15 añade dos artefactos deterministas a cada export con API CU-14:

```text
<artifact>/
├── openapi.yaml
├── postman_collection.json
├── build.gradle
└── src/...
```

Ambos nacen de `SpringApiContract`, una IR HTTP efímera derivada de `SpringApiGenerationPlan`. No se inspecciona Java generado, no se levanta la aplicación y no se invoca un conversor externo.

## Contrato canónico

`SpringApiContractPlanner` fija para cada entidad:

- list, count, create, get, update y delete;
- paths para ID simple y `/by-id` para ID compuesto;
- `q`, `sort`, `direction`, `page`, `size` y `filter.<campo>`;
- request/response schema names;
- status de éxito;
- requirement de autenticación.

En Auth añade `bootstrapAuthentication` y `loginAuthentication` como operaciones públicas.

## OpenAPI

`openapi.yaml` usa OpenAPI 3.0.3, schemas request/response, page/count/error, IDs simples/compuestos y relaciones por IDs.

En `AUTH_INFORMATION_SYSTEM`:

- `bearerAuth` HTTP bearer/JWT global;
- bootstrap/login con `security: []`;
- password `writeOnly` en request;
- password ausente de response schemas.

En `SIMPLE_CRUD` no hay security scheme ni endpoints Auth.

## Postman

`postman_collection.json` usa Collection schema v2.1:

- `baseUrl=http://localhost:8080`;
- carpeta por entidad;
- request name = `operationId` canónico;
- parámetros y bodies coherentes con el mismo contrato;
- Auth agrega variables `jwt`, `username`, `password`;
- bootstrap/login guardan `accessToken` en `jwt`;
- colección Auth aplica Bearer `{{jwt}}` y endpoints públicos usan `noauth`.

## Fail-closed

`GeneratedProjectValidator` valida que OpenAPI/Postman aparezcan juntos, parsea YAML/JSON y exige igualdad exacta entre operationIds OpenAPI y nombres de requests Postman antes de permitir el ZIP.

## Acceptance

Gate dedicado: `springApiArtifactsAcceptance`.

Verifica:

- Simple y Auth;
- OpenAPI parseable;
- Postman JSON parseable;
- paridad exacta de operaciones;
- ID compuesto `/by-id`;
- filtros expresivos;
- seguridad Auth y ausencia de seguridad Simple;
- no exposición de password en response schemas;
- token capture en Postman;
- determinismo byte-a-byte del ZIP;
- build del proyecto generado;
- `contextLoads()` no skipped;
- H2.

El parche de cierre reejecuta además CU-13, CU-14, backend completo, tests frontend de generación, Angular build y `git diff --check`.
