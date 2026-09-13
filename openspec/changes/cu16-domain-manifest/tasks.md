# CU-16 implementation tasks

- [x] Add immutable Domain Manifest schema v1 model/plan.
- [x] Plan manifest from `SpringGenerationModel` + `SpringApiGenerationPlan` + `SpringApiContract`.
- [x] Preserve source class/attribute/relationship UUIDs.
- [x] Model simple/composite identifiers and inheritance explicitly.
- [x] Model scalar field semantic types and CRUD/query capabilities.
- [x] Model direct and many-to-many relations with stable target references.
- [x] Reuse CU-15 operationIds/methods/paths without inventing operations.
- [x] Model Simple authentication as disabled.
- [x] Model Auth credential entity, username/password UUIDs, JWT and bootstrap/login.
- [x] Enforce password sensitive/write-only semantics.
- [x] Render deterministic root `domain-manifest.json` schema v1.
- [x] Extend generated-project validator for parseability, referential integrity and operation parity.
- [x] Add focal planner/renderer/validator tests.
- [x] Add `domainManifestAcceptance` fixtures for Simple, composite ID, Auth and JOINED inheritance.
- [x] Verify byte-identical regeneration / SHA determinism.
- [x] Re-run CU-13, CU-14 and CU-15 acceptance gates.
- [x] Run full backend, frontend generation tests, Angular build and static audits.
- [x] Write CU-16 acceptance evidence and close Cycle 5 only after all gates are green.
