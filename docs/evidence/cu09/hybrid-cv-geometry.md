# CU-09 Cal-010 — híbrido CV geometry + local VLM annotation

**Estado:** experimental; no cierra CU-09.

## Motivación

La evidencia acumulada separa con claridad dos capacidades:

- Qwen3-VL-4B: clases/atributos y semántica local de UML;
- topología densa: líneas largas/cruzadas y multiplicidades pequeñas siguen siendo el frente inestable.

Los experimentos de prompt geométrico, segunda pasada global y crop completo no mejoraron de forma consistente el mejor single-pass. Cal-010 prueba una separación explícita de responsabilidades: CV para geometría, VLM para semántica y Java para autoridad/fail-closed.

## Inspiración técnica

- FlowExtract: suprime elementos y reconstruye conexiones con visión clásica/Hough en vez de delegar todos los edges al VLM.
- ReSECDI: agrupa rectángulos UML y reconstruye relaciones con merging de líneas poligonales y tratamiento de símbolos degradados.
- pipelines de circuitos manuscritos: separan componentes, junctions/cables y conectividad antes de producir la estructura final.
- IMG2XML: usa crops de alta resolución para detalles pequeños, reforzando la idea de anotar evidencia local en vez de pedir lectura global de todo el diagrama.

Cal-010 toma esas ideas, pero evita añadir YOLO, HAWP, PyTorch, OCR externo o otro runtime GPU.

## Gate inicial

Antes de promover `dense-hybrid.enabled=true` se exige inspeccionar el caso `library-whiteboard-realistic` y sus overlays. El primer objetivo es geometría: pares correctos sin extras. Sólo después se evalúa marker/multiplicidades y se repite regression/holdout/hardening/acceptance.

## Cal-011 — corrección de frontera espacial

La primera corrida real de Cal-010 mostró que la etapa `Qwen cerrado -> bbox` no es fiable: los bboxes producidos no coincidieron con las cajas físicas. Cal-011 sustituye esa etapa por `OpenCV -> B1..Bn` y limita al VLM a un mapeo biyectivo `Bx -> classRef` sin coordenadas. Ver `docs/evidence/cu09/cv-first-class-regions.md`.
