# C5-cu16-000 — Preflight Domain Manifest

**Fecha:** 13 de septiembre de 2026
**Estado:** READY FOR IMPLEMENTATION
**Caso:** CU-16
**Depende de:** CU-12, CU-13, CU-14 y CU-15 CERRADOS

## Problema

CU-15 entrega un contrato HTTP reproducible, pero OpenAPI está orientado al transporte. CU-17 y el futuro asistente de la aplicación necesitan una vista semántica estable del dominio: identidad ClassForge, atributos, relaciones, capacidades, herencia y vínculos directos con operationIds.

## Decisión de autoridad

No se parseará `openapi.yaml` para volver a inferir el dominio. El planner de CU-16 combinará información ya canónica:

```text
SpringGenerationModel
SpringApiGenerationPlan
SpringApiContract
        |
        v
DomainManifestPlan
        |
        v
domain-manifest.json
```

Esto permite conservar UUIDs y semántica sin crear una segunda fuente de verdad.

## Schema v1 fijado

Top-level obligatorio:

- `schemaVersion = "1.0"`;
- `generationMode`;
- metadata API (`baseUrl`, nombres de OpenAPI/Postman);
- `authentication`;
- `entities`;
- `operations`.

### Entidad

Debe incluir:

- UUID fuente de clase;
- nombre lógico y nombre de código;
- tabla y endpoint;
- displayName conservador;
- aliases vacíos por defecto;
- definición estructurada del ID;
- herencia;
- atributos;
- relaciones;
- capabilities;
- operationIds aplicables.

### Atributo

Debe incluir UUID fuente, nombres lógico/API/columna, tipo semántico, nullability, identidad, mutabilidad, lectura/escritura, search/filter/sort, sensibilidad/write-only y validaciones básicas.

Tipos v1:

```text
STRING | INTEGER | LONG | DECIMAL | BOOLEAN | DATE | DATETIME | UUID
```

### Relación

Debe preservar UUID de relación y tipo UML cuando exista, tipo API (`ONE_TO_ONE`, `MANY_TO_ONE`, `MANY_TO_MANY`), target estable, optionalidad, request field e información de ID target.

### Operación

Debe referenciar exactamente el `operationId` de CU-15 e incluir capability, entidad, método/path, auth requerida y schemas request/response.

### Auth

Simple: `enabled=false`.

Auth: incluye entidad, username/password attribute UUIDs, `BEARER_JWT`, variable `jwt`, expiración 3600s y operationIds `bootstrapAuthentication` / `loginAuthentication`.

## Reglas de privacidad

El password seleccionado:

- `sensitive=true`;
- `writeOnly=true`;
- `readable=false`;
- `searchable=false`;
- `filterable=false`;
- `sortable=false`;
- required en create/bootstrap;
- optional en update;
- nunca aparece en datos de response.

## Naming lingüístico

CU-16 no introduce heurísticas de idioma. Por defecto:

```text
displayName = logicalName
aliases = []
```

No se inventan plurales.

## Acceptance previsto

Nuevo gate: `domainManifestAcceptance`.

Fixtures mínimas:

1. Simple con ID simple + relaciones;
2. Simple con ID compuesto;
3. Auth con entidad credential y relaciones;
4. JOINED/inheritance para verificar identidad y herencia.

El acceptance debe validar:

- JSON parseable y schema v1;
- referencias UUID resolubles;
- paridad exacta de operationIds con `SpringApiContract`/OpenAPI/Postman;
- password privacy;
- Simple sin Auth activa;
- determinismo byte/SHA;
- build + `contextLoads()` + H2 de proyecto generado;
- regresiones `springGenerationAcceptance`, `springCrudGenerationAcceptance`, `springApiArtifactsAcceptance`;
- full backend, frontend generation tests, Angular build y `git diff --check`.

## Límite con CU-17

CU-16 entrega metadata; no genera interfaz Angular. CU-17 consumirá el manifest como entrada del generador de UI, junto con el contrato API cuando necesite detalles HTTP.
