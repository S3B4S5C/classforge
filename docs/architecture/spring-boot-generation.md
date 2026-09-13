# Generacion Spring Boot/JPA

## Limite implementado

```text
UmlModel
  -> RelationalModelMapper
  -> RelationalModel
  -> SpringGenerationPlanner
  -> SpringGenerationModel
  -> SpringProjectRenderer
  -> FreeMarker + static resources
  -> GeneratedProject
  -> GeneratedProjectValidator
  -> DeterministicZipWriter
  -> SpringBootGenerationArtifact
  -> HTTP application/zip
  -> Frontend export dialog
  -> Browser ZIP download
```

CU-12 conserva la autoridad sobre almacenamiento relacional. El planner consume exclusivamente `RelationalModel` y `SpringGenerationConfig(artifactName, basePackage)`, y produce una IR inmutable preparada para renderizar. El renderer consume sólo esa IR y devuelve un proyecto virtual, inmutable y no persistido.

La IR conserva nombres SQL fisicos, tipos Java cerrados, IDs simple/compuesto, campos escalares, FK directas, N:M, constraints, indexes, metadata de agregacion/composicion, herencia JOINED y tipos de repositorio. Las colisiones de Java y las entradas inconsistentes fallan cerradas mediante diagnosticos estables.

Invariantes: cada entidad tiene exactamente un repositorio; una FK JOINED debe mapear exactamente PK child a PK parent inmediata; una FK directa se resuelve por `sourceRelationshipId`; y el planning es determinista aunque las listas fuente lleguen permutadas. Las templates reciben esta IR render-ready y no vuelven a consultar la IR relacional.

## Orquestacion y export

La generación carga una sola entidad de proyecto y toma documento/revisión del mismo snapshot. Requiere igualdad exacta de `baseRevision`, es read-only y produce un ZIP determinista íntegramente en memoria; la revisión sólo vive como metadata exterior del artifact.

El adaptador HTTP reutiliza `CurrentUser` y `ProjectAccessService.requireEdit` antes de generar; OWNER y EDITOR pueden exportar y NONE conserva la política 404 existente.

La UX frontend abre un dialogo compacto con `artifactName` y `basePackage`, toma `baseRevision` automaticamente de la revision sincronizada y descarga el `Blob` usando el filename de `Content-Disposition`. No expone `RelationalModel`, tablas/FKs, templates, switches de version ni opciones de autenticacion.

La exportacion mantiene modo estricto por defecto: CU-12 sigue exigiendo identificadores explicitos. Si el unico bloqueo relacional es `CLASS_IDENTIFIER_REQUIRED` y cada clase raiz afectada tiene al menos un atributo, el backend responde con `PRIMARY_KEY_FALLBACK_CONFIRMATION_REQUIRED` y enumera la clase/primer atributo propuesto. Solo tras confirmacion explicita, CU-13 crea una proyeccion UML efimera para esa exportacion, marca el primer atributo de cada clase raiz afectada como identifier no-null y vuelve a ejecutar CU-12. `ProjectDocument`, `UmlModel` canonico y revision no se modifican. Si falta cualquier atributo o existe cualquier otro diagnostico relacional, no se ofrece fallback y la generacion falla cerrada.

C3-cu13-006 cierra `GeneratedProjectValidator`: exige skeleton, package/path agreement, referencias internas de tipos y ausencia de marcadores FreeMarker sin resolver antes de archivar. La compilación real permanece fuera del request productivo: el acceptance dedicado extrae ZIPs en temporales y ejecuta el Gradle Wrapper generado.

## Build reproducible del proyecto generado

El `build.gradle` generado importa explicitamente el BOM `org.springframework.boot:spring-boot-dependencies:<springBootVersion>` mediante `platform(...)`. El plugin de Spring Boot no se usa como sustituto de dependency management; starters, H2 y PostgreSQL reciben sus versiones desde el BOM fijado. Esto evita dependencias sin version resoluble y conserva Spring Boot 4.0.8 como target reproducible.

El proyecto generado sigue apuntando a Java 21 mediante Gradle Toolchains. La maquina puede tener otra version de Java como default, pero Gradle debe detectar un JDK 21; el README generado documenta `javaToolchains` como verificacion.

## Frontera de errores HTTP

El adaptador HTTP clasifica fallos conocidos sin devolver archivos parciales:

```text
RelationalMappingException
SpringGenerationException
GeneratedProjectException
SpringArchiveException
        |
        v
categoria estable CU-13
        |
        v
JSON de error
```

Las categorias son `INVALID_GENERATION_CONFIGURATION`, `STALE_PROJECT_REVISION`, `PRIMARY_KEY_FALLBACK_CONFIRMATION_REQUIRED`, `RELATIONAL_MAPPING_REJECTED`, `SPRING_MODEL_REJECTED`, `TEMPLATE_RENDER_FAILED`, `GENERATED_PROJECT_INVALID` y `ARCHIVE_FAILED`. Los diagnosticos de CU-12 se preservan para rechazos relacionales; los fallos internos de template/proyecto generado/archive se sanitizan y no exponen causas tecnicas. Unicamente el happy path devuelve el ZIP.


## Acceptance final CU-13

El task `springGenerationAcceptance` parte de fixtures canónicos `UmlModel`, atraviesa CU-12 y todo CU-13, produce el ZIP que recibe el usuario, lo extrae únicamente bajo `@TempDir` y ejecuta `clean build` mediante el wrapper incluido. No existe `ProcessBuilder`, materialización temporal ni compilación anidada en production code.

La matriz final agrupa tres proyectos representativos:

- `relations`: simple ID, 1:N, 1:1, N:M, aggregation y composition;
- `composite`: composite `@IdClass` y composite FK;
- `inheritance`: JOINED, multi-level JOINED y relación hacia subclass.

Cada proyecto debe compilar sin edición manual, producir resultados JUnit y aprobar `ApplicationTests.contextLoads()` usando `src/test/resources/application.yml` con H2. La fixture `relations` se genera dos veces y exige ZIP bytes idénticos y el mismo SHA-256. El reporte se conserva en `docs/evidence/cu13/cu13-acceptance.json`.

El acceptance también fijó una corrección de rendering: `@PrimaryKeyJoinColumn(s)` pertenece a la declaración de la subclass JOINED, no al cuerpo de la clase.
