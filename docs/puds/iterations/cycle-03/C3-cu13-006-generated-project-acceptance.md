# C3-cu13-006 — GeneratedProject hardening + generated-project acceptance

**Estado:** VALIDADO / CERRADO
**CU:** CU-13 — Generar backend Spring Boot/JPA
**Fase:** Construcción

## Objetivo

Cerrar CU-13 demostrando que el artefacto ZIP realmente entregado al usuario es estructuralmente válido, reproducible y compilable sin edición manual.

## Hardening de GeneratedProject

`GeneratedProjectValidator` ahora falla cerrado ante:

- skeleton obligatorio incompleto;
- más o menos de un bootstrap `@SpringBootApplication`;
- ausencia del context-load test;
- package/type que no coincide con el path Java;
- imports o referencias internas a tipos generados inexistentes;
- marcadores/directivas FreeMarker no resueltos.

Se preservan las validaciones previas de paths relativos POSIX, traversal, duplicados case-insensitive, UTF-8 y LF. Los placeholders runtime `${DB_URL:...}`, `${DB_USERNAME:...}` y `${DB_PASSWORD:...}` siguen permitidos, igual que las expansiones propias de los scripts Gradle Wrapper.

## Corrección guiada por acceptance

La anotación JPA `@PrimaryKeyJoinColumn` / `@PrimaryKeyJoinColumns` de subclasses JOINED se renderiza a nivel de tipo, antes de la declaración `class`, y no dentro del cuerpo de la entidad.

## Acceptance ejecutable

El task `springGenerationAcceptance` ejecuta exclusivamente tests de acceptance. Parte de `UmlModel` canónicos y atraviesa:

```text
UmlModel
 -> RelationalModelMapper
 -> SpringGenerationPlanner
 -> SpringProjectRenderer
 -> GeneratedProjectValidator
 -> DeterministicZipWriter
 -> ZIP real
 -> extracción @TempDir
 -> wrapper generado clean build
 -> ApplicationTests.contextLoads() / H2
```

La compilación anidada existe solo en test code. Production export no escribe al filesystem ni invoca Gradle.

## Matriz

- `relations`: simple ID, 1:N, 1:1, N:M, aggregation, composition.
- `composite`: composite ID con `@IdClass` y composite FK.
- `inheritance`: JOINED, multi-level JOINED y relación hacia subclass.

## Reproducibilidad

La fixture `relations` se genera independientemente dos veces. El acceptance exige bytes ZIP idénticos y SHA-256 idéntico. El valor aceptado y los resultados por fixture se registran en `docs/evidence/cu13/cu13-acceptance.json`.

## Toolchain

El backend generado conserva Java 21, Spring Boot 4.0.8 y Gradle Wrapper 9.2.0. `springGenerationAcceptance` admite `-PgeneratedJavaHome=<JDK21>` para máquinas cuyo Java principal sea otra versión.

## Gates de cierre

El parche de cierre solo reconcilia PUDS/OpenSpec después de:

1. validator/render/archive focal GREEN;
2. CU-12 regression GREEN;
3. CU-13 generation regression GREEN;
4. access/revision regression GREEN;
5. full backend GREEN;
6. `springGenerationAcceptance` GREEN;
7. frontend generation tests GREEN;
8. Angular build GREEN;
9. static scope audits;
10. `git diff --check`.

## Hardening posterior del harness

Después del PASS final se endureció únicamente el propio acceptance, sin cambiar el generador productivo:

- `contextLoads` / `contextLoads()` sólo cuenta como PASS si el testcase no está marcado `skipped`;
- el reporte configurado se elimina al comenzar la ejecución para impedir que un fallo temprano deje un JSON `PASS` de una corrida anterior;
- una divergencia de bytes/SHA se incorpora al agregado de fallos y se refleja como `equalZipBytes=false` si el reporte llega a escribirse.

Este hardening elimina falsos positivos y evidencia obsoleta; no amplía el alcance funcional de CU-13.

## Out of scope preservado

CU-13 no genera controller/service/DTO, Spring Security/JWT, OpenAPI/Postman ni compila durante la petición de export. Esos límites permanecen para CU-14/CU-15.

## Resultado

CU-13 queda CERRADO. Cycle 3 permanece OPEN y el siguiente caso es CU-14 — API CRUD expresiva con modos CRUD simple / Sistema de Información con Auth.
