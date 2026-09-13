# CU-16 design — semantic Domain Manifest

**Status:** SCOPE FIXED / READY FOR IMPLEMENTATION
**PUDS phase:** Construction
**Cycle:** 5
**Depends on:** CU-12, CU-13, CU-14, CU-15 CLOSED

CU-16 generates deterministic `domain-manifest.json` beside the already generated Spring project, OpenAPI and Postman artifacts.

The manifest is planned directly from `SpringGenerationModel`, `SpringApiGenerationPlan` and `SpringApiContract`. It SHALL preserve stable ClassForge source IDs and semantic relation/inheritance metadata while reusing CU-15 operationIds as the HTTP binding.

It is an immutable generated projection, not a persisted or manually edited source of truth.

CU-17 Angular generation and CU-19..23 assistant execution are consumers and remain out of scope.
