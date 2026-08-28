# ClassForge — Modelo UML canonico

## Objetivo

CU03-001 reemplaza las colecciones genericas del documento por tipos de dominio explicitos.

JointJS no participa en este modelo.

## Estructura

```text
ProjectDocument
├── UmlModel
│   ├── UmlClass[]
│   │   └── UmlAttribute[]
│   └── UmlRelationship[]
└── DiagramLayout
    └── classId -> DiagramNodeLayout
```

## UmlClass

- `id: UUID`
- `name`
- `attributes`

El ID es la identidad estable. Renombrar una clase no rompe referencias.

## UmlAttribute

- `id: UUID`
- `name`
- `dataType`
- `customTypeName`
- `visibility`
- `nullable`
- `identifier`

### Tipos iniciales

- String
- Integer
- Long
- Decimal
- Boolean
- Date
- DateTime
- UUID
- Custom

### Visibilidad UML

- PUBLIC -> `+`
- PRIVATE -> `-`
- PROTECTED -> `#`
- PACKAGE -> `~`

## Nombres code-ready

Los nombres de clases, atributos y tipos personalizados deben cumplir:

```text
^[A-Za-z_][A-Za-z0-9_]*$
```

Esto evita ambiguedad al generar Java, TypeScript, rutas y artefactos relacionados.

## Identificadores

Un atributo `identifier=true` no puede ser nullable.

ClassForge no asume que un atributo llamado `id` sea identificador. La semantica se modela explicitamente.

## Relaciones

Aunque CU03-001 aun no ofrece UI para relaciones, el dominio ya reserva:

- ASSOCIATION
- AGGREGATION
- COMPOSITION
- GENERALIZATION

CU03-003 incorporara su edicion visual.

## Layout

`DiagramNodeLayout` contiene x, y, width y height.

Crear una clase genera un layout inicial. CU03-002 permitira modificarlo mediante JointJS.

## Validacion

El frontend previene errores comunes para UX.

El backend es la autoridad definitiva y valida antes de persistir:

- IDs duplicados;
- nombres invalidos;
- nombres de clase duplicados;
- atributos duplicados por clase;
- tipos y visibilidad;
- custom types;
- identifier nullable;
- referencias de relaciones;
- multiplicidades;
- layout huerfano o invalido.

Un documento invalido no incrementa revision ni se persiste.