# whisper.cpp local para CU08-002

ClassForge usa `whisper-server` como runtime STT persistente.

## Puerto esperado

```text
127.0.0.1:8093
```

Spring invoca:

```text
POST /inference
multipart field: file
response_format: json
language: es
```

## Modelo recomendado inicial

Para español debe usarse un modelo multilingue, no un modelo `.en`.

Perfil sugerido:

```text
tiny   -> laptop lenta / menor latencia
base   -> default de demostracion
small  -> mayor precision si el hardware lo permite
```

## Ejemplo Windows

Desde el directorio que contiene `whisper-server.exe`:

```powershell
whisper-server.exe `
  -m ".\models\ggml-base.bin" `
  -l es `
  --host 127.0.0.1 `
  --port 8093 `
  -t 6 `
  -nfa `
  -nt
```

El frontend ya envia WAV PCM. No se requiere `--convert` ni ffmpeg para el flujo normal de ClassForge.

El puerto 8093 es independiente del `llama-server` de CU08-001, que permanece en 8092.