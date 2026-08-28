# ClassForge — Iconografia

## Decision

ClassForge utiliza **Material Symbols Rounded**.

Los iconos se instalan mediante el paquete npm `material-symbols` y se sirven desde el propio frontend.

No se enlaza `fonts.googleapis.com` ni `fonts.gstatic.com`.

Esto mantiene:

- consistencia con Angular Material;
- disponibilidad offline;
- una sola familia visual;
- soporte responsive sin depender de SVGs individuales.

## Convencion

```html
<span class="material-symbols-rounded" aria-hidden="true">
  save
</span>
```

Los iconos decorativos utilizan `aria-hidden="true"` y siempre se acompanan de texto cuando la accion no es universalmente obvia.

## Ejes

ClassForge usa por defecto:

- FILL 0;
- weight 500;
- grade 0;
- optical size 24.

La clase `is-filled` activa FILL 1 cuando se necesita enfatizar un estado.

La clase `is-spinning` se reserva para acciones en progreso como guardado/sincronizacion.