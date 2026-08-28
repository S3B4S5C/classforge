# ClassForge — Relaciones UML

## Alcance CU03-003

El modelo manual soporta:

- Association;
- Aggregation;
- Composition;
- Generalization.

Cada relacion posee UUID estable y referencia clases por UUID.

## Semantica de extremos

### Association

Origen y destino son extremos equivalentes desde el punto de vista visual.

### Aggregation

El `sourceClassId` representa el lado del rombo hueco: el todo/agregado.

### Composition

El `sourceClassId` representa el lado del rombo lleno: el todo/propietario.

### Generalization

El origen es la subclase y el destino la superclase.

La flecha triangular apunta al destino.

## Multiplicidad

ClassForge representa multiplicidad de forma estructurada:

```text
lower: int
upper: int | *
```

Opciones iniciales de UI:

- 1
- 0..1
- 0..*
- 1..*

Generalization no presenta multiplicidades en la UI. Sus dos multiplicidades se persisten como `null`, porque la generalizacion UML no tiene semantica de cardinalidad. El backend omite la validacion de multiplicidad exclusivamente para este tipo de relacion.

## JointJS

Cada `UmlRelationship` se proyecta a `shapes.standard.Link`.

La proyeccion utiliza:

- source/target por UUID;
- router Manhattan;
- connector Rounded;
- markers UML;
- labels nativos para multiplicidad.

Los links JointJS nunca se persisten.

## Inspector

Seleccionar una clase o link actualiza un inspector Angular fuera del SVG.

El inspector permite:

- editar clase;
- anadir atributo;
- eliminar clase;
- editar relacion;
- eliminar relacion;
- revisar extremos y multiplicidades.

Esto mantiene logica de formularios fuera del motor SVG.

## Validacion backend

Antes de guardar se verifican:

- referencias a clases existentes;
- tipo de relacion;
- multiplicidades;
- generalizacion hacia si misma;
- ciclos de generalizacion.

Un ciclo como:

```text
A -> B
B -> C
C -> A
```

devuelve `400 VALIDATION_ERROR` con codigo `GENERALIZATION_CYCLE`.

## Eliminacion

Eliminar una clase desde el store elimina tambien todas sus relaciones.

El backend igualmente rechazaria un documento con relaciones huerfanas.