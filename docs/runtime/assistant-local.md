# Runtime local del Asistente UML — CU08 cerrado

ClassForge no embebe los modelos dentro de Spring. Usa dos procesos locales persistentes.

## llama.cpp

Puerto esperado:

```text
127.0.0.1:8092
```

Runtime oficial del Assistant desde `C2-cu08-fix-014`:

```powershell
llama-server.exe `
  -hf bartowski/Qwen2.5-3B-Instruct-GGUF:Q4_K_M `
  --device Vulkan0 `
  --parallel 1 `
  --host 127.0.0.1 `
  --port 8092 `
  --alias local-model `
  -c 4096 `
  --jinja
```

El modelo 3B Q4_K_M fue elegido porque entra cómodamente en la GTX 1660 SUPER de referencia y sostuvo ~50-56 tokens/s durante las pruebas, mientras Qwen 7B hacía spill de VRAM. `--jinja` es obligatorio para el contrato native tools.

ClassForge verifica `/props` y exige `supports_tools=true` y `supports_tool_calls=true`.

Health oficial:

```text
GET http://127.0.0.1:8092/health
```

## whisper.cpp

Puerto esperado:

```text
127.0.0.1:8093
```

Ejemplo:

```powershell
whisper-server.exe `
  -m "F:\whisper\models\ggml-base.bin" `
  -l es `
  --host 127.0.0.1 `
  --port 8093 `
  -t 6 `
  -nfa `
  -nt
```

`-nfa` evita la ruta Flash Attention problemática observada durante las pruebas en GTX 1660 SUPER. `-nt` evita trabajo de timestamps que ClassForge no consume.

Health oficial:

```text
GET http://127.0.0.1:8093/health
```

## Pre-demo

Antes de abrir el caso de uso:

1. iniciar llama-server;
2. iniciar whisper-server;
3. iniciar Spring;
4. iniciar Angular;
5. abrir un proyecto;
6. comprobar que el panel muestra ambos runtimes en estado listo.

Texto requiere llama.cpp.

Voz requiere llama.cpp + whisper.cpp.
## Diagnóstico de puertos

El indicador verde ya no depende únicamente de `/health`. ClassForge verifica además la identidad del proceso esperado. Si otro proceso ocupa 8092 o 8093 y responde un health genérico, el panel mostrará `MISMATCH` en lugar de READY.

En Windows, si aparece `MISMATCH`, conviene comprobar qué proceso escucha el puerto antes de iniciar los runtimes locales:

```powershell
Get-NetTCPConnection -LocalPort 8092,8093 -State Listen |
  Select-Object LocalPort, OwningProcess
```

Después puede inspeccionarse el proceso con `Get-Process -Id <PID>`.

<!-- C2-CU09-001-VISION-RUNTIME -->
## Runtime visual CU-09

C2-cu09-001 incorpora `VisionModelGateway`, pero no fija aún un VLM de producción. El bean fallback `UnconfiguredVisionModelGateway` devuelve un error explícito `VISION` para evitar que el sistema simule análisis visual con el modelo textual. C2-cu09-002 elegirá el VLM local por benchmark antes de documentar un comando de arranque definitivo.

<!-- C2-CU09-002-QWEN3-VL-RUNTIME -->
## Runtime visual seleccionado — Qwen3-VL-4B Q4_K_M

CU09 mantiene planner textual y Vision en procesos distintos:

```text
8092 -> Qwen2.5-3B-Instruct Q4_K_M / native tools
8093 -> whisper.cpp
8094 -> Qwen3-VL-4B-Instruct Q4_K_M / VisionModelGateway
```

Tras comparar 2B y 4B, el runtime visual seleccionado es:

```text
Qwen3-VL-4B-Instruct
Q4_K_M
llama.cpp / Vulkan0
contexto recomendado: 6144
parallel: 1
```

Arranque directo desde Hugging Face:

```powershell
llama-server.exe `
  -hf Qwen/Qwen3-VL-4B-Instruct-GGUF:Q4_K_M `
  --device Vulkan0 `
  --parallel 1 `
  --host 127.0.0.1 `
  --port 8094 `
  --alias vision-model `
  -c 6144 `
  --fit on `
  --fit-target 768
```

