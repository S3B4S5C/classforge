# C2-cu09-002 — Qwen3-VL runtime and visual benchmark

**Caso de uso:** CU-09 — Crear UML desde imagen/fotografía  
**Fase PUDS:** Elaboración  
**Estado del incremento:** IMPLEMENTADO; evidencia benchmark local pendiente

## Objetivo

Conectar un VLM local real a la frontera creada en C2-cu09-001 sin introducir una segunda ruta de mutación y construir una suite reproducible que mida image→UML antes de cerrar CU-09.

## Baseline visual

El runtime de referencia para iniciar la evaluación es:

```text
Qwen3-VL-2B-Instruct
LLM GGUF:    Q4_K_M
mmproj GGUF: Q8_0
llama.cpp:   puerto 8094
alias:       vision-model
```

El 4B Q4_K_M se conserva como challenger. El resultado de CU09-002 no se considera cerrado hasta ejecutar regression + holdout en la GTX 1660 SUPER de referencia y documentar precisión, latencia y memoria.

El planner textual de CU-08 permanece independiente en `8092`; whisper.cpp permanece en `8093`.

## Pipeline

```text
Imagen normalizada
       ↓
VisionModelGateway
       ↓
LlamaCppVisionModelGateway
       ↓
llama.cpp :8094 / Qwen3-VL
       ↓
JSON Schema constrained output
       ↓
VisionUmlProposal
       ↓
VisionProposalGroundingValidator
       ↓
VisionProposalCompiler
       ↓
AssistantSemanticPlan
       ↓
UmlAssistantCommandResolver
       ↓
BATCH → Preview → Apply → Command Bus → ProjectDocument
```

No existe escritura directa del VLM sobre `ProjectDocument`.

## Contrato multimodal

`LlamaCppVisionModelGateway` usa `POST /v1/chat/completions` con:

- bloque `text` con instrucciones UML;
- bloque `image_url` como data URL Base64;
- `response_format.type=json_object + schema`;
- schema cerrado con `additionalProperties=false`;
- temperatura 0;
- thinking deshabilitado para este adapter;
- timeout y límite de completion configurables.

La salida se deserializa directamente a `VisionUmlProposal`. No hay extracción por regex, limpieza de fences ni reparación heurística. HTTP error, timeout, truncamiento o JSON inválido fallan cerrados en etapa `VISION`.

## Prompt y contexto

El prompt visual no reutiliza el prompt native-tools de CU-08. Ordena transcribir solo símbolos visibles, omitir información incierta y no inventar clases, atributos, relaciones ni multiplicidades.

El contexto de proyecto enviado al VLM contiene solo nombres de clases y atributos. `projectId`, UUID de clases y revisión no se serializan al prompt. El contexto sirve para resolver referencias existentes, nunca como evidencia visual.

## Health multimodal

`GET /api/projects/{projectId}/assistant/health` incorpora:

```text
readyForImage
vision
```

Vision queda READY únicamente si:

1. `/health` responde `status=ok`;
2. `/v1/models` publica el alias configurado bajo llama.cpp;
3. `/props` publica `modalities.vision=true`.

Un llama.cpp textual levantado accidentalmente en 8094 queda `INCOMPATIBLE` o `MISMATCH`, no READY.

## Provenance

C2-cu09-001 ya exige evidence para clases, atributos y relaciones. CU09-002 endurece bounding boxes: si el VLM decide informar geometría debe enviar `x`, `y`, `width` y `height` completos; un bbox parcial se rechaza.

## Benchmark

Se incorporan dos datasets:

```text
assistant/vision/benchmark/regression.json
assistant/vision/benchmark/holdout.json
```

Regression reutiliza fixtures contractuales de 001. Holdout añade imágenes no usadas por esos tests, incluyendo:

- tres clases y dos asociaciones;
- clase existente con atributo nuevo;
- generalización;
- composición;
- multiplicidad `1` / `0..*`;
- imagen rotada;
- bajo contraste;
- dos clases sin relación dibujada;
- imagen no UML.

El benchmark compara el `AssistantSemanticPlan` resultante contra firmas semánticas esperadas. Reporta por separado:

- éxito de transporte;
- JSON/schema válido;
- grounding aceptado;
- exactitud de clases;
- exactitud de atributos;
- exactitud de relaciones;
- exactitud de multiplicidades;
- exactitud semántica total;
- safety de imágenes inválidas;
- latencia p50/p95;
- pico de memoria GPU observado por `nvidia-smi`, cuando está disponible.

Los reportes se escriben en `backend/build/reports/assistant-vision/`.

## Scripts

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-smoke.ps1
pwsh -NoProfile -File .\scripts\assistant-vision-regression.ps1 -Attempts 2 -VerboseAttempts
pwsh -NoProfile -File .\scripts\assistant-vision-holdout.ps1 -Attempts 2 -VerboseAttempts
```

El smoke comprueba una imagen Base64 real; no considera suficiente que `/health` responda.

## Criterio de selección

Baseline: Qwen3-VL-2B-Instruct Q4_K_M + mmproj Q8_0.

Challenger: Qwen3-VL-4B-Instruct Q4_K_M + mmproj Q8_0.

El 4B solo sustituye al 2B si produce una mejora material de precisión sin degradar de forma problemática VRAM, latencia o coexistencia con el planner textual. La evidencia de la máquina local decide; no se selecciona por tamaño nominal.

## Definition of Done pendiente de evidencia

El código y la instrumentación quedan implementados por este parche. Para declarar C2-cu09-002 **COMPLETADO** aún debe existir una corrida local que demuestre:

- smoke multimodal verde;
- regression y holdout guardados como reporte;
- `schemaValidPercent=100`;
- safety de imágenes inválidas `100 %`;
- métricas de precisión revisadas;
- VRAM/latencia registradas cuando la GPU exponga `nvidia-smi`;
- texto y voz de CU-08 sin regresiones con Vision cargado;
- `clean build` backend y build frontend verdes.

Hasta entonces CU-09 permanece EN PROGRESO y C2-cu09-003 no debe cerrar el caso.
