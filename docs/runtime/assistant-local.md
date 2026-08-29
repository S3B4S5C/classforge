# Runtime local del Asistente UML — CU08 cerrado

ClassForge no embebe los modelos dentro de Spring. Usa dos procesos locales persistentes.

## llama.cpp

Puerto esperado:

```text
127.0.0.1:8092
```

Ejemplo para NVIDIA/Vulkan:

```powershell
llama-server.exe `
  -hf ggml-org/gemma-3-4b-it-GGUF:Q4_K_M `
  --no-mmproj `
  --device Vulkan0 `
  -ngl all `
  --parallel 1 `
  --host 127.0.0.1 `
  --port 8092 `
  --alias local-model `
  -c 4096
```

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
