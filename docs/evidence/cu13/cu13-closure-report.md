# CU-13 — Reporte de cierre

**Caso de uso:** CU-13 — Generar backend Spring Boot/JPA
**Fase PUDS:** Construcción
**Ciclo:** 3 — OPEN
**Estado del CU:** CERRADO
**Corte:** 13 de septiembre de 2026

## Decisión

CU-13 queda cerrado. El artefacto exportado por ClassForge se genera desde el `UmlModel` canónico a través de CU-12 y de la IR de generación Spring, se valida antes del archivado, se empaqueta de forma determinista y puede compilar/cargar Spring sobre H2 sin edición manual.

El cierre de CU-13 **no cierra el Ciclo 3**: CU-14 — API CRUD expresiva con modos CRUD simple / Sistema de Información con Auth — permanece como siguiente caso.

## Alcance cerrado

```text
UmlModel
 -> RelationalModelMapper (CU-12)
 -> RelationalModel
 -> SpringGenerationPlanner
 -> SpringGenerationModel
 -> SpringProjectRenderer / FreeMarker
 -> GeneratedProjectValidator
 -> DeterministicZipWriter
 -> ZIP
 -> export HTTP OWNER/EDITOR
 -> descarga Angular
```

El proyecto generado fija Java 21, Spring Boot 4.0.8 y Gradle Wrapper 9.2.0. Incluye bootstrap Spring, entidades JPA, repositories, IDs simples/compuestos, relaciones, herencia JOINED, H2 por defecto, profile PostgreSQL, context-load test, README y wrapper.

El fallback de primary key es opt-in, efímero y sólo se ofrece cuando el único bloqueo es la ausencia de identifiers en clases raíz que sí poseen atributos. No modifica `ProjectDocument`, `UmlModel` ni revisión.

## Gates de cierre

La corrida final quedó GREEN en los diez gates definidos para el cierre:

| Gate | Resultado | Cobertura |
|---|---|---|
| 1–6 | PASS | gates backend previos del recovery final, incluido el full backend |
| 7 | PASS | `springGenerationAcceptance` |
| 8 | PASS | frontend generation focal |
| 9 | PASS | Angular build |
| 10 | PASS | scope/static audits y cierre documental |

La secuencia final de recovery retomó directamente los gates 7–10 porque los gates 1–6 ya estaban GREEN sobre el mismo estado funcional. El documento técnico `C3-cu13-006-generated-project-acceptance.md` conserva el checklist lógico detallado de validator/render/archive, regresiones CU-12/CU-13, acceso/revisión, full backend, acceptance, frontend y auditorías.

## Acceptance generado

La evidencia máquina-legible está en `cu13-acceptance.json`.

| Fixture | Cobertura principal | Build | `contextLoads` | H2 |
|---|---|---|---|---|
| `relations` | simple ID, 1:N, 1:1, N:M, aggregation, composition | PASS | PASS | PASS |
| `composite` | composite ID, composite FK | PASS | PASS | PASS |
| `inheritance` | JOINED, multi-level JOINED, relación hacia subclass | PASS | PASS | PASS |

Determinismo de la fixture `relations`:

```text
equalZipBytes: true
sha256: 7e2a06b03b411fae85be1abf7aebcc53aa7d4927ac45ea836b72c9eabf23bba2
```

## Correcciones de cierre preservadas

- `@PrimaryKeyJoinColumn` / `@PrimaryKeyJoinColumns` se renderiza sobre la declaración de la subclass JOINED.
- `GeneratedProjectValidator` exige skeleton, package/path agreement, tipos internos resolubles y ausencia de marcadores FreeMarker pendientes.
- El acceptance reconoce los nombres JUnit `contextLoads` y `contextLoads()`.
- Los fallos por fixture imprimen la causa concreta y el build generado conserva diagnóstico útil.

## Hardening del harness posterior al PASS

El harness se endureció sin modificar la funcionalidad productiva:

1. un testcase `contextLoads` marcado como `skipped` ya no puede contar como PASS;
2. el reporte configurado se elimina al iniciar para impedir evidencia `PASS` obsoleta si ocurre un fallo temprano;
3. una divergencia de determinismo se agrega a los fallos del acceptance y puede registrarse como `equalZipBytes=false`.

## Fuera de alcance preservado

CU-13 no genera controllers, services, DTOs, autenticación Spring Security/JWT, OpenAPI/Postman, Domain Manifest, frontend generado ni asistente de la aplicación generada. Esas capacidades continúan en CU-14 y casos posteriores.

## Resultado PUDS

```text
Fase: Construcción
Ciclo 3: OPEN
CU-12: CERRADO
CU-13: CERRADO
Next: CU-14
```
