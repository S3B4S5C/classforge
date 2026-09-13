# CU-18 Flutter mobile generation — design

CU-18 renders a standalone Flutter application from the canonical Domain Manifest/API plan. It does not wrap CU-17 Angular with Capacitor.

Each domain entity gets specific model/API/list/detail/form Dart files. Shared infrastructure is limited to HTTP, theme, auth/session and relationship pickers.

Android is the formally accepted platform. Auth uses secure storage for JWT. The primary color is reused from CU-17.
