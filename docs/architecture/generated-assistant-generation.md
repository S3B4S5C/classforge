# CU-19 — Generated application Assistant

**Estado:** CERRADO con Ciclo 8.

CU-19 genera en cada exportación API un Assistant de datos compartido por Angular y Flutter. Los antiguos CU-20..23 quedan absorbidos por este caso para mantener un único pipeline de lenguaje natural/voz.

## Arquitectura

```text
Angular chat/mic ─┐
                  ├─> Spring generado /api/assistant/*
Flutter chat/mic ─┘            |
                               +-> whisper.cpp :8093 -> transcript
                               +-> Qwen/llama.cpp :8092 native tools
                                           |
                                           v
                              grounding/validación Java
                                           |
                                  Domain Manifest 1.0
                                           |
                         QUERY directo / mutación preview+apply
```

Los clientes nunca contactan directamente Whisper ni llama.cpp. El backend generado reutiliza por defecto las mismas instancias locales de ClassForge y permite override mediante `LLAMA_URL`, `LLAMA_MODEL`, `WHISPER_URL`, `WHISPER_LANGUAGE` y `APP_API_BASE_URL`.

## Native tool calling

El patrón replica la autoridad del Assistant principal sin crear una dependencia runtime hacia ClassForge:

1. `route_data_request` clasifica la petición;
2. se expone una tool pesada compatible con el intent;
3. Qwen devuelve native `tool_call`;
4. Java resuelve entidad/campo/relación contra metadata derivada de Domain Manifest;
5. Java valida capacidades, tipos, filtros, writeability, sensibilidad y unicidad de selectores; las PK autogenerables llegan como `createWritable=false` y nunca se piden al LLM en CREATE, salvo una PK `String` seleccionada explícitamente como credencial Auth;
6. solo entonces se ejecuta o se crea preview.

Intents: `QUERY`, `COUNT`, `GET`, `CREATE`, `UPDATE`, `DELETE`, `SET_RELATION`, `ADD_RELATION`, `REMOVE_RELATION`.

## Seguridad y privacidad

`QUERY/COUNT/GET` pueden ejecutarse inmediatamente. Toda mutación produce un resumen sanitizado y un token opaco con TTL de 10 minutos. El comando pendiente permanece exclusivamente en memoria del Spring generado; `/api/assistant/apply` consume el token y ejecuta. El cliente no recibe el comando crudo y los campos `sensitive`, como password, se representan como `***`.

En Auth se reenvía `Authorization: Bearer` al API generado. Resoluciones ambiguas o referencias inexistentes fallan cerradas; el LLM no selecciona arbitrariamente registros.

## Voz

Angular produce WAV PCM mono 16 kHz mediante Web Audio. Flutter usa `record` con `AudioEncoder.wav`, 16 kHz mono y permiso Android `RECORD_AUDIO`. Ambos suben el WAV a `/api/assistant/voice`; Whisper solo añade la etapa de transcripción y el texto resultante entra al mismo planner.

## Acceptance

`generatedAssistantAcceptance` genera Simple/Auth dos veces, exige proyecto/ZIP deterministas, valida privacidad y compila/testea ambos backends exportados. Las regresiones CU-18/CU-17 validan además Flutter APK y Angular production build con los clientes Assistant incluidos.
