# ClassForge — Errores de validacion HTTP 400

## Formato

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Hay datos del modelo UML que necesitan correccion.",
  "violations": [
    {
      "field": "document.umlModel.classes[1].name",
      "code": "DUPLICATE_CLASS_NAME",
      "message": "Ya existe otra clase con el nombre 'Animal'."
    }
  ]
}
```

Cada violacion contiene una ruta, un codigo estable y un mensaje legible.

El frontend muestra el resumen y la lista de violaciones. La validacion Angular mejora UX, pero no reemplaza la validacion backend.

Para JSON que no puede interpretarse se utiliza `INVALID_REQUEST_BODY` con una violacion `INVALID_JSON`.