# C2 — CU31 fix-002 — Submit reactivo de invitaciones

## Estado

**COMPLETADO como corrección post-cierre de CU-31.**

CU-31 permanece CERRADO. El Ciclo 2 permanece ABIERTO y CU-09 continúa como siguiente caso de uso.

## Problema observado

El diálogo de colaboradores renderizaba un `<form>` con `(ngSubmit)="invite()"`, pero el formulario no estaba asociado a `FormGroup` y el componente solo importaba `ReactiveFormsModule`. En esa combinación no existía una directiva de formulario que emitiera `ngSubmit` y cancelara el submit HTML nativo.

El efecto visible era:

```text
OWNER pulsa Invitar
→ el navegador hace submit nativo
→ la página se recarga
→ invite() no ejecuta el POST
→ no aparece feedback
→ no se persiste ProjectInvitation
→ la otra cuenta no puede recibir nada
```

La persistencia backend y `GET /api/project-invitations` no eran la causa: la petición de creación nunca salía del navegador.

## Corrección

El formulario de invitación pasa a ser un formulario reactivo real:

```text
FormGroup inviteForm
└── email: FormControl

<form [formGroup]="inviteForm" (ngSubmit)="invite()">
<input formControlName="email">
```

De este modo Angular intercepta el submit, evita la navegación nativa y ejecuta `invite()` tanto al pulsar el botón como al presionar Enter. El feedback de éxito/error introducido en fix-001 permanece visible y la bandeja de la cuenta invitada continúa refrescándose cada 10 segundos.

## Frontera

No se modifica backend, modelo de dominio, membresías, STOMP ni revisión UML. Esta corrección únicamente repara el enlace del formulario frontend con el flujo de invitaciones ya implementado.
