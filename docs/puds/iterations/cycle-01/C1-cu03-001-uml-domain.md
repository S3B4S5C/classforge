# Iteracion E1 — CU03-001 Modelo UML canonico

## Objetivo

Implementar la primera parte de CU-03 sin introducir JointJS:

- modelo UML tipado;
- clases;
- atributos;
- validacion;
- edicion estructurada;
- persistencia mediante CU-02.

## Funcionalidad

El usuario puede crear, editar y eliminar clases y atributos; elegir tipo, visibilidad UML, identifier y nullable; y guardar el documento.

## Arquitectura

```text
Angular dialogs/editor
        ↓
ProjectWorkspaceStore
        ↓
ProjectDocument tipado
        ↓
PUT /document
        ↓
ProjectDocumentValidator
        ↓
revisionado CU-02
        ↓
H2
```

## Riesgo reducido

Antes de integrar JointJS se demuestra que el modelo existe fuera del canvas, se persiste, se reconstruye, posee IDs estables y es validado por backend.

## Responsive

- Desktop: grid de clases + panel lateral.
- Laptop: grid adaptable + panel compacto.
- Mobile: una columna, toolbar apilada y dialogos limitados al viewport.

## Pruebas

- clase + atributo valido;
- clase duplicada;
- atributo duplicado;
- identificador nullable;
- custom type sin nombre;
- persistencia tipada;
- HTTP 400 con violations;
- build backend;
- build frontend.