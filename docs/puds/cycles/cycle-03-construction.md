# Ciclo 3 — Construcción reproducible

**Fase PUDS:** Construcción
**Estado:** CERRADO
**Cierre:** 13 de septiembre de 2026

## Objetivo
Transformar el modelo UML canónico en artefactos backend reproducibles y utilizables como sistema de información.

## Scope cerrado
- CU-12: IR relacional interna determinista — CERRADO.
- CU-13: generador/export Spring Boot/JPA — CERRADO.
- CU-14: API CRUD expresiva con dos modos: CRUD simple o Sistema de Información con Auth — CERRADO.

CU-15 (OpenAPI/Postman) quedó fuera de este ciclo. El 13 de septiembre de 2026 se abrió el Ciclo 4 para fijarlo e implementarlo.

## Resultado

```text
ProjectDocument @ revision N
  -> UmlModel
  -> RelationalModel                 CU-12
  -> SpringGenerationModel          CU-13
  -> SpringApiGenerationPlan        CU-14 (efímero)
  -> GeneratedProject
  -> validated deterministic ZIP
```

La configuración de CU-14 no modifica el UML, el modelo relacional ni la revisión. La UX ofrece dos modos mutuamente excluyentes:

```text
Generar API / sistema
  ├─ CRUD simple
  │    └─ DTOs + services + controllers + query/list/count/relations
  └─ Sistema de Información con Auth
       ├─ entidad de autenticación
       ├─ atributo STRING usuario/login
       ├─ atributo STRING contraseña
       └─ BCrypt + bootstrap + login + JWT + API protegida
```

La selección Auth viaja por UUID del origen UML. El password nunca aparece en DTOs de respuesta ni se persiste en claro. El bootstrap público sólo funciona con la tabla Auth vacía; después no existe registro público. JWT expira a 3600 s y CU-14 no implementa refresh ni roles.

## Evidencia
- CU-12: `../../evidence/cu12/`.
- CU-13: `../../evidence/cu13/`.
- CU-14: `../../evidence/cu14/cu14-closure-report.md`.
- Gate ejecutable CU-14: `backend/gradlew.bat springCrudGenerationAcceptance`.

El acceptance CU-14 genera un proyecto Simple y uno Auth, ejecuta el Gradle Wrapper de cada export y exige build + `contextLoads()` + H2.
