# C2 — CU31 fix-001 — Feedback de invitaciones y health verificable

## Estado

**COMPLETADO como corrección post-cierre de CU-31.**

CU-31 permanece CERRADO. El Ciclo 2 permanece ABIERTO y CU-09 sigue siendo el siguiente caso de uso.

## Motivo

Después del cierre funcional de CU-31 se detectaron dos problemas de observabilidad:

1. el OWNER no recibía confirmación explícita ni el motivo concreto al intentar invitar;
2. una cuenta con `/projects` ya abierto no refrescaba su bandeja hasta recargar la página;
3. el health del Assistant podía conservar un snapshot verde o aceptar como runtime correcto otro proceso que respondiera un `/health` genérico.

## Correcciones

- feedback de éxito con correo normalizado persistido;
- propagación del `message` estructurado del backend en errores de invitación;
- distinción de errores de red;
- refresco silencioso de invitaciones cada 10 segundos en la biblioteca;
- health de llama.cpp validado con `/health` + `/v1/models`;
- health de whisper.cpp validado con `/health` + identidad `whisper.cpp`;
- estado `MISMATCH` si el puerto responde pero corresponde a otro servicio;
- refresco de health cada 30 segundos únicamente cuando el Assistant está inactivo.

## Frontera

No se añade email real, notificación push ni nuevo topic STOMP para invitaciones. La persistencia y la autorización de CU-31 no cambian.
