# Ciclo 1 — Estabilización de la arquitectura ejecutable

**Fase PUDS:** Elaboración  
**Estado:** CERRADO  
**Corte:** 28 de agosto de 2026

## Objetivo

Construir un incremento ejecutable que validara tempranamente los riesgos arquitectónicos de ClassForge:

- modelo UML canónico;
- persistencia y revisión;
- diagramación web;
- Command Bus;
- colaboración distribuida;
- presencia;
- IA local;
- Speech-to-Text local;
- voz como adaptador del mismo modelo.

## Relación con la planificación inicial

El plan original dividía estas capacidades en varias iteraciones de Elaboración y Construcción.

La ejecución real adelantó colaboración e IA/STT para reducir riesgos antes de iniciar generadores.

```text
plan inicial
  -> varios incrementos separados

ejecución real del Ciclo 1
  -> vertical slice arquitectónico más amplio
  -> validación temprana de riesgos
```

No se reescribe la historia para simular una planificación perfecta. La planificación original permanece en `../history/`.

## Casos de uso incluidos

```text
CU-01
CU-02
CU-03
CU-04
CU-05
CU-06
CU-07
CU-08
CU-28
CU-29
CU-30
```

CU-24 y CU-25 se adelantaron como infraestructura técnica requerida por CU-08.

## Incrementos técnicos

La evidencia granular se encuentra en `../iterations/cycle-01/`.

```text
fundación auth/ownership
CU01 proyecto
CU02 documento y revisión
CU03-001 dominio UML
CU03-002 canvas JointJS
CU03-003 relaciones e inspector
CU04-001 validación
CU05-001 Command Bus + Undo/Redo
CU06-001 autoridad STOMP backend
CU06-002 sincronización Angular
CU06-003 Undo/Redo colaborativo
CU07-001 presencia
CU08-001 texto + semantic plan + BATCH
CU08-002 micrófono + whisper.cpp
CU08-003 runtime health + stale-plan + hardening
```

## Riesgos reducidos

### R1 — Modelo canónico insuficiente

Resultado: `ProjectDocument` separa `UmlModel` y `DiagramLayout`; JointJS permanece como proyección.

### R2 — Mutaciones por múltiples rutas

Resultado: Command Bus reutilizado por UI y Assistant; colaboración transporta operaciones.

### R3 — Conflictos realtime

Resultado: servidor autoritativo, `baseRevision`, resync y rechazo de operaciones obsoletas.

### R4 — IA local demasiado lenta o inviable

Resultado: Gemma 3 4B Q4_K_M validada con llama.cpp; el runtime puede usar CPU o aceleración disponible.

### R5 — IA produce cambios inválidos

Resultado: schema estructurado, grounding, normalización, resolver Java, preview y validador.

### R6 — STT local

Resultado: whisper.cpp recibe WAV PCM mono 16 kHz producido por navegador y retorna transcript.

## Criterios de salida alcanzados

- el modelo sobrevive recarga y guardado;
- UML manual funciona;
- relaciones y multiplicidades funcionan;
- validación protege el documento;
- Undo/Redo funciona local y colaborativamente;
- dos sesiones convergen mediante revisión autoritativa;
- presencia no contamina el modelo persistido;
- texto puede producir un BATCH UML;
- voz puede producir el mismo contrato semántico;
- un plan IA no se aplica sin confirmación;
- cambios remotos invalidan previews obsoletos;
- health de llama/whisper es visible;
- errores del Assistant pueden informar etapa, transcript y plan intentado.

## Limitaciones que pasan a ciclos posteriores

- membresías/invitaciones;
- imagen a UML;
- XMI;
- modelo relacional;
- generadores;
- app generada;
- voz sobre app generada;
- auditoría persistente;
- demo final reproducible;
- diagramas UML académicos completos.

## Resultado del Ciclo 1

El Ciclo 1 no entrega todavía el producto objetivo completo. Entrega una **arquitectura ejecutable validada** sobre la cual pueden construirse los generadores e integraciones posteriores sin cambiar la fuente de verdad ni introducir una segunda ruta de mutación.
