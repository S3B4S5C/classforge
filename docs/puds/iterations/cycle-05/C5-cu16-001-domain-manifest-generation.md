# C5-cu16-001 — Domain Manifest generation and acceptance

**Fecha:** 13 de septiembre de 2026
**Estado:** CLOSED
**Caso:** CU-16
**Depende de:** CU-12, CU-13, CU-14 y CU-15 CERRADOS

## Resultado

CU-16 añade `domain-manifest.json` schema `1.0` a cada export con API CU-14. El artefacto se genera directamente desde `SpringGenerationModel`, `SpringApiGenerationPlan` y `SpringApiContract`; no se reconstruye parseando OpenAPI/Postman.

## Contrato semántico

El manifest conserva:

- modo de generación Simple/Auth;
- UUIDs fuente de clases, atributos y relaciones;
- nombres lógico/código/tabla/endpoint;
- IDs simples/compuestos;
- herencia JOINED;
- atributos, privacidad y validaciones básicas;
- relaciones y targets estables;
- capacidades CRUD/query;
- operationIds/métodos/paths exactamente coherentes con CU-15;
- metadata Auth/JWT y selección de username/password.

`displayName` permanece igual al nombre lógico y `aliases=[]`; CU-16 no inventa pluralización ni aliases.

## Privacidad Auth

El atributo password seleccionado se emite como sensible/write-only, no readable/searchable/filterable/sortable, requerido en create/bootstrap y opcional en update. El validator exige esas invariantes antes de permitir el ZIP.

## Fail-closed

`GeneratedProjectValidator` exige que `domain-manifest.json` acompañe a OpenAPI/Postman, parsea JSON, valida schema/mode/API metadata, UUIDs/referencias internas, herencia, relaciones, Auth y paridad exacta de operationIds.

## Acceptance

Gate dedicado: `domainManifestAcceptance`.

Fixtures:

1. Simple con relación y UUIDs fuente;
2. Simple con ID compuesto;
3. Auth con privacidad/password/JWT;
4. JOINED con identidad/herencia.

El acceptance exige determinismo byte-a-byte, manifest parseable, referencias resolubles, paridad con OpenAPI, build del proyecto generado, `contextLoads()` no skipped y H2.

El parche de cierre reejecuta además CU-13, CU-14, CU-15, backend completo, tests frontend de generación, Angular build, OpenSpec y `git diff --check`.
