# C6-cu17-000 — Scope Angular generado

## Decisiones fijadas

1. Se generan componentes específicos por entidad, no una pantalla CRUD genérica.
2. Cada entidad tiene models/api/list/detail/form propios.
3. El dashboard muestra conteos por entidad y accesos directos; no inventa KPIs.
4. Relaciones to-one usan selector simple; relaciones many-to-many usan selector múltiple.
5. SIMPLE_CRUD no contiene código Auth.
6. AUTH_INFORMATION_SYSTEM añade login, bootstrap inicial, Bearer JWT, interceptor, guard, logout y 401 -> login.
7. Password se renderiza como campo secreto y no como dato readable.
8. IDs compuestos usan `/by-id` y query params según CU-14/CU-15.
9. Angular es standalone y SPA; no SSR.
10. La exportación permite elegir un color primario `#RRGGBB`.
11. El proyecto vive en `frontend/` dentro del ZIP generado.
12. Domain Manifest y SpringApiContract siguen siendo las fuentes; el frontend no persiste configuración adicional en el UML.

## Dashboard

Solo muestra métricas que el contrato realmente garantiza: `count` por entidad.

## Límite

Capacitor/Android corresponde a CU-18. Voz/IA de la aplicación generada corresponde a CU-19..23.
