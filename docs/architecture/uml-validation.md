# ClassForge — Validacion explicita del modelo UML

## Objetivo

CU-04 convierte el validador defensivo introducido durante CU-03 en una funcionalidad CASE visible para el usuario.

La validacion puede ejecutarse sobre el `ProjectDocument` en memoria sin guardar.

```text
ProjectDocument draft
        |
POST /api/projects/{id}/validate
        |
ProjectDocumentValidator.analyze()
        |
ProjectValidationResponse
        |
panel de diagnosticos
```

## Un solo motor

No existen dos juegos de reglas.

### Guardar

`saveDocument()` llama a `ProjectDocumentValidator.validate()`.

`validate()` reutiliza `analyze()` y convierte exclusivamente los diagnosticos `ERROR` en `400 VALIDATION_ERROR`.

Las advertencias no impiden guardar.

### Validar

`validateDocument()` llama a `analyze()` y devuelve todos los diagnosticos con HTTP 200, incluso si hay errores.

No cambia revision y no persiste el draft.

## Severidades

### ERROR

Invalida el modelo y bloquea el guardado.

Ejemplos:

- nombre invalido;
- clase duplicada;
- atributo duplicado;
- identifier nullable;
- relacion huerfana;
- multiplicidad invalida;
- generalizacion hacia si misma;
- ciclo de herencia;
- layout invalido.

### WARNING

No bloquea el guardado, pero indica riesgos de calidad o de generacion futura.

Reglas iniciales:

- `MODEL_EMPTY`;
- `CLASS_WITHOUT_ATTRIBUTES`;
- `CLASS_WITHOUT_IDENTIFIER`;
- `ISOLATED_CLASS`;
- `REFLEXIVE_ASSOCIATION`.

### INFO

La severidad forma parte del contrato desde CU-04 para reglas informativas futuras. La iteracion inicial no necesita generar ruido artificial y puede devolver `infos = 0`.

## Diagnostico

```json
{
  "severity": "WARNING",
  "code": "CLASS_WITHOUT_IDENTIFIER",
  "field": "document.umlModel.classes[0].attributes",
  "elementId": "uuid-de-la-clase",
  "message": "La clase 'Animal' no tiene un atributo marcado como identificador."
}
```

`elementId` identifica el elemento visible del canvas:

- errores de clase o atributo -> UUID de la clase;
- errores de relacion -> UUID de la relacion;
- diagnosticos globales -> `null`.

El frontend utiliza este valor para seleccionar/resaltar el elemento correspondiente en JointJS.

## Respuesta

```json
{
  "valid": true,
  "errors": 0,
  "warnings": 2,
  "infos": 0,
  "diagnostics": []
}
```

`valid` significa `errors == 0`.

Un modelo puede ser valido y contener warnings.

## Seguridad

El endpoint valida primero que el proyecto pertenezca al usuario autenticado.

Un proyecto ajeno conserva la semantica 404 utilizada por el resto del modulo.