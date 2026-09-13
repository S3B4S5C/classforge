# CU-13 design — Spring Boot/JPA generator

**Status:** PROPOSED  
**PUDS phase:** Construction  
**Cycle:** 3  
**Depends on:** CU-12 CLOSED (`UmlModel -> RelationalModel`)  
**Next use case:** CU-14 — expressive CRUD API and optional authentication

## 1. Purpose

CU-13 turns the canonical UML model into a downloadable, reproducible Spring Boot/JPA project.

The user-facing abstraction remains:

```text
UML
  -> Generate Spring Boot
  -> <artifact>-backend.zip
```

The internal pipeline is intentionally layered:

```text
ProjectDocument @ revision N
        |
        v
     UmlModel
        |
        | CU-12
        v
RelationalModelMapper
        |
        v
 RelationalModel
        |
        | CU-13
        v
SpringGenerationPlanner
        |
        v
SpringGenerationModel
        |
        v
SpringGenerationModelValidator
        |
        v
FreeMarker renderer
        |
        v
 GeneratedProject
 (virtual file tree)
        |
        v
GeneratedProjectValidator
        |
        v
DeterministicZipWriter
        |
        v
<artifact>-backend.zip
```

`UmlModel` remains the semantic source of truth. `RelationalModel` remains the internal derived relational IR from CU-12. `SpringGenerationModel` is a second, target-specific, ephemeral IR used only to make templates declarative and simple. Neither derived IR is persisted.

## 2. Scope boundary

### CU-13 SHALL generate

- a complete Gradle project;
- Java 21 source;
- Spring Boot 4.0.8;
- Spring Web MVC bootstrap;
- Spring Data JPA / Hibernate persistence;
- Jakarta Validation dependency;
- JPA entities;
- Spring Data repositories;
- simple and composite identifiers;
- direct JPA relationships derived from the CU-12 relational storage;
- JOINED inheritance;
- H2 as the default local/demo database;
- a PostgreSQL profile;
- an application bootstrap class;
- a context-load test;
- Gradle Wrapper 9.2.0;
- deterministic ZIP export;
- a minimal frontend export flow.

### CU-13 SHALL NOT generate

- domain CRUD controllers;
- domain services;
- DTOs;
- DTO mappers;
- filtering, pagination, sorting or expressive query endpoints;
- OpenAPI or Postman artifacts;
- Spring Security;
- login endpoints;
- JWT;
- password hashing;
- roles or authorization;
- database migrations;
- SQL DDL files;
- generated identifiers or `@GeneratedValue`;
- code from AI.

Controllers/services/DTO/API belong to CU-14. OpenAPI/Postman belongs to CU-15.

The broader project structure in product documentation represents the final generated backend, not the CU-13-only increment.

## 3. Security/authentication decision

The export experience will later support an optional "Include authentication" profile. That profile is explicitly deferred to CU-14 because it requires HTTP/application semantics in addition to persistence.

CU-14 will ask for:

- credential class;
- username attribute;
- password attribute.

It will then add Spring Security, password encoding, login/JWT and CRUD-safe password handling.

CU-13 MUST NOT expose a fake or unused authentication checkbox and MUST NOT generate Spring Security dependencies or security code. The CU-13 architecture only needs to avoid coupling the renderer so tightly that CU-14 cannot add target-specific generation options later.

## 4. Target platform

Generated projects pin exact versions for reproducibility:

```text
Java                 21
Spring Boot           4.0.8
Gradle Wrapper        9.2.0
Build                 Gradle Groovy DSL
Persistence           Spring Data JPA / Hibernate
Default database      H2
Optional profile      PostgreSQL
Template engine       FreeMarker (inside ClassForge only)
```

No generated project SHALL use a floating `latest`, `4.x`, or equivalent version.

FreeMarker is a ClassForge generator implementation dependency. It is not added to generated projects.

## 5. Backend package boundary

Production generation code lives under:

