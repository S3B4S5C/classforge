# Ciclo 6 — Construcción — Frontend Angular generado

**Fase:** Construcción
**Estado:** CERRADO
**Caso:** CU-17

## Objetivo

Cerrar la generación web end-to-end sobre los contratos construidos en CU-12..16.

## Riesgos atacados

1. que el frontend diverja de la API real;
2. que un enfoque CRUD genérico o metadata-driven oculte código difícil de mantener;
3. que Auth filtre password o maneje JWT de forma inconsistente;
4. que IDs compuestos/relaciones no sean navegables;
5. que la personalización visual obligue a editar manualmente el proyecto generado.

## Decisiones

- componentes específicos por entidad;
- dashboard conservador con conteos;
- `to-one` con selección simple y `many-to-many` con selección múltiple;
- color primario configurable durante la exportación;
- Angular standalone;
- Auth con login/bootstrap/interceptor/guard/logout;
- mismo ZIP generado, carpeta `frontend/`;
- Domain Manifest + contrato API como fuentes canónicas de generación.

## Incrementos

- `C6-cu17-000-angular-frontend-scope.md`: decisiones fijadas.
- `C6-cu17-001-angular-frontend-generation.md`: implementación y acceptance.
- `C6-cu17-002-template-literal-validator-recovery.md`: compatibilidad de template literals TypeScript con el validator.
- `C6-cu17-003-dashboard-contract-test-recovery.md`: reconciliación del contrato focal del dashboard.
- `C6-cu17-004-typescript6-config-recovery.md`: compatibilidad del workspace generado con TypeScript 6.

## Criterios de salida

- [x] proyecto Angular completo;
- [x] componentes específicos por entidad;
- [x] dashboard;
- [x] CRUD/query UI;
- [x] relaciones;
- [x] ID compuesto;
- [x] Simple sin Auth;
- [x] Auth con login/bootstrap/JWT;
- [x] password protegido;
- [x] color primario seleccionable;
- [x] generación determinista;
- [x] `ng build` sobre frontends generados Simple/Auth;
- [x] regresiones CU-13..16;
- [x] full backend;
- [x] frontend focal;
- [x] Angular ClassForge build;
- [x] documentación/evidencia.

## Resultado

CU-17 y Ciclo 6 quedan CERRADOS. El siguiente caso fue redefinido antes de implementarse: CU-18 genera Flutter mobile independiente y no reutiliza Angular mediante Capacitor.
