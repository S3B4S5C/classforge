# CU-27 — Closure report

CU-27 convierte el dominio Veterinaria en una demo final reproducible y aislada de la base de desarrollo.

## Estado inicial

- OWNER `demo@classforge.local`;
- EDITOR `colaborador@classforge.local`;
- contraseña demo `classforge-demo`;
- proyecto `Veterinaria CU-27` con UUID fijo;
- seis clases, cinco relaciones y revisión inicial 1;
- fixture XMI 2.1 canónico;
- clase `Usuario` apta para demostrar la generación Auth.

## Reproducibilidad

El perfil `demo` usa una base H2 separada y recrea el esquema al arrancar. `scripts/demo-reset.ps1` elimina únicamente `classforge-demo*` y no administra procesos. El backend se arranca manualmente con el perfil `demo`; entonces `DemoScenarioSeeder` reconstruye el proyecto determinista y `scripts/demo-smoke.ps1` lo verifica por HTTP. El estado normal `data/classforge` no se modifica.

## Evidencia automatizada

`demoScenarioAcceptance` valida login de OWNER/EDITOR, roles, UUID del proyecto, clases/relaciones, export XMI y reimport semántico. `scripts/demo-acceptance.ps1` encadena además los acceptance de XMI, generación Spring/API/Manifest/Angular/Flutter/Assistant, full backend y frontend.

## Enterprise Architect

El cierre incluye `scripts/demo-ea-smoke.ps1`. Con una instalación local y un repositorio desechable, el script usa `EA.Repository`, `GetProjectInterface`, `ImportPackageXMI` y `ExportPackageXMI` en XMI 2.1; el archivo reexportado se envía al preview de importación de ClassForge. Este paso requiere la instalación propietaria y por eso no forma parte de los gates deterministas del patch.

## Decisión

CU-27 es el último caso funcional planificado. Tras sus gates, el producto entra en trabajo de presentación/entrega: ejecutar el runbook en el hardware del examen, conservar los reportes de smoke, preparar diagramas/documento final y no introducir nuevas capacidades salvo correcciones.
