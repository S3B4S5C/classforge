# Iteracion E1 — CU03-002 Canvas JointJS

## Objetivo

Representar visualmente el modelo UML canonico de CU03-001 sin convertir el canvas en fuente de verdad.

## Alcance

Incluido:

1. instalar JointJS Core;
2. crear shape UML propio;
3. reconstruir Graph desde ProjectDocument;
4. crear clase desde toolbar;
5. editar clase con doble clic;
6. arrastrar clases;
7. persistir posiciones;
8. zoom;
9. pan;
10. fit-to-content;
11. reset de vista;
12. responsive laptop/mobile.

No incluido:

- relaciones;
- multiplicidades;
- puertos;
- inspector contextual completo;
- auto-layout ELK;
- undo/redo;
- WebSocket.

## Flujo

```text
Usuario mueve Animal
        |
        v
JointJS actualiza SVG
        |
   pointerup
        |
        v
ProjectWorkspaceStore.updateNodeLayout()
        |
        v
ProjectDocument.layout
        |
        v
dirty = true
        |
     Guardar
        |
        v
PUT /api/projects/{id}/document
```

## Comprobacion de arquitectura

Mover una clase no modifica:

```text
UmlModel
```

Solo modifica:

```text
DiagramLayout
```

Un test de integracion backend verifica que la semantica de la clase permanece igual despues de persistir un movimiento.

## Criterios de aceptacion

- las clases existentes aparecen al abrir el proyecto;
- los atributos se muestran dentro del shape;
- crear una clase la muestra inmediatamente;
- doble clic permite renombrarla;
- mover una clase activa estado dirty;
- guardar incrementa revision;
- F5 conserva la nueva posicion;
- zoom/pan no activan dirty;
- fit y reset no se persisten;
- el editor estructurado sigue funcionando;
- frontend compila;
- backend tests/build pasan.