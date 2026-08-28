# ClassForge — Variantes de botones

## Objetivo

Los fondos principales de ClassForge son claros, por lo que los botones de accion no deben depender del color por defecto de Angular Material.

Se definen dos variantes globales.

### Primary

Clase:

`cf-button-primary`

Uso:

- accion principal de una pantalla;
- crear;
- guardar;
- confirmar;
- iniciar un flujo importante.

Apariencia:

- fondo indigo solido;
- texto blanco;
- contraste alto;
- hover mas oscuro.

### Secondary

Clase:

`cf-button-secondary`

Uso:

- accion alternativa;
- navegacion contextual;
- reintentar;
- volver;
- acceso secundario.

Apariencia:

- fondo indigo muy claro;
- texto indigo oscuro;
- borde visible.

## Regla de jerarquia

Una zona de acciones deberia tener normalmente una sola accion Primary.

Ejemplo:

```html
<a mat-flat-button class="cf-button-primary">Crear cuenta</a>
<a mat-button class="cf-button-secondary">Iniciar sesion</a>
```

## Responsive

La variante no fija anchos. Los layouts siguen siendo responsables de decidir si los botones ocupan ancho completo en pantallas pequenas.

El alto minimo aumenta a 44 px en mobile para mejorar la superficie tactil.