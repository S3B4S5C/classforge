# ClassForge — Project Document

## Decision

Desde CU-02, un proyecto no persiste el estado interno del canvas.

Persiste un documento versionado:

```text
Project
├── id
├── ownerId
├── name
├── revision
└── ProjectDocument
    ├── schemaVersion
    ├── UmlModel
    │   ├── classes
    │   └── relationships
    └── DiagramLayout
        └── nodes
```

## Separacion de responsabilidades

### UmlModel

Describe la semantica del modelo:

- clases;
- relaciones;
- posteriormente atributos, multiplicidades, tipos y demas conceptos UML.

### DiagramLayout

Describe exclusivamente la representacion visual:

- posicion;
- dimensiones;
- estado visual que deba persistir.

JointJS no sera el formato de persistencia.

## Revision

`Project.revision` representa la revision del documento colaborativo.

Un proyecto nuevo comienza en revision 0.

Guardar correctamente un documento incrementa:

```text
0 -> 1 -> 2 -> 3
```

Renombrar el proyecto no incrementa esta revision porque el nombre es metadata, no contenido UML colaborativo.

## Concurrencia

El cliente envia:

```json
{
  "baseRevision": 4,
  "document": {}
}
```

Si el servidor continua en revision 4, guarda y pasa a 5.

Si el servidor ya esta en revision 5, responde `409 PROJECT_REVISION_CONFLICT`.

El repositorio adquiere un bloqueo de escritura durante la comprobacion y persistencia para que la comparacion de revision sea atomica dentro de la transaccion.

## Migracion CU-01

CU-01 almacenaba:

```json
{
  "schemaVersion": "1.0",
  "elements": []
}
```

Como CU-01 no permitia crear elementos UML, un snapshot legacy vacio migra deterministicamente a `ProjectDocument.empty()`.

No se intenta adivinar la semantica de snapshots legacy no vacios.

## Persistencia fisica

Durante desarrollo se conserva la columna H2 `uml_model` para evitar destruir bases existentes.

Su significado logico desde CU-02 es `documentJson`.

Una migracion de esquema fisico podra renombrarla posteriormente cuando se introduzca Flyway.

## API

- `GET /api/projects/{id}` abre el documento.
- `PATCH /api/projects/{id}` cambia metadata como el nombre.
- `PUT /api/projects/{id}/document` guarda el documento con revision optimista.

Todos los endpoints respetan ownership/autorizacion.