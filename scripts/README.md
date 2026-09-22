# Scripts

Aquí irán scripts de automatización del monorepo cuando aparezca una necesidad real (arranque conjunto, empaquetado de modelos locales, generación de ejemplos, etc.).

<!-- ASSISTANT-RELIABILITY-BENCHMARK -->
## Assistant reliability benchmark

`scripts/assistant-e2e-reliability.ps1` ejecuta repetidamente el planner local sin aplicar cambios al proyecto y compara el plan raw del LLM con el plan final después de resolución semántica.

Ejemplo:

```powershell
pwsh -NoProfile -File .\scripts\assistant-e2e-reliability.ps1 `
  -Attempts 50 `
  -Prompt "Crea una asociacion entre 4nimal y mascota" `
  -Classes Animal,Mascota `
  -ExpectedSource Animal `
  -ExpectedTarget Mascota
```

El runtime llama.cpp debe estar levantado en `http://127.0.0.1:8092` o indicarse mediante `-LlamaUrl`.

<!-- CU08-FIX-010-ASSISTANT-E2E-RELIABILITY -->
## Assistant E2E reliability benchmark

`scripts/assistant-e2e-reliability.ps1` levanta el contexto Spring de test y usa el endpoint HTTP real `POST /api/projects/{id}/assistant/plan` contra llama.cpp. El fixture crea un proyecto veterinario mediano con 12 clases, atributos y 9 relaciones. Cada tipo de operación se repite `N` veces alternando prompts limpios, errores tipográficos, transposiciones, mayúsculas, ruido conversacional y referencias inexistentes.

Ejemplo rápido:

```powershell
pwsh -NoProfile -File .\scripts\assistant-e2e-reliability.ps1 -Attempts 5
```

Prueba más estable:

```powershell
pwsh -NoProfile -File .\scripts\assistant-e2e-reliability.ps1 `
  -Attempts 20 `
  -VerboseAttempts
```

La tabla final muestra `APPROVAL`, `FAILURE` y `STATUS` por categoría. Una categoría queda `ERROR` cuando su porcentaje de fallos es estrictamente mayor a 60 %; cualquier fallo menor o igual a ese umbral se marca `WARN`. Si existe al menos una categoría `ERROR`, la tarea Gradle termina con código distinto de cero.

Este benchmark requiere llama.cpp levantado. No aplica el preview al proyecto, por lo que todos los intentos usan el mismo estado UML.

<!-- CU08-FIX-014 -->
## Benchmarks Assistant native tools

```powershell
pwsh -NoProfile -File .\scripts\assistant-tool-regression.ps1 -Attempts 20 -VerboseAttempts
pwsh -NoProfile -File .\scripts\assistant-tool-holdout.ps1 -Attempts 3 -VerboseAttempts
```

`regression` repite la matriz que autorizó el cutover; `holdout` usa redacciones nuevas y una petición compuesta. El antiguo runner A/B se retiró junto con el planner legacy.

<!-- C2-CU09-001-VISION-CONTRACT -->
## Vision contract

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-contract.ps1
```

Ejecuta las regresiones de validación de imagen, evidence/provenance, compiler visual e integración membership/revision. No requiere un VLM real.

<!-- C2-CU09-002-VISION-BENCHMARK -->
## Vision runtime smoke y benchmark

Con llama.cpp multimodal levantado en `127.0.0.1:8094` con alias `vision-model`:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-smoke.ps1
```

El smoke valida `/health`, identidad en `/v1/models`, `modalities.vision=true` en `/props` y una inferencia Base64 real con JSON Schema.

Regression:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-regression.ps1 `
  -Attempts 2 `
  -VerboseAttempts
```

Holdout:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-holdout.ps1 `
  -Attempts 2 `
  -VerboseAttempts
```

Ambos wrappers usan `assistant-vision-benchmark.ps1`, que ejecuta la tarea Gradle `assistantVisionBenchmark`. Los reportes se guardan por defecto en:

```text
backend/build/reports/assistant-vision/regression.json
backend/build/reports/assistant-vision/holdout.json
```

Métricas: transporte, schema válido, grounding, clases, atributos, relaciones, multiplicidades, semantic exact, safety de imagen inválida, p50/p95 y `gpuPeakMiB` cuando `nvidia-smi` está disponible.

Para comparar un challenger puede mantenerse el alias `vision-model` y cambiar únicamente el modelo cargado, usando `-ModelLabel` para identificar el reporte.



### Vision: pizarra real aislada (CU09-CAL-004/005)

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-whiteboard.ps1 `
  -Attempts 2 `
  -ModelLabel "Qwen3-VL-4B Q4_K_M" `
  -TimeoutSeconds 180 `
  -MaxCompletionTokens 3200 `
  -VerboseAttempts
```

Este wrapper filtra `library-whiteboard-realistic` dentro del benchmark hardening real. No duplica el pipeline. CU09-CAL-005 hace configurables `-TimeoutSeconds` y `-MaxCompletionTokens`, usa 180 s / 3200 tokens como defaults focales y genera un reporte separado por `ModelLabel`, por ejemplo `whiteboard-qwen3-vl-4b-q4-k-m.json`. Para comparar el 2B sin sobrescribir evidencia puede usarse `-ModelLabel "Qwen3-VL-2B Q4_K_M" -MaxCompletionTokens 4000`.

Con `-VerboseAttempts`, un `GROUNDING_REJECT` imprime y conserva en el reporte el `VisionUmlProposal` ya parseado antes del grounding. Esto permite inspeccionar evidence/provenance sin relajar el validator ni reparar la salida del modelo.

<!-- C2-CU09-003-VISION-CLOSURE-TOOLING -->
## Vision CU09-003 — explorar primero, aceptar después

