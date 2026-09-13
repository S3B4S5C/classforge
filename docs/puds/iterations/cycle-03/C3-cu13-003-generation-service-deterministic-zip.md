# C3-cu13-003 - Generation service and deterministic ZIP

**Estado:** VALIDADO / CERRADO

## Objetivo

Completar el pipeline interno `Project snapshot @ revision -> UmlModel -> RelationalModel -> SpringGenerationModel -> GeneratedProject -> ZIP` sin HTTP ni persistencia.

## Evidencia

`SpringBootGenerationService` realiza una sola lectura de `ProjectEntity`, la convierte una vez a `Project`, y toma documento y revisión del mismo snapshot. Exige `baseRevision == currentRevision`; revisiones stale y futuras fallan con `ProjectRevisionConflictException`. Es `@Transactional(readOnly = true)` y no guarda, publica eventos ni incrementa revisión.

El artifact contiene `<artifactName>-backend.zip`, `application/zip`, la revisión exterior y bytes defensivos. El ZIP permanece en memoria, usa root `<artifactName>/`, UTF-8, entradas lexicográficas `STORED`, CRC/tamaños explícitos y timestamp fijo, sin comentarios ni entradas de directorio. Commons Compress fija metadata Unix: `gradlew` es `0755` y los demás archivos son `0644`. La revisión no se incorpora al contenido generado ni al ZIP. Tests prueban bytes y SHA-256 iguales para entradas permutadas y para el mismo UML/config en revisiones 7 y 8; sólo cambia la metadata exterior del artifact. UML inválido falla cerrado sin artifact parcial.

Gates GREEN: focal archive/artifact/service, regresión Spring, regresión CU-12, generation package, clean compile y full backend.

## Fuera de alcance

HTTP, autorización, frontend, compilación/arranque del proyecto generado, Security, CRUD y OpenAPI.
