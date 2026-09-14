# C8-cu19-001 — Generated application Assistant

Implementa CU-19 como fusión de los antiguos CU-19..23.

## Entrega

- `GeneratedAssistantRenderer` y metadata grounding desde Domain Manifest;
- gateways generados Whisper/llama.cpp locales;
- native tool routing + tools de datos;
- HTTP executor contra el API generado;
- `/api/assistant/plan`, `/voice`, `/apply`, `/capabilities`;
- preview token opaco con TTL y masking de sensibles;
- chat/voz Angular;
- chat/voz Flutter Android;
- validator fail-closed;
- `generatedAssistantAcceptance` Simple/Auth.

## Gates

1. focal renderer/validator/controller;
2. generated Assistant Simple/Auth + builds backend;
3. CU-18 Flutter regression;
4. CU-17 Angular regression;
5. CU-16 Domain Manifest regression;
6. CU-15 OpenAPI/Postman regression;
7. CU-14 CRUD/Auth regression;
8. CU-13 Spring regression;
9. full backend;
10. frontend generation tests;
11. ClassForge Angular build;
12. `git diff --check`;
13. OpenSpec/evidence audit.


## Hardening post-acceptance

La recertificación manual detectó un problema exclusivo del harness Flutter en Windows multi-drive: `record_android` se resolvía desde un `PUB_CACHE` en `F:` mientras JUnit creaba `@TempDir` en `C:`. Kotlin incremental rechazaba esa combinación con `this and base files have different roots`. `generatedFlutterAcceptance` fija ahora su `java.io.tmpdir` dentro de `backend/build`, de modo que el acceptance permanece en el mismo volumen del repositorio. No se modifica el scaffold Flutter generado ni se desactiva Kotlin incremental.
