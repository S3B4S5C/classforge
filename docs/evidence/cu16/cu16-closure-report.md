# CU-16 closure report

**Caso:** CU-16 — Generar Domain Manifest
**Incremento:** C5-cu16-001
**Estado:** CERRADO
**Fecha:** 13 de septiembre de 2026

## Entrega aceptada

Cada export CU-14/15 incorpora `domain-manifest.json` schema `1.0`, generado determinísticamente desde los mismos modelos/planes canónicos que producen JPA, CRUD/Auth y el contrato HTTP.

## Matriz de gates

| Gate | Resultado |
|---|---|
| CU-16 focal planner/rendering/validator | PASS |
| CU-16 `domainManifestAcceptance` | PASS |
| CU-13 `springGenerationAcceptance` | PASS |
| CU-14 `springCrudGenerationAcceptance` | PASS |
| CU-15 `springApiArtifactsAcceptance` | PASS |
| Full backend | PASS |
| Frontend generation tests | PASS |
| Angular production build | PASS |
| OpenSpec/static audit | PASS |
| `git diff --check` | PASS |

## Semántica validada

- UUIDs de clases, atributos y relaciones;
- IDs simples y compuestos;
- relaciones directas;
- herencia JOINED;
- capabilities y operationIds canónicos;
- Simple sin Auth activa;
- Auth con credential entity, username/password, JWT y privacidad del password.

## Reproducibilidad

Cada fixture se genera dos veces y exige manifest y ZIP byte-identical. `GeneratedProjectValidator` valida parseabilidad, referencias internas y paridad exacta con el contrato CU-15 antes de entregar el archivo.

## Resultado PUDS

CU-16 y Ciclo 5 quedan CERRADOS. El siguiente candidato es CU-17 — frontend web Angular generado.
