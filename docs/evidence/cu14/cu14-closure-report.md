# CU-14 — Closure report

**Fecha:** 13 de septiembre de 2026
**Estado:** CERRADO al completar satisfactoriamente el patch/gates C3-cu14-001.

## Alcance aceptado

CU-14 extiende el ZIP Spring Boot/JPA de CU-13 con una API CRUD expresiva y dos modos explícitos.

### CRUD simple
Genera DTOs, services y controllers con CREATE/READ/UPDATE/DELETE/LIST, búsqueda `q`, filtros `filter.<campo>`, ordenamiento, paginación, conteo y relaciones representadas por IDs. No incorpora Spring Security/JWT.

### Sistema de Información con Auth
Requiere clase Auth + atributo username + atributo password por UUID. Ambos atributos deben ser STRING. El target fuerza username único/no nulo y password no nulo, hashea con BCrypt, no expone password en responses, proporciona bootstrap inicial sólo con tabla vacía, login JWT de una hora y protege el resto del API. No existe registro público posterior, refresh token ni roles en CU-14.

## Seguridad y autoridad
- OWNER/EDITOR de ClassForge sigue controlando quién puede exportar.
- La autenticación generada es un dominio independiente.
- La configuración del target no persiste ni incrementa revisión.
- Selecciones stale/inconsistentes fallan antes de producir ZIP.

## Acceptance
El task `springCrudGenerationAcceptance` genera dos exports representativos (`SIMPLE_CRUD` y `AUTH_INFORMATION_SYSTEM`), los extrae, ejecuta su Gradle Wrapper y exige `BUILD SUCCESSFUL`, `contextLoads()` no skipped y H2. El patch C3-cu14-001 deja este documento únicamente si todos sus gates finalizan correctamente; ante fallo restaura el snapshot previo.

## Cierre de ciclo
Con CU-12, CU-13 y CU-14 aceptados, el Ciclo 3 queda CERRADO. CU-15 (OpenAPI/Postman) queda como siguiente candidato y requiere un nuevo ciclo.
