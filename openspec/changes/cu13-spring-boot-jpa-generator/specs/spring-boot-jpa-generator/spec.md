# Spring Boot/JPA generator requirements

## Architecture and boundaries

### R1 — Canonical input
CU-13 SHALL obtain its relational input by applying the closed CU-12 `RelationalModelMapper` to the canonical project `UmlModel`.

### R2 — Planner boundary
After CU-12 mapping, Spring planning SHALL consume `RelationalModel` plus explicit generation configuration and SHALL NOT reinterpret UML multiplicities or `DiagramLayout`.

### R3 — Target-specific IR
CU-13 SHALL construct an immutable, non-persisted `SpringGenerationModel` before template rendering.

### R4 — No derived persistence
Neither `RelationalModel`, `SpringGenerationModel`, `GeneratedProject`, nor generated ZIP bytes SHALL be persisted as project state.

### R5 — Read-only generation
Generating a backend SHALL NOT mutate `ProjectDocument` or increment project revision.

### R6 — CU-14 boundary
CU-13 SHALL NOT generate CRUD controllers, business services, DTOs, DTO mappers, expressive API features, authentication, Spring Security, JWT, or password handling.

### R7 — CU-15 boundary
CU-13 SHALL NOT generate OpenAPI or Postman artifacts.

## Export request and authorization

### R8 — Accessible project export
OWNER and EDITOR SHALL be allowed to export an accessible project; a user with no project access SHALL NOT.

### R9 — Revision guard
The export request SHALL include `baseRevision`; if it is stale relative to the canonical project revision, generation SHALL fail before producing an archive.

### R10 — Generation configuration
The export request SHALL contain a path-safe `artifactName` and a valid lowercase Java `basePackage`.

### R11 — No security option in CU-13
The CU-13 export contract SHALL NOT accept or display an authentication/security configuration.

## Target platform

### R12 — Java target
Generated source SHALL target Java 21.

### R13 — Spring Boot target
Generated builds SHALL pin Spring Boot `4.0.8`.

### R14 — Gradle target
Generated projects SHALL include Gradle Wrapper `9.2.0` and SHALL NOT depend on a system-installed Gradle for normal use.

### R15 — Generated dependencies
Generated projects SHALL include Spring Web MVC, Spring Data JPA, Jakarta Validation, H2 runtime, PostgreSQL runtime, and Spring Boot test dependencies.

### R16 — Forbidden generated dependencies
Generated projects SHALL NOT include Spring Security, JWT libraries, springdoc/OpenAPI, FreeMarker, or Lombok in CU-13.

## Java/JPA mapping

### R17 — Entity origin
Every relational table originating from a UML class SHALL map to exactly one generated JPA entity.

### R18 — Join-table origin
A relational table originating from `JOIN_RELATIONSHIP` SHALL NOT become a domain entity or repository in CU-13.

### R19 — SQL naming authority
Generated `@Table`, `@Column`, join-column, unique and index names SHALL use the physical names from `RelationalModel` rather than recomputing relational names.

### R20 — Java type mapping
CU-13 SHALL map `VARCHAR/INTEGER/BIGINT/DECIMAL/BOOLEAN/DATE/TIMESTAMP/UUID` to `String/Integer/Long/BigDecimal/Boolean/LocalDate/LocalDateTime/UUID` respectively.

### R21 — Deterministic Java naming
Entity, field, repository, ID-class and application-class names SHALL be deterministic and collision-checked; collisions SHALL fail closed and SHALL NOT be repaired with numeric suffixes.

### R22 — Simple ID
A one-column primary key SHALL render as one `@Id` field of the mapped Java type.

### R23 — No inferred ID generation
CU-13 SHALL NOT generate `@GeneratedValue`, UUID generators, sequences or identity semantics not present in the source model.

### R24 — Composite ID
A multi-column root primary key SHALL use `@IdClass`, and the generated ID class SHALL implement `Serializable` and value-based equality/hash semantics.

### R25 — No EmbeddedId
CU-13 SHALL NOT use `@EmbeddedId` for UML-class composite identifiers.

### R26 — FK scalar suppression
Columns whose origin is a mapped relationship FK SHALL not be emitted as duplicate scalar Java fields when an association field maps those columns.

### R27 — Unidirectional direct relations
FOREIGN_KEY relationships SHALL be rendered only on the CU-12 owning table; CU-13 SHALL NOT invent a bidirectional inverse side without UML role names.

### R28 — Many-to-one
A non-unique direct FK SHALL map to unidirectional `@ManyToOne(fetch = LAZY)` with optional/nullability derived from CU-12 FK columns.

