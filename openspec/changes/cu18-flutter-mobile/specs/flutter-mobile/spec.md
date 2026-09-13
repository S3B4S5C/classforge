# Flutter mobile generation specification

## Requirement: Flutter output

When Spring API generation is enabled, the ZIP MUST include a standalone Flutter project at `mobile/`.

## Requirement: specific entity pages

Each manifest entity MUST generate specific model, API, list, detail and form Dart files.

## Requirement: modes

SIMPLE_CRUD MUST NOT contain Auth screens/services. AUTH_INFORMATION_SYSTEM MUST include bootstrap/login, Bearer JWT and secure token storage.

## Requirement: relationships

To-one relations MUST use a single selector. Collection relations MUST provide multi-selection. Composite identifiers MUST remain representable.

## Requirement: theme

The Flutter app MUST reuse the same export-time `#RRGGBB` primary color used by the generated Angular app.

## Requirement: Android acceptance

Representative Simple/Auth outputs MUST pass Flutter analyze, tests and debug APK build. No claim of formal iOS/desktop/Web validation is made by CU-18.
