# C6-cu17-004 — Recovery de compatibilidad TypeScript 6

**Estado:** Recovery de CU-17.

## Causa

El acceptance de build Angular generado alcanzó por primera vez `ng build` y TypeScript 6 rechazó dos opciones heredadas del `tsconfig.json` generado:

- `baseUrl`;
- `downlevelIteration`.

Con `target: ES2022`, `moduleResolution: bundler` e imports relativos, ninguna de las dos opciones es necesaria para el frontend generado.

Angular también reportó `NG8102` sobre expresiones `filters['campo'] ?? ''`; eran warnings, no el bloqueo del build.

## Corrección

CU-17 elimina `baseUrl` y `downlevelIteration` del `tsconfig.json` generado en vez de silenciar la deprecación con `ignoreDeprecations`.

También elimina el `?? ''` redundante de los inputs de filtro para mantener el template limpio bajo `strictTemplates`.

## Regresión

`AngularFrontendRenderingTests` exige que:

- el `tsconfig.json` generado no contenga `baseUrl`;
- no contenga `downlevelIteration`;
- los list components no vuelvan a emitir `?? ''` sobre `Record<string, string>`.

El cierre de CU-17 sigue condicionado a que `generatedAngularAcceptance` compile en producción los frontends Simple y Auth, además de todas las regresiones CU-13..16.
