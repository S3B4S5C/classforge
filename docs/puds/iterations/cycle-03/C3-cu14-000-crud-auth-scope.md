# C3-cu14-000 — Preflight de alcance: CRUD simple vs Sistema de Información con Auth

**Ciclo:** 3 — Construcción
**CU:** CU-14 — Generar API CRUD expresiva
**Estado:** CERRADO / DESIGN INPUT SATISFECHO
**Fecha:** 13 de septiembre de 2026
**Dependencias cerradas:** CU-12, CU-13

## Objetivo

Fijar antes de implementar CU-14 la decisión de producto que no debe perderse durante el diseño técnico: la generación tendrá **dos modos explícitos**.

```text
Modo A: CRUD simple
Modo B: Sistema de Información con Auth
```

No se modelará como un único CRUD con un checkbox ambiguo de seguridad.

## Modo A — CRUD simple

Debe extender el ZIP de CU-13 con la capa de API de dominio:

- controllers;
- services;
- DTOs y mapping;
- CREATE / READ / UPDATE / DELETE / LIST;
- filtros;
- búsquedas;
- ordenamiento;
- paginación;
- conteo;
- navegación de relaciones.

No genera Spring Security, endpoint de login, JWT ni password hashing por el mero hecho de existir un atributo llamado `password`.

## Modo B — Sistema de Información con Auth

Incluye todo el CRUD simple y obliga a configurar la fuente de credenciales. La UI de exportación debe solicitar, en este orden:

1. **Tabla/entidad de autenticación**.
2. **Atributo de usuario/login** de esa tabla/entidad.
3. **Atributo de contraseña** de esa misma tabla/entidad.

### Restricciones mínimas

- La tabla debe provenir de una clase UML (`RelationalTableOriginType.UML_CLASS`).
- No puede elegirse una join table N:M como tabla de autenticación.
- Usuario y contraseña deben ser atributos UML reales de la entidad elegida, no columnas FK sintéticas ni PK heredadas.
- Usuario y contraseña deben ser atributos distintos.
- Cambiar la tabla invalida/reinicia las dos selecciones de atributos.
- Cualquier referencia stale o selección inconsistente falla antes de renderizar y no produce ZIP parcial.

### Identidad del contrato

La UX puede hablar de “tabla” porque es la abstracción visible para el sistema de información, pero el request no debe depender de strings renombrables. Debe transportar identidades estables del origen canónico:

```text
authClassId / authTableOriginId
usernameAttributeId
passwordAttributeId
```

El mapeo CU-12 ya conserva esas identidades mediante `RelationalTable.origin().umlElementId()` y `RelationalColumn.sourceElementId()`.

## Semántica de seguridad mínima

El modo Auth debe contemplar como mínimo:

- Spring Security;
- `PasswordEncoder`;
- login contra la entidad elegida;
- JWT;
- protección del API;
- exclusión del password en DTOs de respuesta;
- hashing al escribir el atributo que actúa como contraseña.

El incremento C3-cu14-001 cerró esas políticas con defaults explícitos: `/api/auth/login` y un bootstrap de primera cuenta son públicos; el bootstrap solo funciona mientras la entidad de autenticación esté vacía; no existe registro público permanente; el resto de `/api/**` requiere Bearer JWT; el token expira a los 3600 segundos y no se genera refresh token; no se introducen roles en CU-14.

## Separación de identidades

Existen dos dominios de autenticación distintos:

- **ClassForge:** `CurrentUser`, OWNER/EDITOR/NONE decide quién puede abrir/generar el proyecto.
- **Aplicación generada:** la tabla/entidad seleccionada y sus dos atributos deciden el login del sistema generado.

No se reutiliza automáticamente la base de usuarios de ClassForge dentro del sistema generado.

## No persistencia

La configuración de modo/Auth es target-specific para una exportación. No debe:

- modificar `ProjectDocument`;
- modificar `UmlModel`;
- persistir `RelationalModel`;
- agregar estereotipos ocultos a la clase elegida;
- incrementar `revision`.

## Criterio de entrada satisfecho por C3-cu14-001

C3-cu14-001 implementó y verificó el diseño ejecutable que cierra:

1. request/config de generación y diagnósticos estables;
2. contrato de DTOs y mapping;
3. semántica de filtros/paginación/relaciones;
4. política de endpoints protegidos en modo Auth;
5. lifecycle de password;
6. login/JWT y estrategia para primera cuenta/registro;
7. tests/acceptance de ambos modos.
