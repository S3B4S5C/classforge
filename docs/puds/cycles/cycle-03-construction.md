# Ciclo 3 — Construcción reproducible

**Fase PUDS:** Construcción  
**Estado:** OPEN

## Objetivo
Transformar el modelo UML canónico en artefactos de aplicación reproducibles.

## Scope inicial
- CU-12: IR relacional interna determinista.
- CU-13: generador Spring Boot/JPA.
- CU-14: API CRUD.

CU-15 puede ser el siguiente candidato, pero no se implementa aquí.


## Estado al cierre de CU-13

- CU-12: CERRADO.
- CU-13: CERRADO.
- CU-14: PLANIFICADO / siguiente caso del ciclo.

El Ciclo 3 permanece OPEN. CU-13 queda aceptado con validación estructural del `GeneratedProject`, ZIP determinista y acceptance que compila proyectos generados y carga su contexto Spring sobre H2. La evidencia máquina-legible se conserva en `docs/evidence/cu13/cu13-acceptance.json`.
