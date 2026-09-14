# Ciclo 5 — Domain Manifest semántico

**Fase PUDS:** Construcción
**Estado:** CERRADO
**Apertura:** 13 de septiembre de 2026
**Caso de uso objetivo:** CU-16 — Generar Domain Manifest

## Objetivo

Cerrar la brecha semántica entre la aplicación Spring generada y sus consumidores posteriores mediante un `domain-manifest.json` determinista, validado y trazable a los UUID del modelo ClassForge.

## Scope

```text
SpringGenerationModel ---------+
                               |
SpringApiGenerationPlan -------+--> DomainManifestPlan --> domain-manifest.json
                               |
SpringApiContract -------------+
```

El ciclo conserva ambos modos ya cerrados:

- `SIMPLE_CRUD`;
- `AUTH_INFORMATION_SYSTEM`.

## Decisiones fijadas

1. El archivo vive en la raíz del proyecto/ZIP generado como `domain-manifest.json`.
2. Schema v1 usa `schemaVersion: "1.0"`.
3. El manifest se genera directamente desde las IR/planes canónicos; no se reconstruye parseando OpenAPI/Postman.
4. Conserva UUID estables de clases, atributos y relaciones cuando existen en el modelo fuente.
5. Describe entidades, IDs, atributos, relaciones, herencia, capacidades, operationIds y Auth.
6. `operationId` debe coincidir exactamente con CU-15.
7. `displayName` parte del nombre lógico UML y `aliases` empieza vacío; no hay pluralización/aliases heurísticos.
8. Password Auth es sensible/write-only y nunca es legible/buscable/filtrable/ordenable.
9. Simple no contiene metadata Auth activa.
10. La salida es determinista y se valida antes de entregar el ZIP.

## Riesgos principales

- duplicar OpenAPI en lugar de aportar semántica;
- perder UUIDs al atravesar las IR de generación;
- representar incorrectamente herencia/relaciones flattenadas por DTO;
- marcar capacidades que el CRUD real no soporta;
- filtrar password o metadata sensible;
- deriva entre operationIds OpenAPI/Postman/Manifest;
- introducir heurísticas lingüísticas no respaldadas por el UML.

## Criterios de salida

CU-16 sólo podrá cerrarse si:

- Simple y Auth producen `domain-manifest.json` parseable;
- el schema v1 se valida estructural y semánticamente;
- UUIDs fuente y referencias internas son resolubles;
- operaciones Manifest == operaciones `SpringApiContract` == OpenAPI/Postman;
- relaciones/IDs simples y compuestos quedan representados correctamente;
- Auth refleja entidad/username/password/JWT sin fuga de password;
- dos generaciones equivalentes producen bytes y SHA idénticos;
- los proyectos generados continúan aprobando build + `contextLoads()` + H2;
- CU-13, CU-14 y CU-15 permanecen verdes.

## Fuera de alcance

- generación Angular (CU-17);
- Flutter mobile (CU-18);
- ejecución de lenguaje natural/voz (CU-19);
- edición manual del manifest;
- inferencia LLM de aliases/plurales;
- nuevas reglas de negocio o roles.

## Resultado de cierre

C5-cu16-001 implementa `DomainManifestPlan`, renderer determinista y validación fail-closed. El gate `domainManifestAcceptance` cubre relaciones, ID compuesto, Auth y JOINED; CU-13/14/15 permanecen verdes.

Evidencia canónica:

- `../../evidence/cu16/cu16-closure-report.md`;
- `../../evidence/cu16/cu16-acceptance.json`.

Por tanto:

```text
Ciclo 5: CERRADO
CU-16: CERRADO
```
