# C3-cu12-001 — Modelo relacional interno

**Estado:** VALIDADO / CERRADO

## Objetivo
Transformar `UmlModel` en `RelationalModel` determinista para CU-13.

## Decisiones
- IR efímera en `generation.relational`; no UI, API, endpoint ni persistencia.
- Nombres snake_case singulares; tipos escalares cerrados; CUSTOM/enums diferidos.
- PK compuestas, FK/unique/index, 1:1/1:N/N:M, aggregation, composition CASCADE y JOINED inheritance.
- Colisiones, herencia múltiple, reflexivas y multiplicidades no soportadas fallan cerradas.

## Acceptance
La evidencia final de hardening cubre referencias UML desconocidas fail-closed, inversión real 1:1, FK compuesta, N:M estructural, colisión FK, JOINED multinivel y ciclo defensivo, permutaciones deterministas, inmutabilidad y todas las invariantes del validador. Los gates focales, generation, regresión de proyecto, compilación limpia y backend completo terminaron GREEN. CU-13 recibirá `RelationalModelMapper.map(umlModel)`.
