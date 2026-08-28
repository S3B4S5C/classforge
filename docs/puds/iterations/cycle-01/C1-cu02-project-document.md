# Iteracion E1 — CU-02 Abrir y guardar proyecto

## Objetivo

Convertir Project en un documento persistible y versionado antes de introducir el canvas UML.

## Casos trabajados

- CU-02 Abrir y guardar proyecto.
- CU-30 Acceder a proyectos propios.

## Decisiones

1. El backend sigue siendo la fuente de verdad.
2. `ProjectDocument` se separa en semantica UML y layout.
3. JointJS no se persiste.
4. El documento posee `schemaVersion`.
5. `Project.revision` controla concurrencia.
6. El guardado utiliza `baseRevision`.
7. Un conflicto devuelve HTTP 409.
8. Renombrar metadata no cambia la revision UML.
9. El acceso continua limitado por owner.
10. Material Symbols se sirve localmente para mantener offline.

## Implementacion

### Backend

Se agregaron:

- ProjectDocument.
- UmlModel.
- DiagramLayout.
- guardado versionado;
- renombrado;
- migracion del snapshot vacio de CU-01;
- deteccion de revision obsoleta.

### Frontend

Se agregaron:

- contratos ProjectDocument;
- store del workspace con Signals;
- estados saved/dirty/saving/conflict/error;
- boton Guardar;
- renombrado del proyecto;
- indicadores responsive;
- Material Symbols locales.

## Pruebas

Deben comprobarse:

- proyecto nuevo contiene documento vacio valido;
- lectura posterior conserva documento;
- guardar incrementa revision;
- baseRevision obsoleta produce 409;
- otro owner no puede guardar;
- renombrar conserva revision;
- snapshot CU-01 vacio puede abrirse;
- Angular compila;
- Gradle tests/build pasan.

## Resultado esperado

Al terminar la iteracion, CU-03 puede introducir clases y relaciones sin decidir nuevamente como se persiste el modelo.