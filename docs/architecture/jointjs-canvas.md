# ClassForge — Canvas JointJS

## Decision

CU03-002 integra `@joint/core` como motor visual del diagramador.

JointJS **no es** el modelo de dominio ni el formato de persistencia.

```text
ProjectDocument
      |
      v
UmlCanvasComponent
      |
      v
dia.Graph + dia.Paper
```

Al cargar o modificar el documento, el Graph se reconstruye desde:

- `UmlClass`;
- `UmlAttribute`;
- `DiagramNodeLayout`.

No se persiste:

- `graph.toJSON()`;
- atributos internos de JointJS;
- IDs generados por el canvas;
- transformaciones de viewport.

## Shape UML

ClassForge define un shape SVG propio:

```text
+------------------------------+
| CLASS                        |
| Animal                       |
+------------------------------+
| - id: Long {id}              |
| - nombre: String [0..1]      |
+------------------------------+
```

El shape utiliza el UUID de `UmlClass` como ID del elemento JointJS.

## Movimiento

JointJS modifica visualmente la posicion durante drag.

Solo al finalizar la interaccion (`element:pointerup`) se emite:

```text
classId
x
y
width
height
```

El `ProjectWorkspaceStore` actualiza exclusivamente `DiagramLayout`.

Esto evita re-renderizar el Graph en cada pixel del movimiento y mantiene separada la semantica UML.

## Edicion

- Crear clase: toolbar del canvas -> dialog Angular -> Store.
- Editar clase: doble clic en el shape -> dialog Angular -> Store.
- Atributos: editor estructurado existente.
- Eliminar: editor estructurado existente.

CU03-003 incorporara relaciones e inspector contextual.

## Navegacion

El canvas soporta:

- drag sobre fondo para pan;
- rueda/trackpad para pan;
- Ctrl/Cmd + rueda para zoom;
- pinch cuando el dispositivo lo emite;
- zoom in/out;
- fit-to-content;
- reset view.

Las transformaciones del viewport no se persisten porque no forman parte del diagrama.

## Responsive

### Desktop

Canvas amplio, toolbar horizontal y editor estructurado debajo.

### Laptop

Canvas conserva prioridad espacial; toolbar se compacta y el panel de estado se adapta.

### Mobile

- toolbar en varias filas;
- canvas con altura limitada al viewport;
- controles tactiles grandes;
- pan sobre fondo;
- clases arrastrables;
- doble toque/doble clic para editar.

## Dependencia

```text
@joint/core 4.3.2
```

Se usa la edicion open-source, sin componentes comerciales de JointJS+.