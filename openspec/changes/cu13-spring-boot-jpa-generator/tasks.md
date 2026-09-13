# CU-13 tasks — Spring Boot/JPA generator

## 1. Contract and architecture
- [x] Confirm CU-12 is CLOSED and keep `RelationalModel` unchanged.
- [x] Reconcile CU-13/CU-14/CU-15 scope in current PUDS/product architecture docs before implementation.
- [x] Define generation request/config records (`baseRevision`, `artifactName`, `basePackage`).
- [x] Define stable CU-13 generation diagnostic codes and exception boundary.

## 2. Spring generation IR
- [x] Create immutable `SpringGenerationModel` and render-view records.
- [x] Create deterministic Java naming strategy with collision detection.
- [x] Create `SpringGenerationModelValidator`.
- [x] Add focused naming/model-validator tests.

## 3. Planner
- [x] Implement `SpringGenerationPlanner` consuming `RelationalModel` only.
- [x] Map all closed relational scalar types to Java types.
- [x] Plan simple IDs without inferred generation strategy.
- [x] Plan composite IDs using `@IdClass`.
- [x] Suppress FK physical columns as duplicate scalar entity fields.
- [x] Plan unidirectional 1:N / many-to-one mappings from CU-12 FK ownership.
- [x] Plan unidirectional 1:1 mappings from CU-12 FK + unique constraints.
- [x] Plan composite `@JoinColumns`.
- [x] Plan N:M `@ManyToMany/@JoinTable` with deterministic owner.
- [x] Preserve aggregation `NO_ACTION` semantics.
- [x] Preserve composition delete `CASCADE` without reverse `CascadeType.REMOVE`.
- [x] Plan JOINED inheritance, including composite and multi-level identities.
- [x] Plan table unique constraints and indexes.
- [x] Plan exactly one repository per UML-class entity and no join-table repository.
- [x] Add planner tests for every mapping rule.

## 4. FreeMarker rendering
- [x] Add FreeMarker generator dependency to ClassForge backend.
- [x] Create versioned templates under `src/main/resources/generation/spring/`.
- [x] Render `build.gradle` and `settings.gradle` with pinned Spring Boot 4.0.8 / Java 21.
- [x] Add static Gradle Wrapper 9.2.0 resources/scripts.
- [x] Render application bootstrap.
- [x] Render entities.
- [x] Render `IdClass` types.
- [x] Render repositories.
- [x] Render H2 default configuration.
- [x] Render PostgreSQL environment-based profile.
- [x] Render context-load test.
- [x] Render README and `.gitignore`.
- [x] Prove templates do not contain relational decision logic.

## 5. Virtual generated project
- [x] Implement immutable `GeneratedFile` / `GeneratedProject`.
- [x] Normalize text to UTF-8 LF.
- [x] Reject absolute, traversal and duplicate paths.
- [x] Implement `GeneratedProjectValidator`.
- [x] Validate package declarations against paths.
- [x] Validate generated type references and required skeleton.
- [x] Detect unresolved template markers.
- [x] Add path-safety and structural tests.

## 6. Deterministic archive
- [x] Implement deterministic ZIP writer.
- [x] Sort entries lexicographically under `<artifactName>/`.
- [x] Fix ZIP timestamps and metadata.
- [x] Preserve `gradlew` executable mode.
- [x] Ensure wrapper JAR is copied byte-for-byte.
- [x] Add duplicate-generation SHA-256 equality test.

## 7. Application orchestration and HTTP
- [x] Implement generation application service: snapshot -> CU-12 -> planner -> validator -> renderer -> project validator -> ZIP.
- [x] Reuse project access authority for OWNER/EDITOR export.
- [x] Enforce `baseRevision` before generation.
- [x] Confirm generation is read-only and does not increment revision.
- [x] Add `POST /api/projects/{projectId}/generation/spring-boot`.
- [x] Return `application/zip` and deterministic `<artifactName>-backend.zip` filename.
- [x] Map generation failures to stable HTTP diagnostics with no partial ZIP.
- [x] Keep explicit identifiers as the strict default and offer first-attribute PK fallback only for missing-identifier-only failures where every affected root class has attributes.
- [x] Apply the confirmed fallback only to an ephemeral UML projection and preserve canonical document/revision unchanged.
- [x] Add controller/service integration tests for access, stale revision, success, fallback confirmation and generation rejection.

