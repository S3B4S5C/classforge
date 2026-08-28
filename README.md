# ClassForge

> Model it. Generate it. Talk to it.

ClassForge es una herramienta CASE web y colaborativa para modelar diagramas de clases UML y utilizarlos como fuente de verdad para generar backends Spring Boot, frontends web/mobile y artefactos auxiliares operables tambien mediante lenguaje natural y voz.

La especificacion consolidada del producto esta en `docs/product/product.md`.

## Estado actual

Implementado hasta **CU-05 — Deshacer y rehacer cambios**.

- autenticacion y ownership;
- CU-01 crear proyecto;
- CU-02 abrir/guardar con revision;
- modelo UML canonico tipado;
- CU-03 editor manual JointJS;
- clases y atributos;
- Association, Aggregation, Composition y Generalization;
- multiplicidades;
- inspector;
- CU-04 validacion explicita;
- ERROR/WARNING/INFO;
- diagnosticos navegables;
- CU-05 Command Bus;
- Undo/Redo local;
- shortcuts de teclado;
- historial local acotado.

El siguiente caso es **CU-06 — Colaboracion en tiempo real**.

## Ejecutar backend

```powershell
cd backend
.\gradlew.bat bootRun
```

Backend:

```text
http://localhost:8082
```

Health:

```text
GET http://localhost:8082/api/health
```

Consola H2:

```text
http://localhost:8082/h2-console
```

## Ejecutar frontend

```powershell
cd frontend
npm install
npm start
```

Frontend:

```text
http://localhost:4200
```

El proxy Angular reenvia `/api/*` a:

```text
http://localhost:8082
```

## Persistencia vigente

La aplicacion usa Spring Data JPA/Hibernate.

- H2 en archivo para desarrollo.
- H2 en memoria para tests.
- PostgreSQL como destino posterior.

Un posible `*.classforge` se considera formato portable futuro, no persistencia primaria.

## Arquitectura vigente

```text
ProjectDocument
├── UmlModel
└── DiagramLayout
```

Desde CU-05:

```text
UI / Canvas
    ↓
UmlCommand
    ↓
UmlCommandBus
    ↓
UmlCommandExecutor
    ↓
ProjectDocument
    ↓
JointJS
```

Reglas:

- JointJS representa el modelo; no es el modelo.
- UUID es identidad estable.
- Command Bus gobierna las mutaciones manuales.
- el backend sigue siendo autoridad final de validacion.
- Guardar usa revision explicita.
- Validar no persiste ni cambia revision.
- Material Symbols Rounded se sirve localmente.

## PUDS

Consultar:

- `docs/puds/use-cases.md`;
- `docs/puds/current-status.md`;
- `docs/puds/iterations/`.

Los diagramas UML academicos se elaboran separadamente en `docs/uml/`.

## Proximo hito

**CU-06 — Colaboracion en tiempo real.**

CU-06 debera transportar operaciones/comandos y mantener al servidor como autoridad.