```text
com.classforge.generation.spring
├── application/
├── model/
├── planning/
├── rendering/
├── archive/
├── validation/
└── web/
```

CU-12 packages under `generation.relational` remain unchanged except for bug fixes proven necessary by a failing CU-13 acceptance case. CU-13 must not move relational semantics into templates.

## 6. Generation request

The product request contains only target-specific export configuration:

```text
SpringBootGenerationRequest
- baseRevision
- artifactName
- basePackage
```

`artifactName` is the Gradle project name and ZIP stem. It uses a strict, path-safe artifact-name contract.

`basePackage` is a strict lowercase Java package. It is never inferred from UML classes.

No database selector is required in CU-13: every generated project includes H2 as the default profile and PostgreSQL as an opt-in runtime profile.

No security configuration is accepted in CU-13.

## 7. Revision and access semantics

Generation is read-only but revision-aware.

- OWNER and EDITOR may export a project they can access.
- NONE may not export it.
- The request includes the workspace `baseRevision`.
- The backend compares it with the current canonical project revision before generation.
- A stale revision fails with the existing revision-conflict semantics.
- Generation never changes project revision.
- Generation never writes to `ProjectDocument`.

The model used for one export is the canonical snapshot resolved at the accepted revision.

## 8. CU-12 boundary

`SpringGenerationPlanner` consumes `RelationalModel`, not `UmlModel` and not `DiagramLayout`.

The orchestration layer performs:

```text
ProjectDocument.umlModel
 -> RelationalModelMapper.map(...)
 -> SpringGenerationPlanner.plan(relationalModel, config)
```

After the CU-12 call, CU-13 must not reconstruct relational rules from UML multiplicities.

### 8.1 Explicit first-attribute identifier fallback

Strict export remains the default. If strict CU-12 preflight fails only because one or more root classes have no explicit identifier, CU-13 may offer one user-confirmed retry only when every affected class has at least one attribute. The retry works on an ephemeral copy of `UmlModel`: for each affected root class, its first attribute in canonical list order is copied with `identifier=true` and `nullable=false`. Subclasses, classes that already define identifiers, layout and relationships are not changed.

This is an export profile, not a CU-12 rule and not a persisted UML edit. The canonical project snapshot remains byte-for-byte unchanged and revision is not incremented. The prepared copy is passed back through the existing `RelationalModelMapper`; CU-13 does not build tables/FKs itself. If strict mapping reports any diagnostic other than `CLASS_IDENTIFIER_REQUIRED`, if an affected class has no attributes, or if the confirmed retry still fails CU-12, generation remains fail-closed.

The HTTP contract exposes the recoverable state as `PRIMARY_KEY_FALLBACK_CONFIRMATION_REQUIRED` plus class/attribute suggestions. The frontend explains that the choice affects only the current ZIP and requires an explicit secondary action before resubmitting with the opt-in flag.

## 9. SpringGenerationModel

`SpringGenerationModel` is immutable and non-persisted. It contains only information required to render one target project.

Minimum shape:

```text
SpringGenerationModel
├── generatorSchemaVersion
├── artifactName
├── basePackage
├── applicationClassName
├── entities[]
└── repositories[]

SpringEntityModel
├── className
├── tableName
├── superClassName?
├── inheritanceRoot
├── id
├── scalarFields[]
├── directRelations[]
├── manyToManyRelations[]
├── uniqueConstraints[]
└── indexes[]

SpringRepositoryModel
├── interfaceName
├── entityClassName
└── idType
```

The exact record decomposition may evolve during implementation, but templates SHALL NOT need to inspect raw `RelationalModel` objects or decide ownership/cardinality rules.

## 10. Java naming

Physical SQL names continue to come from CU-12 and are not recalculated.

CU-13 introduces a deterministic Java naming strategy for source identifiers:

- table logical name -> Java entity class name in PascalCase;
- attribute logical name -> Java field name in lowerCamelCase;
- repository -> `<Entity>Repository`;
- composite ID -> `<Entity>Id`;
- application class -> PascalCase artifact stem + `Application`.