Variables de ClassForge:

```text
CLASSFORGE_ASSISTANT_VISION_PROVIDER=llama-cpp
CLASSFORGE_ASSISTANT_VISION_URL=http://127.0.0.1:8094
CLASSFORGE_ASSISTANT_VISION_MODEL=vision-model
CLASSFORGE_ASSISTANT_VISION_TIMEOUT_SECONDS=180
CLASSFORGE_ASSISTANT_VISION_MAX_TOKENS=3200
```

`GET /api/projects/{projectId}/assistant/health` solo marca Vision READY cuando `/health` está listo, `/v1/models` contiene `vision-model` bajo llama.cpp y `/props` informa `modalities.vision=true`.

Smoke real de transporte multimodal:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-smoke.ps1
```

### Evidencia de selección 2B vs 4B

Con el mismo pipeline/schema y GTX 1660 SUPER de 6 GiB:

- 4B: regression 100 % semantic exact, holdout 100 %, safety 100 %; pizarra real completó 2/2 JSON válidos con 3200 tokens/180 s; pico observado ~5464 MiB.
- 2B: en la pizarra real truncó 2/2 por `max_tokens` incluso con 4000 completion tokens; pico observado ~4417 MiB.

Por ello CU09-Cal-006 congela el 4B como runtime visual seleccionado. El 2B se conserva solo como evidencia comparativa; no se seguirá calibrando como candidato principal.

### Caso rápido de pizarra real

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-whiteboard.ps1 `
  -Attempts 2 `
  -ModelLabel "Qwen3-VL-4B Q4_K_M" `
  -TimeoutSeconds 180 `
  -MaxCompletionTokens 3200 `
  -VerboseAttempts
```

Ejecuta únicamente `library-whiteboard-realistic` y conserva un reporte separado por modelo. `finish_reason=length` sigue siendo `OUTPUT_CONTRACT`; JSON truncado nunca se repara y grounding continúa fail-closed.

<!-- C2-CU09-003-CALIBRATION-AFTER-IMPLEMENTATION -->
## CU09-003 — calibración después de terminar la implementación

El parche CU09-003 no arranca ni benchmarkea automáticamente Qwen3-VL. El build normal mantiene deshabilitadas las pruebas que requieren un runtime visual real. Esto permite terminar primero el código y después iterar modelo/prompt de forma controlada.

Exploración sin gates de calidad:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-explore.ps1 `
  -Attempts 2 `
  -ModelLabel "Qwen3-VL candidate" `
  -VerboseAttempts
```

También puede ejecutarse únicamente hardening:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-hardening.ps1 -Attempts 2 -VerboseAttempts
```

Cuando modelo/prompt estén estabilizados, la aceptación estricta es:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-acceptance.ps1 `
  -Attempts 2 `
  -ModelLabel "modelo seleccionado" `
  -VerboseAttempts
```

Por defecto exige semantic exact `regression >= 95 %`, `holdout >= 85 %`, `hardening >= 75 %`, schema/safety 100 %, E2E real de persistencia y regresión de CU-08. Solo después de esa evidencia debe actualizarse la documentación de estado a `CU-09: CERRADO`.

## Vision dense input experiments (CU09-Cal-009)

La corrida focal post-Cal-008 confirmó que la segunda inferencia `relationships-only` no mejora la pizarra real: conserva clases/atributos, pero reduce la topología útil y sigue sin recuperar multiplicidades. Por ello `dense-two-pass-enabled` vuelve a `false` por defecto. El código experimental permanece disponible con `CLASSFORGE_ASSISTANT_VISION_TWO_PASS=true`, pero no forma parte del runtime seleccionado.

