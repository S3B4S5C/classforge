# C3-cu13-002 - FreeMarker and virtual GeneratedProject

**Estado:** VALIDADO / CERRADO

`SpringGenerationModel` is rendered through classpath FreeMarker templates into a deterministic, immutable, in-memory `GeneratedProject`, validated before return. `GeneratedFile` protects text/binary bytes defensively; the validator enforces relative POSIX paths, duplicate rejection, UTF-8 and LF text.

The virtual project contains Gradle Wrapper 9.2.0, Java 21/Spring Boot 4.0.8 build support, Application, H2 default and PostgreSQL profile, README and gitignore. Structural tests validate entities, simple IDs, composite `@IdClass`, repositories, ManyToOne/OneToOne and composite joins, aggregation, composition with `@OnDelete(action = OnDeleteAction.CASCADE)`, N:M including composite joins, JOINED inheritance including composite keys, unique constraints, indexes and deterministic imports. The renderer is virtual-only: it does not write the filesystem.

Focused rendering/contracts, planner/validator regression, generation-package, clean compile and full-backend gates are GREEN. ZIP, export service/revision guard, HTTP, frontend, CRUD, Security, OpenAPI and generated-project compilation acceptance remain out of scope.
