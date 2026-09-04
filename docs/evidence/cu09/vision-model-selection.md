# CU-09 — evidencia de selección del VLM

**Fecha:** 29 de agosto de 2026.

## Decisión

Runtime visual seleccionado: **Qwen3-VL-4B-Instruct Q4_K_M** sobre llama.cpp/Vulkan, puerto 8094, `--parallel 1`, contexto recomendado 6144.

La selección es técnica y no equivale todavía a `CU-09 CERRADO`; falta estabilizar la pizarra real y ejecutar acceptance completa.

## Evidencia 4B

- regression: semantic exact 100 %, schema 100 %, grounding 100 %, safety 100 %;
- holdout: semantic exact 100 %, clases/atributos/relaciones/multiplicidades 100 %, safety 100 %;
- hardening sintético previo a la pizarra: todos los casos completados PASS exacto;
- pizarra real focal, 3200 completion tokens / timeout 180 s: 2/2 respuestas transportadas y schema-valid; ambas llegaron a grounding;
- pico GPU observado en la corrida focal: 5464 MiB.

La pizarra reveló errores restantes de grounding/evidencia y topología, no de transporte/schema. Cal-006 los aborda sin relajar fail-closed.

## Evidencia 2B

- pizarra real focal, 4000 completion tokens / timeout 180 s: 2/2 respuestas terminaron `OUTPUT_CONTRACT` por truncamiento `max_tokens`;
- pico GPU observado: 4417 MiB.

El 2B queda descartado como runtime principal para CU-09 por no completar de forma estable el caso realista denso con el budget probado.

## Riesgo y criterio restante

El 4B debe todavía demostrar que sigue físicamente las conexiones de diagramas densos, preserva multiplicidades y no infiere tipos ausentes. Después de volver verde `library-whiteboard-realistic`, se repetirán regression + holdout + hardening y finalmente `assistant-vision-acceptance.ps1`.


## Evidencia posterior a Cal-006

La corrida focal del 4B después de Cal-006 confirmó que el caso realista ya atraviesa transporte, schema y grounding al 100 % con 3200 completion tokens / 180 s y un pico observado de 5462 MiB.

Lectura semántica observada:

- clases visibles: 6/6 detectadas; `Categoría`/`Préstamo` difieren del oracle únicamente por diacríticos;
- atributos visibles: todos los atributos esperados quedaron presentes y los tipos no explícitos permanecieron en `STRING`;
- topología de asociaciones: 6 de 7 relaciones esperadas estuvieron presentes si se trata `ASSOCIATION` como no dirigida;
- relación faltante: `Usuario — Libro`;
- relación espuria: `Categoría — Préstamo`;
- multiplicidades de la pizarra: omitidas (`null`) en la propuesta;
- `Biblioteca ◇— Libro` fue reconocido correctamente como `AGGREGATION`.

Cal-007 no cambia el VLM seleccionado ni relaja validaciones. Corrige el comparador del benchmark para aplicar la misma normalización conservadora de diacríticos del backend y considerar las asociaciones sin dirección, manteniendo GENERALIZATION/AGGREGATION/COMPOSITION direccionales. Además agrega métricas parciales por elemento para que un único caso denso no aparezca engañosamente como 0 % cuando existe reconocimiento parcial.

La calibración de prompt se limita a trazado físico de líneas y una segunda inspección local de multiplicidades. El objetivo siguiente sigue siendo dejar verde `library-whiteboard-realistic` antes de ejecutar acceptance completa.

## Evidencia posterior a Cal-007 y decisión Cal-008

La corrida focal post-Cal-007 confirmó una regresión específica del prompt monolítico: clases y atributos permanecieron en 100 %, pero el refuerzo de trazado dentro de la misma inferencia hizo que el 4B sobreinterpretara conectores como `AGGREGATION 0..1 ↔ 0..1`. La topología cayó respecto a Cal-006. Por tanto Cal-008 conserva las mejoras del comparador de Cal-007, pero revierte únicamente ese refuerzo relacional del prompt principal al comportamiento conservador de Cal-006.

Cal-008 introduce una segunda inferencia `relationships-only` para diagramas densos. La primera pasada sigue detectando clases y atributos; si hay al menos cuatro clases y `dense-two-pass-enabled=true`, el mismo Qwen3-VL-4B recibe otra vez la imagen junto con una lista cerrada `ref = clase` y un schema reducido que solo admite relaciones, multiplicidades, warnings y confidence. La segunda pasada no puede crear clases ni usar refs que no hayan sido confirmados en la primera; cualquier ref extraño, JSON truncado o contrato inválido se rechaza fail-closed.

El merge conserva clases/atributos de la primera pasada y reemplaza exclusivamente `relationships` con la salida relationships-only. No existe una nueva ruta de mutación: el `VisionUmlProposal` resultante continúa por grounding, evidence bounds, compiler, semantic plan, resolver, preview y Command Bus existentes.

El runner focal permite comparar `single-pass` y `two-pass` sobre la misma pizarra y guarda reportes separados por modelo/modo. Qwen3-VL-4B Q4_K_M continúa siendo el VLM seleccionado; CU-09 permanece EN PROGRESO hasta validar la pizarra con two-pass y repetir acceptance completa.


## Evidencia post-Cal-008 y decisión Cal-009

El modo two-pass de Cal-008 no supera el mejor single-pass previo sobre `library-whiteboard-realistic`: las clases/atributos permanecen correctos, pero la segunda pasada produjo aproximadamente 4/7 relaciones esperadas por intento, 1 relación espuria y 0 multiplicidades recuperadas. Se descarta por tanto como default de producción y `dense-two-pass-enabled` vuelve a `false`.

La hipótesis siguiente es de escala/encuadre, no de capacidad textual: la foto original está girada, incluye marco/márgenes y las multiplicidades ocupan pocos píxeles. Cal-009 añade estrategias benchmark-only `board-crop` y `tiles` para medir esa hipótesis sin volver a modificar el prompt ni introducir un merge visual en el runtime. El 4B sigue siendo el modelo seleccionado; el resultado de crop/tiling decidirá si vale la pena diseñar un preprocesamiento visual permanente antes de acceptance.

## Cal-010 — decisión híbrida después de los experimentos densos

El 4B permanece seleccionado. Cal-007 (prompt geométrico), Cal-008 (relationships-only global) y Cal-009 (board-crop) no superaron el mejor single-pass en la topología de la pizarra. Esto desplaza la calibración desde "más prompt" a separación de percepción geométrica y semántica.

Cal-010 evalúa OpenCV CPU para derivar únicamente pares físicamente conectados y usa Qwen3-VL-4B sobre una hoja de evidence local para clasificar marker/multiplicidades. La anotación local no puede inventar endpoints. Esta estrategia es experimental y no modifica el VLM seleccionado ni habilita todavía el cierre de CU-09.
