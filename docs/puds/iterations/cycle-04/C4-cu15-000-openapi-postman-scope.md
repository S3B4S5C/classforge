# C4-cu15-000 — Preflight OpenAPI/Postman

**Fecha:** 13 de septiembre de 2026
**Estado:** CLOSED / IMPLEMENTED BY C4-cu15-001
**Caso:** CU-15
**Depende de:** CU-12, CU-13 y CU-14 CERRADOS

## Problema

CU-14 ya genera una API real, pero el consumidor humano/automatizado todavía no recibe un contrato OpenAPI ni una colección Postman reproducible. Generarlos mediante introspección runtime o conversores externos introduciría pasos no deterministas y una dependencia innecesaria de procesos fuera del pipeline de ClassForge.

## Decisión

CU-15 reutilizará la IR/plan de CU-14. Se introducirá un contrato HTTP canónico de generación y dos renderers puros:

```text
SpringApiGenerationPlan
  -> SpringApiContract
      -> OpenApiRenderer
      -> PostmanCollectionRenderer
```

El contrato conservará paths, métodos, operationIds, parámetros, schemas, responses y metadata de seguridad suficientes para producir ambos formatos sin reinterpretar templates Java.

## OpenAPI fijado

Archivo: `openapi.yaml`.

- versión `3.0.3`;
- `info.title` derivado del artifact;
- server default `http://localhost:8080`;
- operationIds estables;
- schemas request/response por entidad;
- schema de `PageResponse`;
- parámetros `q`, `sort`, `direction`, `page`, `size`;
- filtros explícitos `filter.<campo>` cuando apliquen;
- IDs simples como path param;
- IDs compuestos mediante query params en `/by-id`;
- responses 200/201/204 y errores 400/404/409 coherentes con CU-14.

Auth:

- `components.securitySchemes.bearerAuth` HTTP bearer/JWT;
- seguridad global Bearer;
- `/api/auth/bootstrap` y `/api/auth/login` con `security: []`;
- `LoginRequest` y `LoginResponse`;
- password aceptado en requests pero ausente de responses / marcado `writeOnly`.

Simple:

- sin `securitySchemes`;
- sin `/api/auth/*`;
- sin password especial.

## Postman fijado

Archivo: `postman_collection.json`.

- schema Collection v2.1;
- variable `baseUrl` = `http://localhost:8080`;
- carpeta por entidad;
- requests List, Count, Create, Get, Update y Delete;
- query/path params según OpenAPI;
- bodies JSON de ejemplo deterministas;
- mismos nombres/operationIds rastreables al contrato.

Auth:

- carpeta `Authentication`;
- request `Bootstrap` y `Login`;
- variable `jwt`;
- test script de bootstrap/login guarda `pm.response.json().accessToken`;
- requests protegidos usan Bearer `{{jwt}}`;
- endpoints públicos no heredan Bearer.

Simple:

- sin carpeta Authentication;
- sin variable jwt ni auth de colección.

## Determinismo y validación

- ordenar paths por path+method;
- ordenar schemas/fields/params por regla estable;
- YAML con formato canónico definido por renderer;
- JSON Postman serializado con orden estable y newline final;
- validar OpenAPI semánticamente, no sólo con grep;
- parsear Postman como JSON y validar estructura/URLs/auth;
- cross-check: operaciones Postman == operaciones OpenAPI esperadas;
- regenerar al menos una fixture y exigir bytes/SHA idénticos.

## Acceptance previsto

Nuevo gate: `springApiArtifactsAcceptance`.

Fixtures mínimas:

1. `simple`: relaciones + ID simple/compuesto + filtros/paging;
2. `auth`: entidad Auth + login/bootstrap + endpoints protegidos.

El gate debe conservar también:

- `springGenerationAcceptance`;
- `springCrudGenerationAcceptance`;
- full backend;
- frontend generation tests;
- Angular build;
- `git diff --check`.

## Fuera de alcance

- Domain Manifest (CU-16);
- cliente TypeScript/frontend generado (CU-17);
- Swagger UI/runtime introspection como requisito;
- refresh tokens/roles;
- lógica de negocio no modelada.

## Nota de implementación

`C4-cu15-001` materializa las dos proyecciones puras bajo una fachada única `SpringApiArtifactsRenderer`: un método renderiza OpenAPI y otro Postman desde el mismo `SpringApiContract`. La decisión arquitectónica de una sola IR y dos proyecciones se conserva; no existe un segundo contrato paralelo.
