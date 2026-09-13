# CU-15 closure report

**Caso:** CU-15 — Generar OpenAPI y Postman
**Incremento:** C4-cu15-001
**Estado:** CERRADO
**Fecha:** 13 de septiembre de 2026

## Entrega aceptada

Cada export CU-14 incluye `openapi.yaml` OpenAPI 3.0.3 y `postman_collection.json` Postman v2.1 generados desde un `SpringApiContract` común. No hay introspección runtime ni conversor externo.

## Matriz de gates

| Gate | Resultado |
|---|---|
| CU-15 focal contract/rendering | PASS |
| CU-15 `springApiArtifactsAcceptance` Simple/Auth | PASS |
| CU-13 `springGenerationAcceptance` | PASS |
| CU-14 `springCrudGenerationAcceptance` | PASS |
| Full backend | PASS |
| Frontend generation tests | PASS |
| Angular production build | PASS |
| `git diff --check` | PASS |

## Seguridad

Auth documenta Bearer JWT, bootstrap/login públicos y requests protegidos. Password se acepta como input write-only y no aparece en response schemas. Simple no contiene configuración de seguridad ni endpoints Auth.

## Reproducibilidad

El acceptance genera dos veces la misma fixture y exige ZIP byte-identical. El validator parsea ambos artefactos y exige paridad exacta entre los operationIds de OpenAPI y los requests Postman.

## Resultado PUDS

CU-15 y Ciclo 4 quedan CERRADOS. El siguiente candidato es CU-16 — Domain Manifest.