El parche CU09-003 **no ejecuta automáticamente** suites que dependan de un VLM real. Solo el contrato determinista forma parte de la validación del parche.

Para iterar modelo/prompt sin convertir cada experimento en un gate:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-explore.ps1 -Attempts 2 -VerboseAttempts
```

`explore` ejecuta regression, holdout y hardening con thresholds 0 y conserva los reportes para comparar candidatos. `assistant-vision-hardening.ps1` permite ejecutar sólo las degradaciones visuales.

E2E real aislado:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-e2e.ps1
```

Comprueba smoke multimodal y `imagen -> plan -> command -> ProjectDocument persistido -> reopen` mediante `AssistantVisionAcceptanceIntegrationTest`.

Cuando el VLM ya esté calibrado:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-acceptance.ps1 -Attempts 2 -VerboseAttempts
```

La aceptación agrega regression >=95 %, holdout >=85 %, hardening >=75 %, schema/safety 100 %, E2E real y regression CU-08. Produce `backend/build/reports/assistant-vision/cu09-acceptance.json`. Sigue siendo la forma recomendada de generar una corrida agregada. El cierre funcional de CU-09 del 5 de septiembre de 2026 quedó documentado en `docs/evidence/cu09/cu09-acceptance.json`; la ejecución archivada post-Cal-017 de este agregador quedó explícitamente diferida y no debe presentarse como realizada.

### Pizarra real: original vs crop vs tiles (CU09-Cal-009)

Cal-008 deja de ser el comportamiento por defecto: el two-pass relacional no mejoró la topología de la pizarra. `assistant-vision-whiteboard.ps1` vuelve a `single-pass` y añade `-ImageStrategy original|board-crop|tiles`.

```powershell
# fotografía original
pwsh -NoProfile -File .\scripts\assistant-vision-whiteboard.ps1 -ImageStrategy original -Attempts 2 -ModelLabel "Qwen3-VL-4B Q4_K_M" -VerboseAttempts

# foto orientada + recortada al área útil de pizarra (recomendado primero)
pwsh -NoProfile -File .\scripts\assistant-vision-whiteboard.ps1 -ImageStrategy board-crop -Attempts 2 -ModelLabel "Qwen3-VL-4B Q4_K_M" -VerboseAttempts

# cuatro regiones solapadas ampliadas; más lento, benchmark-only
pwsh -NoProfile -File .\scripts\assistant-vision-whiteboard.ps1 -ImageStrategy tiles -Attempts 1 -ModelLabel "Qwen3-VL-4B Q4_K_M" -VerboseAttempts
```

`board-crop` y `tiles` solo se habilitan para `library-whiteboard-realistic`. `tiles` ejecuta cuatro inferencias single-pass y fusiona firmas semánticas únicamente en el benchmark; no introduce ningún merge visual ni ruta de mutación en producción. Los reportes se separan por modelo, estrategia y modo.

### Pizarra real: hybrid-cv (CU09-Cal-010)

Cal-014 activa hybrid-CV en producción para diagramas densos. Este benchmark conserva selección explícita de modo para comparar `single-pass`, `two-pass` y `hybrid-cv`: Qwen conserva clases/atributos, OpenCV CPU reconstruye candidatos geométricos y una inferencia local anota tipo/multiplicidades.

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

`hybrid-cv` exige `ImageStrategy=original`. El benchmark guarda diagnósticos de localización/geometría/evidence sheet bajo `backend/build/reports/assistant-vision/geometry/<caseId>/`. La primera descarga de la dependencia OpenCV empaquetada ronda 110 MiB.

### Cal-011 — validar sólo cajas/mapping/geometría híbrida

Para depurar primero la frontera CV-first sin ejecutar la anotación VLM de relaciones:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-whiteboard.ps1 `
  -VisionMode hybrid-cv `
  -HybridGeometryOnly `
  -ImageStrategy original `
  -Attempts 1 `
  -ModelLabel "Qwen3-VL-4B Q4_K_M" `
  -TimeoutSeconds 180 `
  -MaxCompletionTokens 3200 `
  -HybridLocalizationTokens 1200 `
  -VerboseAttempts
```

Inspeccionar `backend/build/reports/assistant-vision/geometry/library-whiteboard-realistic/` y, en particular, `class-regions.png`, `mapping.json`, `geometry.json` y `overlay.png`.


## CU-27 — demo reproducible final

Los scripts de CU-27 ya no abren ni cierran Spring/Angular. Flujo mínimo:

```powershell
.\scripts\demo-reset.ps1

# Consola 1
cd backend
.\gradlew.bat bootRun --no-daemon --args="--spring.profiles.active=demo"

# Consola 2
cd frontend
npm start

# Tercera consola, desde la raíz
.\scripts\demo-smoke.ps1 -RequireAi
.\scripts\demo-acceptance.ps1
```

`demo-stop.ps1` no termina procesos: Spring y Angular se detienen con `Ctrl+C` en sus consolas. `demo-ea-smoke.ps1 -RepositoryPath <repo-desechable>` ejecuta el round-trip XMI real contra la Automation Interface de Enterprise Architect. Ver `GUIA-DEMO-CU27.md` y `docs/runtime/cu27-demo.md`.


## AWS demo deployment bundle

Para construir el artefacto reproducible que se instala en EC2:

```powershell
.\scripts\aws-build-deploy-bundle.ps1
```

Ejecuta tests backend, characterization tests frontend, `bootJar` y `ng build`, y empaqueta JAR + Angular + Nginx/systemd/IAM/env templates en `classforge-aws-deploy-bundle.zip`. `-SkipTests` existe solo para reconstrucciones posteriores a un gate ya validado. Ver `docs/runtime/aws-demo-deployment.md`.
