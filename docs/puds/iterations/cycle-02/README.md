# Evidencia técnica — Ciclo 2

**Fase:** Elaboración  
**Estado:** ABIERTO

## Casos

- CU-31 — colaboración entre cuentas reales: CERRADO;
- CU-09 — entrada visual al modelo canónico: EN PROGRESO.

## Incrementos de CU-31

1. `C2-cu31-001-membership-access.md` — COMPLETADO.
2. `C2-cu31-002-invitations-ui.md` — COMPLETADO.
3. `C2-cu31-003-realtime-membership-hardening.md` — COMPLETADO.

## Incrementos de CU-09

1. `C2-cu09-001-image-input-and-vision-contract.md` — COMPLETADO como infraestructura contractual.
2. `C2-cu09-002-qwen3-vl-runtime-and-visual-benchmark.md` — IMPLEMENTADO; evidencia benchmark local pendiente.
3. `C2-cu09-003-vision-ux-hardening-and-e2e-closure.md` — IMPLEMENTADO; validación real del VLM y aceptación final pendientes.

## Correcciones post-cierre de CU-31

- `C2-cu31-fix-001-invitation-feedback-runtime-health.md`: feedback de invitaciones, refresco de bandeja y health de runtimes con verificación de identidad. CU-31 permanece CERRADO; CU-09 sigue siendo el siguiente CU.
- `C2-cu31-fix-002-invitation-submit.md`: corrige el submit nativo del diálogo de colaboradores para que Angular envíe realmente el POST de invitación y conserve el feedback en pantalla. CU-31 permanece CERRADO; CU-09 sigue siendo el siguiente CU.

- `C2-cu08-fix-009-semantic-resolution-benchmark.md`: experimento post-cierre para canonicalización tolerante de referencias UML y benchmark raw-vs-final del Assistant. CU-31 permanece CERRADO y CU-09 sigue siendo el siguiente CU.

<!-- CU08-FIX-013-NATIVE-TOOLS -->
## Hardening del Assistant antes de CU-09

- `C2-cu08-fix-013-native-tool-calling-foundation.md`: introduce native tools detrás de un modo experimental y benchmark A/B contra legacy. CU-08 permanece CERRADO y CU-09 continúa siendo el siguiente CU funcional.

<!-- CU08-FIX-014 -->
- `C2-cu08-fix-014-native-tools-cutover.md`: native tools oficial, multi-tool, retiro del planner legacy y evidencia previa a CU-09.

<!-- CU08-FIX-014-V1.6-FINAL-HOLDOUT -->
## Evidencia final del hardening previo a CU-09

La corrida holdout posterior a fix-014 v1.6 cerró `33/33 = 100.0 %`, con safety y multi-tool en `100 %` y build verde. C2-cu09-001 se abre sobre esa base; la regression post-v1.6 de 20 intentos por categoría queda como confirmación recomendada de no regresión del Assistant textual.

<!-- C2-CU09-001 -->
C2-cu09-001 abre formalmente CU-09 reutilizando la IR y el Command Bus de CU-08. No hay VLM definitivo aún y ninguna imagen puede mutar `ProjectDocument` fuera de preview/BATCH/Apply.

<!-- C2-CU09-002 -->
C2-cu09-002 incorpora el adapter llama.cpp multimodal, health específico de Vision y datasets regression/holdout. El baseline es Qwen3-VL-2B Q4_K_M + mmproj Q8_0 y el 4B queda como challenger. El incremento no se considera completado hasta adjuntar una corrida local reproducible con safety 100 % y métricas de precisión/VRAM/latencia.


<!-- C2-CU09-003-IMPLEMENTED -->
## C2-cu09-003 — implementación completa antes de calibración

El tercer incremento termina la superficie funcional de Imagen → UML y deja preparada la evidencia de cierre. Incorpora UX de preparación de imagen, evidence overlay, estados `NO_CHANGES`/`NO_ACTIONABLE_UML`, hardening dataset, modo exploratorio, E2E real y agregador de aceptación.

Por decisión de implementación, CU09-003 no fuerza todavía una corrida Qwen3-VL: los tests deterministas y builds pueden quedar verdes con gateway fake, mientras que smoke/regression/holdout/hardening/E2E se ejecutarán después para iterar modelo y prompt. CU-09 permanece `EN PROGRESO` hasta que `assistant-vision-acceptance.ps1` produzca aceptación verde.
