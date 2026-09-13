# CU-15 implementation tasks

- [x] Add immutable canonical Spring API contract derived from `SpringApiGenerationPlan`.
- [x] Add deterministic OpenAPI 3.0.3 renderer.
- [x] Model CRUD/list/count/filter/sort/page and simple/composite IDs.
- [x] Model stable request/response/error schemas.
- [x] Add Auth bearer scheme, public bootstrap/login and write-only password semantics.
- [x] Add deterministic Postman Collection v2.1 renderer.
- [x] Add `baseUrl` and Auth `jwt` collection variables.
- [x] Add bootstrap/login token-capture scripts.
- [x] Ensure Simple mode contains no Auth/security artifacts.
- [x] Add `openapi.yaml` and `postman_collection.json` to `GeneratedProject`.
- [x] Extend generated-project validation to parse and cross-check both artifacts.
- [x] Add focal unit tests for contract/renderers.
- [x] Add `springApiArtifactsAcceptance` for Simple + Auth.
- [x] Re-run CU-13 and CU-14 generated-project acceptance.
- [x] Run full backend, frontend generation tests, Angular build and static audits.
- [x] Write CU-15 acceptance evidence and close Cycle 4 only after all gates are green.
