# Ciclo 2 — Colaboración real y entrada visual

**Fase PUDS:** Elaboración  
**Estado:** ABIERTO  
**Inicio:** 28 de agosto de 2026

## Objetivo

Cerrar dos riesgos todavía presentes en la arquitectura ejecutable:

1. convertir la colaboración de multisesión owner-only en colaboración entre cuentas reales;
2. demostrar que una imagen/fotografía puede converger de forma segura en el mismo modelo UML canónico.

## Casos del ciclo

| Caso | Estado |
|---|---|
| CU-31 Membresía e invitaciones | CERRADO |
| CU-09 Imagen a UML | PLANIFICADO EN ESTE CICLO |

CU-10 y CU-11 de interoperabilidad XMI/Enterprise Architect se difieren deliberadamente al ciclo final del proyecto.

## Orden

```text
CU-31
  -> C2-cu31-001 membership + política OWNER/EDITOR       [COMPLETADO]
  -> C2-cu31-002 invitaciones internas + UI               [COMPLETADO]
  -> C2-cu31-003 hardening realtime/presencia/Assistant   [COMPLETADO]

CU-09
  -> entrada de imagen
  -> interpretación visual
  -> semantic plan / BATCH / preview
  -> cierre Ciclo 2
```

## Riesgos principales

- autorización consistente entre REST, STOMP, presencia y Assistant;
- conservar `Project.ownerId` como ownership explícito;
- migración aditiva sin destruir H2;
- invitaciones persistentes sin depender de SMTP o Internet;
- evitar duplicados/races en membresía e invitaciones;
- evitar que CU-09 cree una segunda fuente de verdad.

## Estado después de C2-cu31-003

Ya existen:

- `ProjectMembership` EDITOR persistente;
- política central OWNER/EDITOR/NONE;
- invitación interna por correo normalizado;
- bandeja de invitaciones pendientes;
- aceptar/rechazar/cancelar invitaciones;
- creación transaccional de membership al aceptar;
- diálogo de Colaboradores en el workspace.

CU-31 queda formalmente CERRADO: OWNER y EDITOR colaboran entre cuentas distintas en realtime/presencia, Assistant respeta membership, NONE se rechaza y las carreras de invitaciones quedan serializadas por proyecto.

## Criterio de cierre

El Ciclo 2 no se cierra con CU-31. Permanece ABIERTO y CU-09 — Imagen → UML — es el siguiente caso.
