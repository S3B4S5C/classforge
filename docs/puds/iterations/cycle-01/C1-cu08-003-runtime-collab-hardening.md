# C1 — CU08-003 Runtime y hardening colaborativo

**Ciclo:** 1  
**Caso:** CU-08  
**Incremento:** 003  
**Estado:** cierre de CU-08

## Objetivo

Cerrar CU-08 asegurando disponibilidad de runtimes y evitando aplicar previews sobre una revisión distinta.

## Resultado

- `GET /api/projects/{id}/assistant/health`;
- health de llama.cpp y whisper.cpp;
- texto requiere llama;
- voz requiere llama + whisper;
- bloqueo durante pending/resync/conflict;
- bloqueo de draft local divergente;
- `baseRevision` comprobada al recibir y al aplicar;
- stale plan descartado ante cambio remoto;
- diagnósticos `stage/source/transcript/attemptedPlan`;
- BATCH conserva atomicidad y una revisión.

CU-08 queda cerrado.
