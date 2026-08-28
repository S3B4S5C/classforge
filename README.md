# ClassForge

> Model it. Generate it. Talk to it.

ClassForge es una herramienta CASE web y colaborativa para modelar diagramas de clases UML y utilizarlos como fuente de verdad para generar backends Spring Boot, frontends web/mobile y artefactos auxiliares operables también mediante lenguaje natural y voz.

La especificación consolidada del producto está en [`docs/product/product.md`](docs/product/product.md).

## Estructura

```text
classforge/
├── frontend/        # Angular 22
├── backend/         # Spring Boot 4 / Java 21
├── docs/
│   ├── product/
│   ├── architecture/
│   ├── puds/
│   └── uml/
├── examples/
└── scripts/
```

Este es un monorepo simple: Angular y Gradle siguen siendo proyectos independientes. No utiliza Nx, Turborepo ni una herramienta adicional de orquestación de monorepo.

## Requisitos

### Frontend

- Node.js **22.22.3 o superior dentro de una rama soportada por Angular 22**.
- npm.

Se incluye `.nvmrc` dentro de `frontend/`.

### Backend

- JDK 21 o superior para ejecutar las herramientas de desarrollo.
- El proyecto compila con `java.version=21`.
- Gradle 9.1+ si vas a ejecutar Gradle con tu JDK 25. IntelliJ IDEA puede gestionar la distribución de Gradle al importar el proyecto.

## Ejecutar backend

Desde IntelliJ, abre la carpeta `backend/` como proyecto Gradle y ejecuta:

```text
com.classforge.ClassForgeApplication
```

O desde terminal, si tienes Gradle 9.1+ instalado:

```bash
cd backend
gradle bootRun
```

El backend escucha en `http://localhost:8080`.

Prueba:

```text
GET http://localhost:8080/api/health
```

Respuesta esperada:

```json
{
  "status": "UP",
  "application": "ClassForge"
}
```

## Ejecutar frontend

```bash
cd frontend
npm install
npm start
```

El frontend escucha en `http://localhost:4200`.

Durante desarrollo Angular usa `proxy.conf.json`, por lo que `/api/*` se reenvía a `http://localhost:8080`.

Al levantar ambos proyectos, la página principal debe mostrar:

```text
Backend: ClassForge: UP
```

## Base de datos inicial

El backend usa H2 en archivo para el desarrollo inicial:

```text
backend/data/classforge
```

La consola está habilitada en:

```text
http://localhost:8080/h2-console
```

Más adelante se separarán perfiles para H2 (desarrollo/demo) y PostgreSQL.

## Decisiones iniciales

- Aplicación principal: Angular 22 web.
- Backend/orquestador: Spring Boot.
- Target Java: 21.
- Servidor autoritativo para colaboración.
- WebSocket/STOMP se incorporará sobre el backend existente.
- El núcleo UML se implementará antes del canvas visual.
- Los proyectos generados no se versionarán automáticamente en este repositorio.

## Próximo hito

Implementar el **modelo canónico UML** (`UMLModel`) antes de instalar JointJS o comenzar la generación de código.
