# CU-14 design — expressive CRUD + Auth

**Status:** IMPLEMENTED / CLOSED
**PUDS phase:** Construction
**Cycle:** 3
**Depends on:** CU-12, CU-13 CLOSED

CU-14 adds an ephemeral `SpringApiGenerationPlan` between the Spring generation model and rendering. `SIMPLE_CRUD` emits DTO/service/controller/common API support. `AUTH_INFORMATION_SYSTEM` additionally validates stable UML IDs for auth class/username/password and emits BCrypt/JWT/Spring Security code.

Auth policy: no public registration; one-time `/api/auth/bootstrap` only while the Auth table is empty; public `/api/auth/login`; all remaining requests authenticated; JWT 3600 s; no refresh and no roles in CU-14. Password is write-only and hashed.
