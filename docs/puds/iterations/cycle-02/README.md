# Evidencia técnica — Ciclo 2

**Fase:** Elaboración  
**Estado:** ABIERTO

## Casos

- CU-31 — colaboración entre cuentas reales: CERRADO;
- CU-09 — entrada visual al modelo canónico: SIGUIENTE.

## Incrementos de CU-31

1. `C2-cu31-001-membership-access.md` — COMPLETADO.
2. `C2-cu31-002-invitations-ui.md` — COMPLETADO.
3. `C2-cu31-003-realtime-membership-hardening.md` — COMPLETADO.

Después se abrirán los incrementos técnicos de CU-09.

## Correcciones post-cierre de CU-31

- `C2-cu31-fix-001-invitation-feedback-runtime-health.md`: feedback de invitaciones, refresco de bandeja y health de runtimes con verificación de identidad. CU-31 permanece CERRADO; CU-09 sigue siendo el siguiente CU.
- `C2-cu31-fix-002-invitation-submit.md`: corrige el submit nativo del diálogo de colaboradores para que Angular envíe realmente el POST de invitación y conserve el feedback en pantalla. CU-31 permanece CERRADO; CU-09 sigue siendo el siguiente CU.

- `C2-cu08-fix-009-semantic-resolution-benchmark.md`: experimento post-cierre para canonicalización tolerante de referencias UML y benchmark raw-vs-final del Assistant. CU-31 permanece CERRADO y CU-09 sigue siendo el siguiente CU.

<!-- CU08-FIX-013-NATIVE-TOOLS -->
## Hardening del Assistant antes de CU-09

- `C2-cu08-fix-013-native-tool-calling-foundation.md`: introduce native tools detrás de un modo experimental y benchmark A/B contra legacy. CU-08 permanece CERRADO y CU-09 continúa siendo el siguiente CU funcional.

<!-- CU08-FIX-014 -->
- `C2-cu08-fix-014-native-tools-cutover.md`: native tools oficial, multi-tool, retiro del planner legacy y evidencia previa a CU-09.
