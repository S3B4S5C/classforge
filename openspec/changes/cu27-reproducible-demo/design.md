# CU-27 Reproducible Final Demo — Design

## Goal

Provide a deterministic transition scenario that exercises the completed ClassForge pipeline without coupling normal development data to exam/demo state.

## Design

- Spring profile `demo` owns an isolated H2 database.
- `DemoScenarioSeeder` creates fixed OWNER/EDITOR identities and one fixed project.
- The project document is imported from the canonical XMI 2.1 fixture, so XMI and demo semantics share one source.
- Runtime AI services remain external/local and are inspected through the existing Assistant health endpoint.
- Enterprise Architect validation uses its Automation Interface only in an explicit transition smoke script and never in unit/CI tests.
- Demo scripts are repo-relative, preserve the normal ClassForge database, and never launch or terminate Spring/Angular; reset only prepares data while server lifecycle remains manual.
