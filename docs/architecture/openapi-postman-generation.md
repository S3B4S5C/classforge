# Generación OpenAPI y Postman — CU-15

**Estado:** IMPLEMENTADO / CU-15 CERRADO.
**Ciclo:** 4.

## Principio

OpenAPI y Postman son proyecciones del mismo contrato HTTP canónico. Ninguno se reconstruye inspeccionando el código Java generado y Postman no se mantiene como contrato paralelo.

```text
RelationalModel
  -> SpringGenerationModel
  -> SpringApiGenerationPlan
  -> SpringApiContract
       |             |
       v             v
  OpenAPI 3.0.3   Postman 2.1
       |             |
       +------v------+
              |
      GeneratedProject
              |
  GeneratedProjectValidator
              |
       deterministic ZIP
```

## Fuente de verdad

`SpringApiGenerationPlan` de CU-14 fija entidades, DTOs, relaciones y modo Simple/Auth. CU-15 derivará una IR `SpringApiContract` sin persistencia, adecuada para representar:

- path + método HTTP;
- operationId;
- parámetros path/query;
- request body;
- responses;
- schemas;
- seguridad;
- ejemplos estables.

Los renderers de Java, OpenAPI y Postman deben compartir semántica; ninguna template vuelve a inferir dominio desde nombres libres.

## OpenAPI

Target `3.0.3`.

El documento debe describir exactamente:

- list/count/create/get/update/delete;
- IDs simples y compuestos;
- `q`, filtros, sorting y paginación;
- request/response DTOs;
- relaciones por IDs;
- errores 400/404/409.

Auth agrega Bearer JWT global y las operaciones públicas bootstrap/login. El password es input-only y nunca aparece en schemas de respuesta.

## Postman

Collection schema v2.1.

La colección usa `{{baseUrl}}`, organiza requests por entidad y reproduce los mismos parámetros/bodies que OpenAPI. En Auth, `Bootstrap` y `Login` guardan `accessToken` como `jwt`; requests protegidos utilizan Bearer `{{jwt}}`.

## Reproducibilidad

No se ejecuta Spring, no se consulta `/v3/api-docs` y no se llama a `openapi-to-postman`. Los artefactos se generan in-memory antes del ZIP, se validan y se ordenan determinísticamente.

Archivos finales:

```text
<generated-project>/
├── openapi.yaml
├── postman_collection.json
├── build.gradle
├── gradlew
├── gradlew.bat
└── src/...
```

## Límite con CU-16/CU-17

CU-15 produce contratos y herramientas de consumo manual. `domain-manifest.json` pertenece a CU-16. El cliente TypeScript y frontend Angular pertenecen a CU-17 y podrán consumir `openapi.yaml` sin cambiar la autoridad de CU-15.
