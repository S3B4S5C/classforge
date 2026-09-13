# C6-cu17-002 — Recovery del validator para template literals TypeScript

## Problema detectado

El primer gate focal de CU-17 reveló una interacción con el hardening heredado de CU-13. `GeneratedProjectValidator` interpreta `${...}` como una posible expresión FreeMarker sin resolver en todos los archivos de texto. El frontend Angular generado usa legítimamente template literals de TypeScript, por ejemplo:

```ts
`${this.endpoint}/count`
```

Por ello un frontend válido podía ser rechazado antes de llegar a `generatedAngularAcceptance`.

## Corrección

La detección de directivas FreeMarker (`<#...>`, `<@...>`, etc.) se conserva para todos los archivos. La detección de expresiones `${...}` deja de aplicarse únicamente a `frontend/**/*.ts`, donde esa sintaxis pertenece a JavaScript/TypeScript en runtime.

El cambio no relaja la validación del backend, Gradle, YAML, Markdown ni otros artefactos generados.

## Regresión

`GeneratedProjectValidatorTests` verifica simultáneamente que:

- un template literal TypeScript bajo `frontend/**/*.ts` es aceptado;
- `${model.basePackage}` en un artefacto generado no-TypeScript continúa produciendo `UNRESOLVED_TEMPLATE_MARKER`.

CU-17 solo puede cerrarse si este recovery y todos los gates del incremento quedan GREEN.
