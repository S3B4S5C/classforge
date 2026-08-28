# Iteracion E1 — CU03-003 Relaciones e inspector

## Objetivo

Cerrar el alcance funcional de CU-03: creacion manual del diagrama de clases.

## Funcionalidad incorporada

1. modo Crear relacion;
2. seleccion de origen;
3. seleccion de destino;
4. dialog de configuracion;
5. Association;
6. Aggregation;
7. Composition;
8. Generalization;
9. multiplicidades;
10. links UML en JointJS;
11. seleccion de links;
12. doble clic para editar;
13. eliminacion;
14. inspector contextual;
15. listado estructurado de relaciones;
16. responsive reforzado.

## Flujo de creacion

```text
Relacion
   |
seleccionar origen
   |
seleccionar destino
   |
dialog UML
   |
ProjectWorkspaceStore.addRelationship()
   |
ProjectDocument
   |
JointJS re-proyecta
   |
dirty = true
```

## Arquitectura

JointJS solamente captura seleccion y representa geometria.

Los dialogos Angular producen estructuras `UmlRelationship`.

El store modifica `ProjectDocument`.

El backend valida antes de persistir.

## Validaciones relevantes

- referencia inexistente;
- multiplicidad invalida;
- generalizacion a si misma;
- ciclo de herencia.

## Responsive

### Desktop

Canvas + inspector lateral.

### Laptop

Canvas prioritario + inspector compacto.

### Tablet/mobile

Inspector se coloca debajo del canvas y las acciones pasan a una columna cuando el ancho lo exige.

El listado estructurado de relaciones permite operar incluso cuando seleccionar un link pequeno sea menos comodo en pantalla tactil.

## Definition of Done CU-03

Al finalizar CU03-003 el usuario puede:

- crear clases;
- crear atributos;
- mover clases;
- crear relaciones UML;
- configurar multiplicidades;
- editar/eliminar todo lo anterior;
- guardar;
- recargar;
- reconstruir el mismo diagrama.

CU-03 queda funcionalmente cerrado.