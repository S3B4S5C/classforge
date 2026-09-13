# CU-15 — OpenAPI/Postman specification

## Requirement: deterministic API artifacts

Every successful CU-14 system export SHALL include `openapi.yaml` and `postman_collection.json` generated from one canonical API contract.

### Scenario: repeat generation
Given the same canonical project revision and generation options, two generations MUST produce byte-identical OpenAPI and Postman artifacts.

## Requirement: OpenAPI mirrors the generated API

OpenAPI SHALL be version 3.0.3 and SHALL describe all generated CRUD, list, count, filtering, sorting, pagination, relation-ID and identifier operations.

### Scenario: composite identifier
An entity with a composite identifier MUST expose the `/by-id` operations and their identifier query parameters exactly as the generated controller does.

## Requirement: Simple mode has no authentication contract

`SIMPLE_CRUD` SHALL NOT contain Auth endpoints, a bearer security scheme, global security, a `jwt` Postman variable or bearer authorization.

## Requirement: Auth mode is executable from Postman

`AUTH_INFORMATION_SYSTEM` SHALL document public bootstrap/login, protected remaining operations and Bearer JWT.

### Scenario: login
A successful Login request in Postman MUST store `accessToken` in collection variable `jwt`, and protected requests MUST use `Bearer {{jwt}}`.

### Scenario: credential privacy
The selected password field MUST be accepted only where required for writes and MUST NOT be present in response schemas/examples.

## Requirement: no external generation dependency

Producing OpenAPI/Postman SHALL NOT require starting the generated Spring application, querying runtime endpoints or executing an external OpenAPI-to-Postman converter.

## Requirement: CU boundaries

CU-15 SHALL NOT generate `domain-manifest.json`, TypeScript clients or Angular/mobile UI.
