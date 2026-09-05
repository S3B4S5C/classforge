# Evidencia técnica — Ciclo 2

**Fase:** Elaboración  
**Estado:** CERRADO  
**Cierre:** 5 de septiembre de 2026

## Casos

- CU-31 — colaboración entre cuentas reales: CERRADO.
- CU-09 — entrada visual al modelo canónico: CERRADO.

## Incrementos de CU-31

1. `C2-cu31-001-membership-access.md` — COMPLETADO.
2. `C2-cu31-002-invitations-ui.md` — COMPLETADO.
3. `C2-cu31-003-realtime-membership-hardening.md` — COMPLETADO.

## Incrementos de CU-09

1. `C2-cu09-001-image-input-and-vision-contract.md` — COMPLETADO: entrada visual y convergencia al pipeline canónico.
2. `C2-cu09-002-qwen3-vl-runtime-and-visual-benchmark.md` — COMPLETADO: runtime Qwen3-VL-4B seleccionado y benchmark reproducible.
3. `C2-cu09-003-vision-ux-hardening-and-e2e-closure.md` — COMPLETADO: UX, hybrid-CV, fail-closed, E2E, canonicalización y smoke final.

## Calibraciones de CU-09

La secuencia de calibración que terminó formando la arquitectura productiva fue:

```text
Cal-011 class regions
Cal-012 physical topology
Cal-013 per-edge annotation + multiplicities
Cal-014 production activation
Cal-015 fail-closed policy
Cal-016 canonical product E2E
Cal-017 identifier canonicalization + executable gate
```

Cal-010 se conserva como antecedente superseded de la primera frontera híbrida VLM->bbox.

## Evidencia principal

- `docs/evidence/cu09/cu09-closure-report.md`
- `docs/evidence/cu09/cu09-acceptance.json`
- `docs/evidence/cu09/vision-model-selection.md`
- `docs/evidence/cu09/cv-first-class-regions.md`
- `docs/evidence/cu09/hybrid-cv-geometry.md`

## Resultado

El Ciclo 2 cierra con ambos riesgos objetivo resueltos sobre la misma autoridad `ProjectDocument`/Command Bus. La validación multi-pizarra adicional queda registrada como riesgo residual aceptado, no como evidencia ejecutada.
