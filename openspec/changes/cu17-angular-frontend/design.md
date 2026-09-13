# CU-17 Angular frontend generation — design

CU-17 renders a standalone Angular SPA from the canonical `DomainManifestPlan` and API contract already produced by CU-14..16.

## Chosen architecture

- Specific generated components per entity.
- Shared infrastructure only for HTTP pagination/reference loading and Auth.
- Dashboard uses canonical count endpoints.
- Native form controls are selected from semantic types.
- to-one relations use one selector; many-to-many relations use a multiple selector.
- Composite IDs are handled using canonical `/by-id` query parameters.
- Auth mode adds login/bootstrap/JWT interceptor/route guard/logout.
- Simple mode contains no Auth files.
- Primary UI color is an explicit export option (`#RRGGBB`).

CU-18 Capacitor and CU-19..23 assistant/voice remain out of scope.
