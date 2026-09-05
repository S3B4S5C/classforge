# C2-cu09-002 — Runtime Qwen3-VL y benchmark visual

**Caso de uso:** CU-09 — Crear UML desde imagen/fotografía  
**Estado final:** COMPLETADO  
**Cierre funcional integrado en CU-09:** 5 de septiembre de 2026

## Objetivo

Conectar un VLM local real al contrato de C2-cu09-001 y construir evidencia reproducible para seleccionar modelo, medir image -> UML y endurecer el pipeline antes de promoverlo a producto.

## Runtime seleccionado

La evaluación comenzó con Qwen3-VL-2B y Qwen3-VL-4B. El modelo seleccionado fue:

```text
Qwen3-VL-4B-Instruct Q4_K_M
mmproj Q8_0
llama.cpp multimodal
alias: vision-model
puerto: 8094
parallel: 1
contexto recomendado: 6144
```

El planner textual de CU-08 permanece separado en 8092 y whisper.cpp en 8093.

## Motivo de selección

El 4B demostró mejor comportamiento en la pizarra real densa. El 2B truncó 2/2 intentos focales aun con 4000 completion tokens; el 4B completó respuestas estructuradas con 3200 tokens y mantuvo aproximadamente 5.4 GiB de VRAM en la máquina local.

La evidencia inicial del 4B incluyó regression y holdout con 100 % semantic exact/safety antes de abordar la pizarra real. La pizarra reveló que el riesgo restante no era transporte/schema, sino topología y multiplicidades.

## Contrato multimodal

`LlamaCppVisionModelGateway` usa `/v1/chat/completions` con imagen Base64 y salida JSON constrained. La respuesta debe respetar schema cerrado; no existe reparación heurística de JSON. Timeout, HTTP error, truncamiento o contrato inválido fallan en etapa `VISION`.

## Benchmark

Las suites separan:

- `regression`: fixtures conocidos;
- `holdout`: ejemplos no usados para el ajuste inicial;
- `hardening`: casos difíciles y la pizarra realista;
- safety para imágenes no UML;
- métricas por clases, atributos, relaciones y multiplicidades.

La evolución del benchmark terminó incorporando además un **executable gate**: para la estrategia de producto `original`, un plan no puede considerarse Exact si no puede resolver un BATCH, producir preview y pasar `ProjectDocumentValidator`.

## Evolución de la calibración

El modelo seleccionado se mantuvo estable; lo que cambió fue la distribución de autoridad:

1. prompt monolítico: suficiente para clases/atributos, inestable en conectividad densa;
2. segunda pasada global de relaciones: no mejoró topología/multiplicidades;
3. crop/tiles benchmark-only: no resolvió el problema central;
4. híbrido CV + VLM: separó geometría física de semántica;
5. Cal-011/012/013 cerraron class regions, topología, tipos y multiplicidades.

## Evidencia focal final

`library-whiteboard-realistic`, 3 intentos consecutivos:

```text
3/3 PASS Exact
Transport        100.0 %
Schema valid     100.0 %
Grounding        100.0 %
Classes          18/18, 0 unexpected
Attributes       51/51, 0 unexpected
Relationships    18/18, 0 unexpected
Multiplicity     30/30, 0 unexpected
Semantic exact   100.0 %
Safety invalid   100.0 %
GPU peak         5455 MiB
```

Esta corrida mide estabilidad sobre el fixture focal, no generalización estadística sobre pizarras arbitrarias.

## Resultado final

C2-cu09-002 queda completado porque el runtime fue seleccionado, el benchmark se volvió reproducible y la información obtenida permitió cerrar la arquitectura híbrida. La historia detallada de selección/modelo está en `docs/evidence/cu09/vision-model-selection.md`; la evolución geométrica está en `docs/evidence/cu09/hybrid-cv-geometry.md`.