Underscores are treated as word boundaries. Existing camel-case boundaries are preserved semantically.

Any collision in generated Java type, field, repository, or path names fails closed. CU-13 never invents numeric suffixes.

## 11. Relational-to-Java type mapping

CU-13 maps the closed CU-12 relational type set exactly:

```text
VARCHAR    -> java.lang.String
INTEGER    -> java.lang.Integer
BIGINT     -> java.lang.Long
DECIMAL    -> java.math.BigDecimal
BOOLEAN    -> java.lang.Boolean
DATE       -> java.time.LocalDate
TIMESTAMP  -> java.time.LocalDateTime
UUID       -> java.util.UUID
```

No `CUSTOM` type exists in `RelationalModel`; CU-12 already fails closed before CU-13.

## 12. Entity rendering rules

Only `RelationalTableOriginType.UML_CLASS` becomes a JPA entity.

A `JOIN_RELATIONSHIP` table is consumed as relationship storage and does not become a domain entity or repository in CU-13.

For a normal entity:

```java
@Entity
@Table(name = "...")
public class EntityName {
    ...
}
```

Scalar `ATTRIBUTE` columns become Java fields with `@Column` preserving physical column name and nullable semantics.

Physical FK columns are not duplicated as scalar fields when represented by an association. Inherited PK columns in a JOINED subclass are not duplicated as Java fields because identity is inherited from the root Java class.

No Lombok is generated.

Generated entities provide a JPA no-arg constructor and ordinary accessors. CU-13 does not define domain-specific `equals/hashCode` for entities.

## 13. Simple identifiers

A one-column entity primary key becomes:

```java
@Id
@Column(name = "...")
private IdType id;
```

CU-13 SHALL NOT add `@GeneratedValue`, UUID generators or sequence strategies because the UML/relational model does not express identifier generation semantics.

## 14. Composite identifiers

A root entity with multiple PK columns uses `@IdClass`.

Example:

```java
@Entity
@IdClass(DetallePedidoId.class)
class DetallePedido {
    @Id
    private UUID pedidoId;

    @Id
    private UUID productoId;
}
```

and:

```java
public class DetallePedidoId implements Serializable {
    ...
}
```

The generated ID class contains exactly the PK fields, a no-arg constructor, accessors, and value-based `equals/hashCode`.

`@EmbeddedId` is not used in CU-13. This keeps UML attributes as normal entity properties and avoids introducing an object structure that did not exist in the source model.

## 15. Direct foreign-key relationships

A CU-12 `RelationalRelation` with `storage=FOREIGN_KEY` is rendered only on the physical FK holder identified by `owningTableName`.

CU-13 does not create a bidirectional inverse collection/property because `UmlRelationship` currently has no role names.

The Java navigation field is deterministic and derived from the referenced entity name. If a deterministic field name collides with another generated field, generation fails closed.

### Many-to-one

A non-unique FK to another entity becomes a unidirectional `@ManyToOne(fetch = LAZY)`.

`optional` and `@JoinColumn(nullable=...)` reflect the CU-12 FK nullability.

Composite FKs use `@JoinColumns` with positional local/reference correspondence from `RelationalForeignKey`.

### One-to-one

A CU-12 FK covered by the corresponding unique constraint becomes a unidirectional `@OneToOne(fetch = LAZY)` on the CU-12 owning table.

Uniqueness is rendered from `RelationalUniqueConstraint`; composite unique FKs are represented at table level rather than by incorrectly marking each component individually unique.

CU-13 does not re-run the 1:1 owner-selection algorithm. CU-12 has already chosen the physical owner.

## 16. Many-to-many relationships

A CU-12 `storage=JOIN_TABLE` relation becomes one unidirectional `@ManyToMany(fetch = LAZY)` with an explicit `@JoinTable`.

No inverse-side `mappedBy` collection is generated because role names are not present in the canonical UML model.

The owning Java side is deterministic:

- ASSOCIATION: `sourceTableName` from the normalized CU-12 relation (lexicographically stable);
- AGGREGATION: `sourceTableName`, which preserves the UML whole/source semantics.

The generated collection property uses a deterministic implementation name based on the target entity, e.g. `libroSet`; it is not presented as a UML role name.

Join columns and inverse join columns come directly from the two `RelationalForeignKey` objects of the join table. Composite referenced PKs are supported.

The join table pair SHALL be unique. The renderer expresses the all-FK uniqueness contract even where the JPA provider manages the concrete physical primary-key DDL for `@ManyToMany` join tables.

## 17. Aggregation and composition

Aggregation uses the same JPA association forms as a normal association and preserves `NO_ACTION` delete behavior.

Composition uses the CU-12 FK direction and `CASCADE` referential action. CU-13 SHALL NOT use `CascadeType.REMOVE` on the child-to-parent `@ManyToOne`, because that can reverse aggregate deletion semantics.

For a direct composition FK, CU-13 uses Hibernate `@OnDelete(action = OnDeleteAction.CASCADE)` on the owning association so schema generation can express database-level delete cascade in the CU-12 direction.

No bidirectional parent collection/orphan-removal policy is invented in CU-13.

## 18. JOINED inheritance

CU-12 `JOINED_INHERITANCE` becomes JPA JOINED inheritance.

The root entity is rendered with:

```java
@Inheritance(strategy = InheritanceType.JOINED)
```

A subclass:

- `extends` its immediate generated parent;
- does not redeclare inherited ID fields;
- maps its physical subclass PK/FK through `@PrimaryKeyJoinColumn` or `@PrimaryKeyJoinColumns`;
- uses the same identifier Java type as the hierarchy root;
- supports multi-level inheritance.

Composite hierarchy identifiers are supported.

CU-13 does not choose another inheritance strategy.

## 19. Constraints and indexes

CU-12 constraints remain authoritative.

`RelationalUniqueConstraint` is rendered using JPA table-level `@UniqueConstraint` whenever it is not safely expressible as a single-column property flag.

`RelationalIndex` is rendered through table-level `@Index`.

CU-13 does not create additional domain indexes or uniqueness constraints except those needed to faithfully map the selected JPA representation of an existing CU-12 relationship.

## 20. Repositories

Every generated UML-class entity gets exactly one Spring Data repository:

```java
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {}
```

For a composite root ID:

```text
JpaRepository<DetallePedido, DetallePedidoId>
```

A JOINED subclass repository uses the inherited root ID type.

Join tables do not get repositories.

No custom query methods are generated in CU-13.

## 21. Generated project skeleton

The virtual project contains at least:

```text
build.gradle
settings.gradle
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
.gitignore
README.md
src/main/java/<base-package>/Application.java
src/main/java/<base-package>/entity/*.java
src/main/java/<base-package>/repository/*.java
src/main/resources/application.yml
src/main/resources/application-postgres.yml
src/test/java/<base-package>/ApplicationTests.java
src/test/resources/application.yml
```

Composite ID classes live with their entities unless implementation evidence shows a clearer stable package boundary. The final decision must be documented and deterministic.

CU-13 does not create empty `controller`, `service`, `dto`, or `security` packages.

## 22. Generated Gradle build

`build.gradle` pins Java 21 and Spring Boot 4.0.8 and includes only dependencies justified by CU-13:

- `spring-boot-starter-webmvc`;
- `spring-boot-starter-data-jpa`;
- `spring-boot-starter-validation`;
- H2 runtime;
- PostgreSQL runtime;
- `spring-boot-starter-test` for tests.

It does not include:

- Spring Security;
- JWT libraries;
- springdoc/OpenAPI;
- FreeMarker;
- Lombok.

The generated project uses Maven Central as its dependency repository.

## 23. Database profiles

### Default profile: H2

The generated application SHALL boot without external database setup using H2.

The default configuration uses an application-local H2 database configuration suitable for demo/development and sets `spring.jpa.open-in-view=false`.

### PostgreSQL profile