### R29 — One-to-one
A direct FK whose local column set is uniquely constrained SHALL map to unidirectional `@OneToOne(fetch = LAZY)` on the CU-12 owner.

### R30 — Composite FK
Multi-column direct FKs SHALL use explicit positional `@JoinColumns` matching `RelationalForeignKey.columnNames` to `referencedColumnNames`.

### R31 — Many-to-many
JOIN_TABLE relations SHALL map to one deterministic unidirectional `@ManyToMany(fetch = LAZY)` with an explicit `@JoinTable` using the CU-12 join-table FKs.

### R32 — Many-to-many owner
For an ASSOCIATION join table, the normalized CU-12 `sourceTableName` SHALL own the Java mapping. For AGGREGATION, the semantic CU-12 source/whole SHALL own it.

### R33 — No invented UML role
Generated relationship field names SHALL be deterministic implementation names and SHALL NOT be represented or persisted as UML role names.

### R34 — Aggregation delete behavior
AGGREGATION SHALL retain `NO_ACTION` referential semantics and SHALL NOT introduce removal cascade.

### R35 — Composition cascade direction
COMPOSITION SHALL preserve CU-12 database-level `CASCADE` in the parent-to-child delete direction and SHALL NOT use child-to-parent `CascadeType.REMOVE`.

### R36 — Composition Hibernate mapping
Where a direct FK carries CU-12 `CASCADE`, the generated owning association SHALL use an appropriate Hibernate `@OnDelete(CASCADE)` mapping.

## JOINED inheritance

### R37 — JOINED strategy
CU-12 `JOINED_INHERITANCE` SHALL render as JPA `InheritanceType.JOINED`.

### R38 — Root identity
The hierarchy root SHALL define the JPA identifier exactly once.

### R39 — Subclass source
A generated subclass SHALL extend its immediate generated parent and SHALL NOT redeclare inherited ID fields.

### R40 — Primary-key join
Subclass physical identity SHALL be represented with `@PrimaryKeyJoinColumn` or `@PrimaryKeyJoinColumns` according to the CU-12 inherited FK.

### R41 — Multi-level inheritance
CU-13 SHALL support multi-level JOINED hierarchies emitted by CU-12.

### R42 — Composite inherited ID
JOINED hierarchies with a composite root ID SHALL preserve the composite ID type and multi-column primary-key join.

## Constraints, indexes and repositories

### R43 — Unique constraints
CU-12 `RelationalUniqueConstraint` values SHALL be represented in generated JPA metadata without incorrectly making individual members of a composite constraint independently unique.

### R44 — Indexes
CU-12 `RelationalIndex` values SHALL be represented through JPA table index metadata.

### R45 — Repository per entity
Every generated UML-class entity SHALL receive exactly one Spring Data `JpaRepository`.

### R46 — Repository ID type
Repository ID generic type SHALL be the entity's simple Java ID type or generated `IdClass` type; JOINED subclasses SHALL use the inherited hierarchy ID type.

### R47 — No join-table repository
A pure N:M join table SHALL NOT receive a repository.

### R48 — No generated query methods
CU-13 repositories SHALL contain no inferred domain query methods; expressive querying belongs to CU-14.

## Project configuration

### R49 — Default H2
The generated project SHALL boot and its Spring context SHALL load with H2 without requiring an external database.

### R50 — PostgreSQL profile
The generated project SHALL include a `postgres` profile configured from environment variables and SHALL contain no committed database password.

### R51 — Schema management
CU-13 SHALL rely on Hibernate schema management for the generated prototype and SHALL NOT generate Flyway/Liquibase migrations or SQL DDL files.

### R52 — Open-in-view
Generated configuration SHALL disable `spring.jpa.open-in-view`.

## Rendering

### R53 — FreeMarker
Textual generated artifacts SHALL be rendered with version-controlled Apache FreeMarker templates owned by ClassForge.

### R54 — Dumb templates
Templates SHALL format a prepared Spring generation model and SHALL NOT decide relationship ownership, FK selection, inheritance topology, identifier strategy, or collision recovery.

### R55 — No complex string builder generator
Complex Java classes SHALL NOT be assembled by ad-hoc manual string concatenation as the primary generation mechanism.

## Virtual project and safety

### R56 — Virtual tree first
Rendering SHALL produce an in-memory `GeneratedProject` before archive creation.

### R57 — Safe paths
Generated file paths SHALL be relative, POSIX-style and free from absolute paths and `..` traversal segments.

