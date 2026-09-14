# Ciclo 10 — CU-27 Demo reproducible final

**Fase PUDS:** Transición
**Estado:** CERRADO al superar los gates CU-27.

## Objetivo

Convertir el conjunto de capacidades cerradas en una demostración repetible sobre el dominio Veterinaria, con estado inicial determinista, dos actores reales, scripts de arranque/reset/smoke y evidencia final.

## Decisiones

- perfil Spring `demo` aislado de la base de desarrollo;
- UUIDs y credenciales demo estables;
- fixture XMI 2.1 canónico compartido por seed y ejemplo;
- OWNER + EDITOR para colaboración;
- `Usuario.username/password` incluido para generación Auth;
- IA local no se duplica: se reutilizan 8092/8093/8094;
- Enterprise Architect real se prueba mediante Automation COM contra un repositorio desechable;
- los tests deterministas no dependen de que los runtimes LLM/STT/Vision estén levantados.

## Gates

- `demoScenarioAcceptance`;
- CU-10/CU-11 XMI regression;
- CU-19 generated Assistant regression;
- CU-18 Flutter regression;
- CU-17 Angular regression;
- CU-16 Domain Manifest;
- CU-15 OpenAPI/Postman;
- CU-14 CRUD/Auth;
- CU-13 Spring generation;
- full backend;
- frontend generation tests + production build;
- `git diff --check`;
- smoke EA real disponible mediante `scripts/demo-ea-smoke.ps1`.

## Salida

CU-27 cierra el último caso funcional planificado. CU-26 permanece descartado/fuera de alcance. A partir de este punto el trabajo es de presentación, documentación académica, diagramas y ejecución del guion de transición.