La calibración cambia ahora la **entrada visual** sin modificar prompt, grounding ni compiler. El runner focal acepta tres estrategias exclusivamente para `library-whiteboard-realistic`:

```text
original    = fotografía original sin transformación
board-crop  = rota la foto 90° CCW cuando es vertical y recorta marco/márgenes
tiles       = board-crop dividido en 4 regiones solapadas y ampliadas 1.5x
```

`board-crop` sigue ejecutando una sola inferencia normal. `tiles` ejecuta cuatro inferencias single-pass y combina **solo dentro del benchmark** sus firmas semánticas para medir si el aumento de escala recupera relaciones/multiplicidades; este merge no existe en producción ni puede mutar `ProjectDocument`.

Prueba rápida recomendada:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-whiteboard.ps1 `
  -ImageStrategy board-crop `
  -Attempts 2 `
  -ModelLabel "Qwen3-VL-4B Q4_K_M" `
  -TimeoutSeconds 180 `
  -MaxCompletionTokens 3200 `
  -VerboseAttempts
```

Si `board-crop` mejora claramente frente a la foto original, puede probarse después:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-whiteboard.ps1 `
  -ImageStrategy tiles `
  -Attempts 1 `
  -ModelLabel "Qwen3-VL-4B Q4_K_M" `
  -TimeoutSeconds 180 `
  -MaxCompletionTokens 3200 `
  -VerboseAttempts
```

`tiles` requiere `VisionMode=single-pass`. Los reportes incluyen estrategia y modo en el nombre, por ejemplo `whiteboard-qwen3-vl-4b-q4-k-m-board-crop-single-pass.json`.

## Vision hybrid CV — producción

Cal-010 agrega OpenCV 4.9 mediante `org.openpnp:opencv:4.9.0-0`. La primera compilación necesita descargar aproximadamente 110 MiB desde Maven Central; no se instala Python, CUDA adicional ni un segundo modelo. El native se carga de forma perezosa únicamente al ejecutar el modo híbrido.

Configuración por defecto:

```text
CLASSFORGE_ASSISTANT_VISION_HYBRID=true
CLASSFORGE_ASSISTANT_VISION_HYBRID_MIN_CLASSES=4
CLASSFORGE_ASSISTANT_VISION_HYBRID_FALLBACK=true
CLASSFORGE_ASSISTANT_VISION_HYBRID_LOCALIZATION_TOKENS=1200
CLASSFORGE_ASSISTANT_VISION_HYBRID_RELATIONSHIP_TOKENS=512
CLASSFORGE_ASSISTANT_VISION_HYBRID_MULTIPLICITY_TOKENS=128
```

Prueba focal recomendada con el 4B ya levantado en 8094:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-whiteboard.ps1 `
  -VisionMode hybrid-cv `
  -ImageStrategy original `
  -Attempts 2 `
  -ModelLabel "Qwen3-VL-4B Q4_K_M" `
  -TimeoutSeconds 180 `
  -MaxCompletionTokens 3200 `
  -HybridLocalizationTokens 1200 `
  -HybridRelationshipTokens 512 `
  -HybridMultiplicityTokens 128 `
  -VerboseAttempts
```

Los artefactos de diagnóstico se escriben en:

```text
backend/build/reports/assistant-vision/geometry/library-whiteboard-realistic/
  localization.json
  geometry.json
  annotation.json
  threshold.png
  segments.png
  overlay.png
  relationship-sheet.png
```

El kill switch `CLASSFORGE_ASSISTANT_VISION_HYBRID=false` fuerza semantic-only. Los diagramas con menos de cuatro clases siguen semantic-only; `fallback-to-semantic=true` se conserva. El semantic pass usa `CLASSFORGE_ASSISTANT_VISION_MAX_TOKENS=3200`; mapping, relationship y multiplicity usan 1200, 512 y 128 respectivamente.
