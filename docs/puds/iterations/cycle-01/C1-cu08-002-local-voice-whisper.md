# C1 — CU08-002 Voz local con whisper.cpp

**Ciclo:** 1  
**Caso:** CU-08  
**Incremento:** 002

## Objetivo

Activar la entrada de voz real usando STT local y reutilizar exactamente el planner de CU08-001.

## Pipeline

```text
micrófono
 -> WAV PCM mono 16 kHz
 -> Spring multipart
 -> whisper-server
 -> transcript
 -> AssistantPlanService source=VOICE
 -> mismo planner de texto
 -> preview
 -> Aplicar
```

## Reglas

- Whisper solo transcribe;
- el audio no modifica `ProjectDocument`;
- transcript y plan pueden diagnosticarse;
- errores no producen mutaciones;
- máximo inicial de grabación: 20 s;
- runtime por defecto: `127.0.0.1:8093`.
