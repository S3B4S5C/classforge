# C6-cu17-001 — Generación Angular y cierre

## Implementación

CU-17 añade `AngularFrontendRenderer` al pipeline después de Domain Manifest.

Salida añadida:

```text
frontend/
├── package.json
├── angular.json
├── src/app/
│   ├── dashboard/
│   ├── core/
│   ├── auth/              # solo Auth
│   └── entities/
│       └── <entity>/
│           ├── <entity>.models.ts
│           ├── <entity>.api.ts
│           ├── <entity>-list.component.ts
│           ├── <entity>-detail.component.ts
│           └── <entity>-form.component.ts
└── README.md
```

El renderer recibe el `DomainManifestPlan`, nombre del artefacto y color primario.

## Hardening

`GeneratedProjectValidator` falla cerrado si:

- faltan archivos Angular estructurales;
- una entidad del manifest no tiene sus componentes específicos;
- SIMPLE_CRUD contiene Auth;
- AUTH_INFORMATION_SYSTEM carece de login/bootstrap/guard/interceptor;
- el theme no contiene un color `#RRGGBB`;
- una expresión FreeMarker queda sin resolver fuera del TypeScript generado.

Los template literals `${...}` dentro de `frontend/**/*.ts` se tratan como sintaxis TypeScript, no como FreeMarker. La regresión queda registrada en `C6-cu17-002-template-literal-validator-recovery.md`.

## Acceptance

Gate: `generatedAngularAcceptance`.

Genera Simple y Auth y ejecuta `ng build --configuration production` sobre ambos reutilizando temporalmente el `node_modules` ya instalado de ClassForge.

## Resultado

CERRADO cuando el patch runner completa todos los gates sin rollback.