### R58 — Unique paths
Duplicate generated paths SHALL fail closed.

### R59 — Stable text encoding
All generated text SHALL use UTF-8 and LF line endings.

### R60 — Static binary fidelity
Static binary resources such as the Gradle wrapper JAR SHALL be copied byte-for-byte.

### R61 — Generated-project validation
A validation stage SHALL verify the required skeleton, path safety, generated type references, package/path agreement and absence of unresolved template markers before archiving.

### R62 — Fail closed
Any relational mapping, Spring planning, template rendering, generated-project validation or archive error SHALL produce no downloadable partial ZIP.

## Deterministic archive

### R63 — Stable ZIP bytes
Equal canonical model content plus equal generation configuration under the same generator version SHALL produce byte-identical ZIPs.

### R64 — ZIP ordering
Archive entries SHALL be lexicographically ordered below one `<artifactName>/` root directory.

### R65 — Stable ZIP metadata
ZIP entry timestamps, comments, entry method and permission metadata SHALL be fixed rather than host/time dependent.

### R66 — No generated nondeterminism
Generated source/resources SHALL contain no generation timestamp, random UUID, host path or other nondeterministic value.

### R67 — Unix wrapper mode
The exported `gradlew` SHALL retain executable Unix permissions in archive metadata.

### R68 — Determinism proof
CU-13 acceptance SHALL compare SHA-256 of two independently generated ZIPs from equal inputs and require equality.

## HTTP and frontend

### R69 — Export endpoint
CU-13 SHALL expose one authenticated project-scoped Spring Boot generation endpoint returning `application/zip` on success.

### R70 — Download name
The endpoint SHALL return a deterministic filename `<artifactName>-backend.zip` through `Content-Disposition`.

### R71 — Minimal export UI
The frontend SHALL expose a `Generate Spring Boot` action with a compact dialog for artifact name and base package, and SHALL download the returned ZIP.

### R72 — Hidden internals
The normal export UI SHALL NOT expose RelationalModel, SQL tables/FKs, FreeMarker, JPA planner internals, target-version switches or authentication controls.

### R73 — Concise failures
The frontend SHALL present concise actionable generation errors and SHALL NOT render backend stack traces or long technical diagnostics in normal UI.

## Runtime policy and acceptance

### R74 — No compile during user request
The production export request SHALL NOT invoke a nested Gradle compilation of the generated project.

### R75 — Dedicated generated-project acceptance
CU-13 SHALL provide a dedicated acceptance task that materializes representative generated projects in temporary directories and executes their generated tests/build.

### R76 — Acceptance matrix
Generated-project acceptance SHALL cover simple ID, composite ID, 1:N, 1:1, N:M, aggregation, composition, JOINED inheritance, multi-level JOINED, subclass targets and composite FK behavior supported by CU-12.

### R77 — Runnable without source edits
Every accepted generated fixture SHALL compile and load its Spring context with H2 without manual source modifications.

### R78 — Cleanup
Acceptance temporary project directories SHALL be isolated and cleaned by the test harness.

## Future security seam

### R79 — Authentication deferred
Credential-class selection, username/password selection, username uniqueness, password hashing, Spring Security, login, JWT and route protection SHALL be implemented in CU-14, not CU-13.

### R80 — No relational security mutation
Future authentication configuration SHALL remain an export/application-generation profile and SHALL NOT be persisted into `RelationalModel` or mutate UML semantics.

## Opt-in identifier fallback

### R81 — Strict identifier default
CU-13 SHALL invoke the canonical CU-12 mapping in strict mode by default and SHALL NOT silently infer primary keys.

### R82 — Fallback eligibility
CU-13 MAY offer a first-attribute identifier fallback only when the strict CU-12 failure contains exclusively `CLASS_IDENTIFIER_REQUIRED` diagnostics and every affected root class has at least one UML attribute. Classes without attributes or any concurrent relational diagnostic SHALL keep the normal fail-closed rejection.

### R83 — Explicit non-persistent confirmation
The fallback SHALL require an explicit export request flag. When selected, CU-13 SHALL create an ephemeral generation-only `UmlModel` projection that marks the first attribute, in canonical UML attribute order, of each affected root class as non-null identifier. It SHALL NOT mutate or persist `ProjectDocument`, the canonical `UmlModel`, or project revision.

### R84 — Retry through canonical CU-12
After applying the ephemeral fallback projection, CU-13 SHALL rerun the normal `RelationalModelMapper`; any remaining CU-12 diagnostic SHALL fail closed and SHALL produce no partial ZIP.
