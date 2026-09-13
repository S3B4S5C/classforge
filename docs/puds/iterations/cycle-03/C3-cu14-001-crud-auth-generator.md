# C3-cu14-001 — CRUD expresivo + Sistema de Información con Auth

**Ciclo:** 3 — Construcción
**CU:** CU-14 — Generar API CRUD expresiva
**Estado:** CLOSED
**Fecha:** 13 de septiembre de 2026
**Dependencias:** CU-12 CLOSED, CU-13 CLOSED

## Incremento
Implementa los dos modos aprobados en C3-cu14-000 sobre el generador Spring existente.

### CRUD simple
- DTO request/response por entidad;
- service y controller por entidad;
- CREATE, READ, UPDATE, DELETE, LIST;
- `q`, `filter.<campo>`, sort asc/desc, page/size y count;
- referencias de relaciones por ID;
- IDs simples y compuestos;
- sin Spring Security ni tratamiento especial de campos llamados password.

### Sistema de Información con Auth
- selección estable `authClassId`, `usernameAttributeId`, `passwordAttributeId`;
- ambos atributos deben ser STRING y declarados por la clase elegida;
- username único/no nulo y password no nulo en el target;
- BCrypt en create/update;
- password excluido de responses;
- bootstrap único mientras la tabla Auth esté vacía;
- login JWT; expiración 3600 s; sin refresh;
- API stateless protegida salvo bootstrap/login;
- sin roles en CU-14.

## Gates
1. `SpringApiGenerationPlannerTests`.
2. `SpringCrudRenderingTests`.
3. `SpringBootGenerationControllerTests`.
4. full backend test.
5. frontend build.
6. `springCrudGenerationAcceptance`: export Simple + Auth, build real de ambos y `contextLoads()` sobre H2.

El patch de cierre ejecuta estos gates y revierte automáticamente si cualquiera falla.
