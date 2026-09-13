# CU-15 design — deterministic OpenAPI + Postman

**Status:** IMPLEMENTED / CLOSED
**PUDS phase:** Construction
**Cycle:** 4
**Depends on:** CU-12, CU-13, CU-14 CLOSED

CU-15 derives a canonical ephemeral HTTP contract from `SpringApiGenerationPlan` and renders both `openapi.yaml` (OpenAPI 3.0.3) and `postman_collection.json` (Postman Collection v2.1) into every generated Spring project.

No runtime introspection or external converter is part of generation. Simple mode contains no auth metadata. Auth mode contains public bootstrap/login, global bearer JWT security, token capture in Postman, and write-only password semantics.

CU-16 Domain Manifest and CU-17 TypeScript/frontend generation are explicitly out of scope.
