# Ciclo 4 — Contrato API reproducible

**Fase PUDS:** Construcción
**Estado:** CERRADO
**Apertura:** 13 de septiembre de 2026
**Caso de uso objetivo:** CU-15 — Generar OpenAPI y Postman

## Objetivo

Convertir la API ejecutable cerrada en CU-14 en un contrato portable, auditable y reproducible, sin introducir una segunda fuente de verdad ni depender de ejecutar la aplicación generada.

## Scope

```text
SpringApiGenerationPlan
        |
        v
contrato HTTP canónico
   |             |
   v             v
openapi.yaml   postman_collection.json
   \_____________/
         |
         v
 mismo ZIP Spring generado
```

El ciclo cubre los dos modos existentes:

- `SIMPLE_CRUD`;
- `AUTH_INFORMATION_SYSTEM`.

No cubre CU-16 Domain Manifest, CU-17 cliente/frontend Angular, CU-18 mobile ni nuevas reglas de negocio.

## Decisiones fijadas

1. `openapi.yaml` y `postman_collection.json` se generan siempre; no hay checkbox independiente.
2. OpenAPI target: `3.0.3`.
3. Los dos artefactos nacen del mismo contrato canónico derivado de CU-14.
4. La exportación no ejecuta el backend generado ni herramientas externas.
5. `openapi.yaml` y `postman_collection.json` viven en la raíz del ZIP/proyecto generado.
6. Postman usa colección schema v2.1 y variable `baseUrl=http://localhost:8080`.
7. Auth agrega `jwt`, bootstrap/login públicos y Bearer token para operaciones protegidas.
8. Password es write-only y no aparece en responses.
9. Simple no incluye security scheme, jwt ni requests Auth.
10. La salida es determinista y se valida antes de entregar el ZIP.

## Riesgos principales

- deriva entre controllers, OpenAPI y Postman;
- documentación incorrecta de IDs compuestos y parámetros `filter.<campo>`;
- fuga accidental del password en schemas/examples;
- colección Auth que no propague el token correctamente;
- pérdida de determinismo por orden no estable en paths/schemas/items.

## Criterios de salida

CU-15 se cierra porque:

- Simple/Auth produzcan YAML/JSON parseables;
- cada operación HTTP generada esté representada en OpenAPI y Postman;
- Postman no invente operaciones fuera del contrato;
- Auth aplique correctamente la semántica de seguridad fijada;
- password no se exponga;
- dos generaciones equivalentes produzcan artefactos byte-identical;
- los proyectos generados sigan aprobando build + `contextLoads()` + H2;
- la suite completa CU-13/CU-14 continúe verde.

## Evidencia esperada

La evidencia final está en `docs/evidence/cu15/`. El gate `springApiArtifactsAcceptance` valida Simple/Auth, parseabilidad, paridad de operaciones, determinismo y build/context/H2 de los proyectos generados.
