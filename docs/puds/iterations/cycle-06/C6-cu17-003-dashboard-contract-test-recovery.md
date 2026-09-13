# C6-cu17-003 — Recovery del contrato focal del dashboard

## Contexto

Después de C6-cu17-002, el renderer Angular ya supera la validación de marcadores FreeMarker. El gate focal llegó entonces a una aserción heredada del propio test de CU-17.

El dashboard generado modela cada tarjeta con un `endpoint` canónico y construye el conteo en tiempo de ejecución mediante un template literal TypeScript:

```ts
{ name: 'Usuario', endpoint: '/api/usuario', ... }
this.http.get(`${entity.endpoint}/count`)
```

El test esperaba incorrectamente encontrar el literal concatenado `/api/usuario/count` dentro del archivo generado. Ese literal no forma parte del diseño del renderer.

## Recuperación

Se actualiza `AngularFrontendRenderingTests` para validar el contrato real y más estable:

1. la metadata del dashboard contiene `endpoint: '/api/usuario'`;
2. el conteo usa `` `${entity.endpoint}/count` ``;
3. se mantienen todas las demás comprobaciones de componentes específicos, Auth y color primario.

No se modifica la implementación productiva del dashboard.

## Resultado esperado

El gate focal debe dejar de fallar por una concatenación textual que el renderer no promete y continuar hacia `generatedAngularAcceptance`, donde Simple y Auth se compilan como aplicaciones Angular reales.
