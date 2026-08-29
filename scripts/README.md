# Scripts

Aquí irán scripts de automatización del monorepo cuando aparezca una necesidad real (arranque conjunto, empaquetado de modelos locales, generación de ejemplos, etc.).

<!-- ASSISTANT-RELIABILITY-BENCHMARK -->
## Assistant reliability benchmark

`scripts/assistant-reliability.ps1` ejecuta repetidamente el planner local sin aplicar cambios al proyecto y compara el plan raw del LLM con el plan final después de resolución semántica.

Ejemplo:

```powershell
pwsh -NoProfile -File .\scripts\assistant-reliability.ps1 `
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

<!-- CU08-FIX-013-NATIVE-TOOLS -->
## Benchmark A/B del Assistant

Con Qwen2.5-Instruct iniciado en `llama-server` con `--jinja`, `assistant-tool-ab.ps1` ejecuta el mismo benchmark HTTP E2E dos veces: primero con `planner-mode=legacy` y después con `planner-mode=tools`. Los reportes JSON quedan bajo `backend/build/reports/assistant-tool-ab/` y la consola muestra aprobación por categoría, delta y regla safety fail-closed.

```powershell
pwsh -NoProfile -File .\scripts\assistant-tool-ab.ps1 -Attempts 5 -VerboseAttempts
```
