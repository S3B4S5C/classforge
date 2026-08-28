# Iteracion E1 — CU04-001 Validacion UML

## Objetivo

Permitir al modelador ejecutar explicitamente la validacion del diagrama actual sin tener que guardarlo.

## Precondicion

CU-03 completo:

- modelo canonico tipado;
- canvas JointJS;
- clases y atributos;
- relaciones y multiplicidades;
- inspector.

## Flujo principal

1. Usuario modifica el diagrama.
2. Pulsa `Validar`.
3. Frontend envia el `ProjectDocument` draft.
4. Backend verifica ownership.
5. `ProjectDocumentValidator.analyze()` ejecuta las reglas.
6. Backend devuelve HTTP 200 con errores, warnings e infos.
7. Frontend muestra el resumen.
8. Usuario pulsa un diagnostico navegable.
9. ClassForge selecciona la clase o relacion afectada en JointJS.
10. La revision del proyecto permanece sin cambios.

## Flujo de guardado

Guardar conserva la proteccion defensiva:

```text
Guardar
  |
analyze()
  |
ERROR?
  | si
400 VALIDATION_ERROR
  |
  no
persistir + revision
```

Warnings no bloquean.

## Reglas de calidad iniciales

- modelo vacio;
- clase sin atributos;
- clase sin identificador;
- clase aislada;
- asociacion reflexiva.

## Reutilizacion

CU-04 no duplica las reglas introducidas en CU-03. Refactoriza el mismo motor para producir un reporte.

## Responsive

El panel de diagnosticos:

- mantiene resumen horizontal en desktop;
- apila resumen y contadores en tablet/mobile;
- ofrece filas tactiles completas;
- conserva una ruta alternativa a la seleccion directa de elementos pequenos del SVG.

## Definition of Done

- boton Validar visible;
- validacion sin persistencia;
- HTTP 200 para modelos validos e invalidos;
- ERROR/WARNING/INFO;
- elementId navegable;
- panel de resultados;
- click resalta clase/relacion;
- Guardar sigue bloqueando errores;
- warnings permiten guardar;
- revision no cambia al validar;
- tests backend;
- build frontend;
- documentacion PUDS.