## 8. Frontend export flow
- [x] Add `Generate Spring Boot` action in the existing project action/export area.
- [x] Add compact dialog for artifact name and base package.
- [x] Send current `baseRevision` automatically.
- [x] Download ZIP on success.
- [x] Keep RelationalModel/JPA/template internals hidden.
- [x] Do not add security/login option in CU-13.
- [x] Show concise actionable errors and stale-revision retry guidance.
- [x] Show recoverable missing-PK warnings with proposed class/first-attribute observations and an explicit continue action.
- [x] Keep non-recoverable UML failures fail-closed with concise observations.
- [x] Add focused frontend tests and full frontend build gate.

## 9. Structural generation tests
- [x] Verify generated skeleton and pinned versions.
- [x] Verify simple entity/repository.
- [x] Verify composite `IdClass` and repository ID type.
- [x] Verify 1:N mapping.
- [x] Verify 1:1 unique mapping.
- [x] Verify composite FK mapping.
- [x] Verify N:M join-table mapping and deterministic owner.
- [x] Verify aggregation behavior.
- [x] Verify composition `@OnDelete(CASCADE)` and absence of reverse remove cascade.
- [x] Verify JOINED inheritance and multi-level JOINED.
- [x] Verify relationship to subclass.
- [x] Verify table unique/index annotations.
- [x] Verify no duplicate FK scalar fields.
- [x] Verify no join-table entity/repository.
- [x] Verify no controller/service/DTO/security/OpenAPI output.

## 10. Generated-project acceptance
- [x] Add dedicated `springGenerationAcceptance` Gradle task.
- [x] Generate representative projects from canonical `UmlModel` through CU-12 and CU-13.
- [x] Materialize each fixture only in isolated temporary directories.
- [x] Execute generated Gradle tests/build.
- [x] Prove Spring context loads against H2.
- [x] Cover simple ID.
- [x] Cover composite ID.
- [x] Cover 1:N.
- [x] Cover 1:1.
- [x] Cover N:M.
- [x] Cover aggregation.
- [x] Cover composition.
- [x] Cover JOINED inheritance.
- [x] Cover multi-level JOINED.
- [x] Cover relationship to subclass.
- [x] Cover composite FK.
- [x] Clean temporary outputs.

## 11. Regression and static gates
- [x] Run CU-12 relational tests unchanged and GREEN.
- [x] Run focused CU-13 backend tests GREEN.
- [x] Run generated-project acceptance GREEN.
- [x] Run project access/revision regression GREEN.
- [x] Run full backend tests GREEN.
- [x] Run focused frontend tests GREEN.
- [x] Run full frontend tests/build GREEN.
- [x] Prove no generated artifacts were added to persistence.
- [x] Prove no Spring Security/JWT/OpenAPI generation code was introduced.
- [x] `git diff --check` clean apart from repository line-ending warnings.

## 12. Documentation and closure
- [x] Create `docs/architecture/spring-boot-generation.md` as CU-13 source of truth.
- [x] Create Cycle 3 CU-13 iteration evidence document.
- [x] Update `docs/product/product.md` to make CU-13/CU-14/CU-15 boundaries explicit.
- [x] Update `docs/puds/use-cases.md` from CU-13 PLANIFICADO -> EN PROGRESO -> CERRADO only after all gates.
- [x] Update `docs/puds/current-status.md` only after acceptance evidence exists.
- [x] Update Cycle 3 status/evidence without closing Cycle 3 while CU-14 remains pending.
- [x] Record deterministic ZIP SHA-256 acceptance evidence.
- [x] Leave CU-14 as the next use case and carry its two explicit modes into the design input: simple CRUD or authenticated information system with authentication table/class + username/password attribute selection.
