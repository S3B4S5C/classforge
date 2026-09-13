# CU-17 — Closure report

**Caso:** Generar frontend web Angular
**Ciclo:** 6
**Estado:** CERRADO tras ejecutar el patch runner con todos los gates GREEN.

## Resultado

Cada exportación API incluye un proyecto `frontend/` Angular standalone generado específicamente para el dominio.

### Simple

- dashboard;
- navegación por entidades;
- list/detail/form específicos;
- búsqueda/filtros/sort/paginación;
- CRUD;
- relaciones;
- ID simple/compuesto;
- sin Auth.

### Auth

Incluye lo anterior más:

- login;
- bootstrap inicial;
- JWT Bearer;
- interceptor;
- guard;
- logout;
- 401 -> login;
- password secreto/no readable.

### Personalización

El exportador permite seleccionar `primaryColor` en formato `#RRGGBB`.

## Gates

1. CU-17 focal rendering/validator.
2. `generatedAngularAcceptance` Simple + Auth con production build.
3. CU-16 Domain Manifest regression.
4. CU-15 OpenAPI/Postman regression.
5. CU-14 CRUD/Auth regression.
6. CU-13 Spring generation regression.
7. full backend.
8. frontend generation tests.
9. Angular ClassForge production build.
10. `git diff --check`.
11. OpenSpec/evidence audit.

El cierre incorpora los recoveries de endurecimiento de CU-17:

- `C6-cu17-002`: permite template literals TypeScript `${...}` sin debilitar el detector FreeMarker fuera de `frontend/**/*.ts`;
- `C6-cu17-003`: alinea el test focal del dashboard con la composición dinámica de `/count`;
- `C6-cu17-004`: elimina opciones `tsconfig` deprecadas bajo TypeScript 6 y limpia los nullish-coalescing redundantes de filtros.

La evidencia JSON se conserva en `cu17-acceptance.json`.