`application-postgres.yml` uses environment configuration and contains no committed secrets.

At minimum:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

Running with the `postgres` profile activates the PostgreSQL datasource.

CU-13 relies on Hibernate schema management for the generated prototype. Flyway/Liquibase migrations are out of scope.

Tests use isolated H2 and may use `create-drop` independently of the runtime profile.

## 24. Template strategy

Apache FreeMarker is mandatory for text generation.

Templates live under a versioned ClassForge resource tree, conceptually:

```text
generation/spring/
├── project/
├── jpa/
└── test/
```

Templates SHALL receive `SpringGenerationModel`/render view models, not raw UML or relational-domain objects.

Templates SHALL decide formatting only. They SHALL NOT contain algorithms for:

- relationship ownership;
- multiplicity interpretation;
- FK selection;
- inheritance traversal;
- collision resolution;
- identifier strategy.

Complex Java source SHALL NOT be built by ad-hoc string concatenation.

## 25. GeneratedProject virtual file tree

Rendering produces an in-memory `GeneratedProject` before any archive is written.

Conceptual shape:

```text
GeneratedProject
- artifactName
- files[]

GeneratedFile
- path
- content bytes
```

Rules:

- paths are relative POSIX-style paths;
- no absolute paths;
- no `..` path segments;
- no duplicate paths;
- text is UTF-8 with LF line endings;
- file order is lexicographic;
- byte arrays are defensively copied;
- static binary resources such as `gradle-wrapper.jar` are copied byte-for-byte.

Renderers do not write arbitrary server filesystem paths.

## 26. GeneratedProject validation

Before archiving, validation SHALL prove at minimum:

- required project files exist;
- no duplicate or unsafe path exists;
- all expected entity and repository paths are unique;
- no unresolved FreeMarker markers remain in generated text;
- Java package declarations match output paths;
- all referenced generated types exist;
- one application bootstrap class exists;
- all rendered text is UTF-8/LF.

A validation error fails closed: no ZIP is returned.

## 27. Deterministic ZIP

For identical canonical model content and identical generation configuration, CU-13 SHALL produce byte-identical ZIP output in the same generator version.

Determinism rules:

- lexicographically sorted entries;
- a single top-level `<artifactName>/` directory;
- fixed ZIP timestamps (1980-01-01);
- no comments;
- no generated timestamps;
- no random UUIDs;
- no host-dependent absolute paths;
- stable file contents and LF line endings;
- stable entry method/metadata;
- `gradlew` preserved as executable for Unix-compatible extraction.

Apache Commons Compress may be used for deterministic archive metadata and Unix mode support rather than implementing ZIP metadata manually.

A deterministic test compares SHA-256 of two independently generated exports from the same inputs.

## 28. No server-side artifact persistence

Generated projects and ZIPs are ephemeral.

CU-13 SHALL NOT:

- store the ZIP in the database;
- add generated-project entities/repositories;
- store a copy under the project record;
- mutate the project document;
- increment revision.

Temporary directories are allowed only in acceptance tests that compile generated output and must be cleaned by the test harness.

## 29. HTTP export contract

Expose one authenticated export operation, conceptually:

```text
POST /api/projects/{projectId}/generation/spring-boot
```

Request JSON:

```json
{
  "baseRevision": 12,
  "artifactName": "biblioteca",
  "basePackage": "com.example.biblioteca"
}
```

Success:

```text
200 OK
Content-Type: application/zip
Content-Disposition: attachment; filename="biblioteca-backend.zip"
```

The body is the deterministic ZIP bytes.

The exact controller class may follow existing ClassForge web package conventions, but the generation logic must remain outside the controller.

## 30. Generation diagnostics

Generation is fail-closed.

Errors are separated into stable categories:

```text
INVALID_GENERATION_CONFIGURATION
STALE_PROJECT_REVISION
RELATIONAL_MAPPING_REJECTED
SPRING_MODEL_REJECTED
TEMPLATE_RENDER_FAILED
GENERATED_PROJECT_INVALID
ARCHIVE_FAILED
```

