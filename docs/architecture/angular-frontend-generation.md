# Generación Angular — CU-17

## Estado

**CU-17 implementado y aceptado en Ciclo 6.**

El frontend web se genera dentro del mismo ZIP del sistema Spring y vive en `frontend/`.

## Fuente de verdad

CU-17 no reconstruye el dominio desde HTML ni desde OpenAPI.

```text
SpringGenerationModel
        |
SpringApiGenerationPlan
        |
SpringApiContract
        |
DomainManifestPlan
        |
        v
AngularFrontendRenderer
```

`domain-manifest.json` aporta semántica estable; el contrato API fija paths y operaciones. El frontend generado no es una nueva fuente de verdad.

## Decisiones de producto

- Angular standalone.
- Componentes **específicos por entidad**, no un CRUD genérico metadata-driven.
- Cada entidad genera:
  - `<entity>.models.ts`;
  - `<entity>.api.ts`;
  - `<entity>-list.component.ts`;
  - `<entity>-detail.component.ts`;
  - `<entity>-form.component.ts`.
- Dashboard con conteos por entidad y accesos directos. No se inventan gráficos ni KPIs sin semántica del dominio.
- `to-one`: selección de una referencia.
- `many-to-many`: selección múltiple.
- IDs compuestos se transportan como query params para get/update/delete.
- No hay SSR.
- La UI generada usa un color primario `#RRGGBB` seleccionado al exportar.
- `displayName` proviene del Domain Manifest; CU-17 no inventa pluralizaciones ni aliases.

## SIMPLE_CRUD

Genera:

- dashboard;
- navegación por entidad;
- listados;
- búsqueda;
- filtros;
- ordenamiento;
- paginación;
- detalle;
- creación;
- edición;
- eliminación;
- formularios derivados de tipos/nullability/capacidades;
- controles de relaciones.

No genera login, JWT, guards ni interceptores Auth.

## AUTH_INFORMATION_SYSTEM

Además genera:

- `/login`;
- `/bootstrap` para la primera cuenta;
- `AuthService`;
- interceptor Bearer JWT;
- route guard;
- logout;
- redirección a login ante 401;
- password con `input type="password"` y nunca como campo readable.

El bootstrap usa el mismo request de la entidad Auth y el endpoint público de CU-14.

## Dashboard

El dashboard hace `GET <entity-endpoint>/count` por entidad y muestra:

- nombre;
- cantidad total;
- acceso al listado.

Esto es deliberadamente conservador: el modelo no contiene todavía semántica suficiente para elegir métricas, agregaciones o gráficos de negocio.

## Theming

El request de exportación añade:

```json
{
  "primaryColor": "#2563EB"
}
```

El backend normaliza/valida el formato `#RRGGBB` y el frontend generado materializa:

```css
:root {
  --app-primary: #2563EB;
}
```

El color se aplica a navegación, acciones principales, foco y acentos.

## Acceptance

`generatedAngularAcceptance` genera dos sistemas reales:

- `SIMPLE_CRUD`;
- `AUTH_INFORMATION_SYSTEM`.

Los frontends se construyen con el mismo toolchain Angular instalado en ClassForge mediante un enlace temporal de `node_modules`, evitando descargas durante el acceptance.

Además se ejecutan regresiones CU-13/CU-14/CU-15/CU-16, suite backend completa, tests focales frontend, build Angular de ClassForge y `git diff --check`.

## Fuera de alcance

- Flutter/Android: CU-18, como proyecto móvil independiente (no empaquetado de Angular).
- IA/voz de la aplicación generada: CU-19..23.
- roles/refresh token;
- dashboards semánticos o BI;
- personalización visual distinta del color primario;
- generación de componentes desde reglas manuales posteriores al ZIP.
