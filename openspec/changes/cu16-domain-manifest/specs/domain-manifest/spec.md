# CU-16 — Domain Manifest specification

## Requirement: every API export contains one semantic manifest

Every successful CU-14 API export SHALL include root-level `domain-manifest.json` with `schemaVersion` equal to `1.0`.

## Requirement: manifest is derived from canonical generation state

The manifest SHALL be derived from ClassForge generation models/plans and SHALL NOT be reconstructed by parsing generated OpenAPI/Postman files.

### Scenario: stable source identity
Entity, attribute and relation entries MUST preserve their source ClassForge UUID whenever the underlying canonical model provides one.

## Requirement: entities expose semantic UI/assistant metadata

Every API entity SHALL expose logical/code names, table, endpoint, identifier structure, attributes, relations, inheritance, capabilities and applicable operationIds.

### Scenario: linguistic metadata
`displayName` SHALL default to the logical UML name and `aliases` SHALL default to an empty list. CU-16 SHALL NOT infer plurals or aliases heuristically.

## Requirement: attribute capabilities match the generated CRUD contract

Attribute read/write/search/filter/sort/validation metadata SHALL describe behavior that CU-14 actually generates.

### Scenario: immutable identifier
Identifier fields MUST be marked immutable for update semantics.

### Scenario: credential privacy
The Auth password attribute MUST be sensitive and write-only, MUST NOT be readable/searchable/filterable/sortable, MUST be required for create/bootstrap and optional for update.

## Requirement: relation semantics remain traceable

Relations SHALL preserve source relationship UUID/UML type when available, target entity identity, API relation kind, optionality and request-field binding.

Inheritance SHALL be represented separately and SHALL NOT be inferred from flattened response fields.

## Requirement: operation binding is identical to CU-15

Every manifest operation SHALL use the exact CU-15 `operationId`, HTTP method and path. The set of manifest operationIds MUST equal the set in `SpringApiContract`, OpenAPI and Postman.

## Requirement: generation modes are explicit

`SIMPLE_CRUD` SHALL have authentication disabled. `AUTH_INFORMATION_SYSTEM` SHALL identify the credential entity and selected username/password attributes and SHALL describe Bearer JWT, `jwt`, 3600-second expiry, bootstrap and login operationIds.

## Requirement: deterministic artifact

Equivalent canonical input and options MUST generate byte-identical `domain-manifest.json`.

## Requirement: CU boundaries

CU-16 SHALL NOT generate Angular UI, mobile packaging, assistant execution logic, roles/refresh tokens, LLM aliases or new business rules.