Relational mapping diagnostics from CU-12 remain intact and may be translated into an export diagnostic payload when no ZIP can be produced.

No partial ZIP is returned.

Technical stack traces are not exposed to the normal frontend.

## 31. Frontend scope

CU-13 adds only the minimum user-visible export flow.

The workspace exposes a `Generate Spring Boot` action in the existing project action/export area. It opens a compact dialog containing:

- artifact name;
- base package;
- Generate;
- Cancel.

The current project revision is sent as `baseRevision` and is not manually editable.

The dialog does not expose:

- `RelationalModel`;
- tables/FKs/indexes;
- FreeMarker;
- JPA planner details;
- Spring Boot/Gradle version switches;
- authentication/security options.

On success the browser downloads the ZIP.

On a generation-readiness error the UI shows a concise actionable message, preserving detailed diagnostics for development/logging. A stale revision asks the user to retry generation from the current project state.

## 32. Compilation policy

ClassForge SHALL NOT compile the generated backend synchronously on every user export.

Runtime export is:

```text
snapshot
-> map
-> plan
-> validate
-> render
-> validate
-> archive
-> download
```

Compilation is a generator acceptance gate, not a production-request dependency.

This keeps export fast, deterministic and independent from external dependency availability.

## 33. Acceptance strategy

CU-13 has three verification layers.

### Unit/model tests

Verify:

- Java naming;
- planner mappings;
- composite ID planning;
- relationship ownership;
- inheritance;
- configuration validation;
- generated-project path safety;
- deterministic archive behavior.

### Structural render tests

Generate representative projects and assert:

- expected file tree;
- correct annotations/imports;
- no duplicate FK scalar fields;
- correct ID/repository types;
- H2/PostgreSQL configuration;
- no unresolved template markers;
- no CU-14/CU-15/security artifacts.

### Generated-project acceptance

A dedicated Gradle acceptance task materializes representative generated projects in temporary directories and executes their generated Gradle tests.

The fixture set SHALL exercise at least:

1. simple entity with simple UUID ID;
2. composite ID;
3. 1:N association;
4. 1:1 association;
5. N:M association;
6. aggregation;
7. composition CASCADE;
8. JOINED inheritance;
9. multi-level JOINED inheritance;
10. relationship targeting a subclass;
11. composite FK where supported by CU-12.

Acceptance passes only when generated projects compile and their Spring context starts with H2 without manual source changes.

Generated-project acceptance may be a dedicated opt-in Gradle task rather than part of every ordinary backend unit-test invocation, but it is mandatory evidence for closing CU-13.

## 34. Reproducibility acceptance

At least one representative fixture SHALL be independently generated twice and prove:

```text
SHA-256(zip A) == SHA-256(zip B)
```

The same test also proves that no timestamps or random identifiers leak into the archive.

## 35. Non-goals and deferred work

### CU-14

- CRUD API;
- service layer;
- DTOs and mappers;
- filtering/sorting/pagination/count;
- optional authentication profile;
- credential class selection;
- username/password attribute selection;
- username uniqueness derived by security profile;
- password encoding;
- Spring Security;
- login endpoint;
- Bearer JWT;
- protection of business routes;
- password omission from response DTOs.

### CU-15

- OpenAPI contract;
- Postman collection.

### Later

- schema migrations;
- role-based authorization;
- refresh tokens;
- MFA/social login;
- user-defined relationship role names;
- custom/enumerated UML types once canonical UML models them explicitly.

## 36. Architectural invariant

The central invariant for CU-13 is:

```text
UML decides domain semantics.
CU-12 decides relational storage.
CU-13 decides Spring/JPA representation and file rendering.
Templates decide formatting only.
```

If CU-13 discovers that it must reinterpret UML multiplicities or modify CU-12 relational ownership to render code, implementation SHALL stop and the contradiction SHALL be reviewed instead of embedding a second relational mapper in the Spring generator.
