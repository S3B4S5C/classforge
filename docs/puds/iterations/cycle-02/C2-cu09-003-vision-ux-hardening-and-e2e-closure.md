# C2-cu09-003 — UX, hardening, E2E y cierre de Imagen -> UML

**Caso de uso:** CU-09 — Crear UML desde imagen/fotografía  
**Estado final:** COMPLETADO  
**Fecha de cierre:** 5 de septiembre de 2026

## Objetivo

Completar la experiencia de producto y cerrar los riesgos que permanecían después de conectar el VLM: preparación de imagen, estados seguros, geometría densa, multiplicidades, política de fallo, aplicabilidad del plan, concurrencia y persistencia canónica.

## UX entregada

- selector de archivo;
- drag & drop;
- clipboard;
- cámara compatible;
- rotación;
- crop conservador;
- reset;
- cancelación/retry;
- evidence overlay cuando existe geometría;
- `READY`, `NO_CHANGES` y `NO_ACTIONABLE_UML`;
- `command=null` cuando no hay cambios accionables;
- botón `Analizar nuevamente` ante fallo de Vision.

La preparación de imagen no muta `ProjectDocument`.

## Arquitectura final del análisis visual

```text
imagen validada/normalizada
  -> semantic first pass Qwen3-VL
  -> si <4 clases: semantic-only
  -> si >=4 clases:
       OpenCV class regions B1..Bn
       -> closed Bx -> classRef mapping
       -> OpenCV physical relationship topology
       -> per-edge type/marker classification
       -> per-endpoint conditioned transcription
       -> explicit connector attribution
       -> Java multiplicity parser
  -> VisionUmlProposal
  -> code identifier canonicalization
  -> grounding/compiler
  -> AssistantSemanticPlan
  -> BATCH + preview
```

### Autoridad física

OpenCV/Java posee la topología física del modo híbrido. El VLM puede clasificar un edge ya confirmado y leer texto local, pero no añadir o eliminar arbitrariamente pares físicos.

### Multiplicidades

La solución final no usa reglas semánticas como “aggregation no tiene multiplicidad”. Cada endpoint se procesa visualmente. Cuando varios conectores inciden cerca de una clase, Java condiciona el crop suprimiendo evidencia exclusiva de competidores; si existe un label, una segunda inferencia lo atribuye explícitamente a un edge. Java sólo acepta la multiplicidad si `owner == current edgeId` y luego la parsea determinísticamente.

## Calibraciones principales

| Cal | Resultado |
|---|---|
| 011 | class regions físicas reconstruidas; VLM deja de producir bbox |
| 012 | 6 pares físicos reales en la pizarra focal, 0 extras |
| 013 | anotación per-edge y multiplicidades; 3/3 Exact |
| 014 | hybrid-CV productivo por defecto para >=4 clases |
| 015 | fail-closed por defecto; fallback semántico sólo rollback/debug |
| 016 | E2E por autoridad colaborativa real |
| 017 | canonicalización de nombres visuales + executable plan gate |

## Política fail-closed

Producción usa:

```text
hybrid enabled = true
minClasses = 4
fallback-to-semantic = false
```

Si el routing ya seleccionó hybrid-CV y una etapa falla:

- `TRANSPORT` se conserva como `VISION/TRANSPORT`;
- error contractual/interno se representa como `VISION/OUTPUT_CONTRACT`;
- no se devuelve la propuesta semántica inicial;
- no existe preview ni Apply;
- la UI permite reintentar.

Los diagramas con menos de cuatro clases usan semantic-only por estrategia, no por fallback.

## Cal-016 — E2E canónico

Los tests integrados prueban el flujo real:

```text
AssistantImagePlanService.plan
 -> READY + BATCH + baseRevision
 -> preview del mismo BATCH
 -> ProjectOperationRequest
 -> ProjectCollaborationService.apply
 -> ProjectCommandExecutor
 -> validate
 -> save
 -> reopen
```

Se verificó:

- `plan()` no persiste;
- preview == documento persistido/reabierto;
- BATCH con múltiples hijos => exactamente `revision + 1`;
- stale plan => `REVISION_CONFLICT`;
- stale rejection => 0 mutación parcial assistant;
- cambio de revisión durante Vision => no `READY`.

El acceptance con VLM real quedó cableado a la misma autoridad de Apply, aunque no se ejecutó como corrida final archivada en este corte.

## Cal-017 — identificadores visuales vs. dominio

El smoke real detectó una brecha que el benchmark semántico no veía: Qwen podía leer correctamente `Categoría`, `Préstamo` y `añoPublicacion`, pero `ProjectDocumentValidator` exige identificadores `^[A-Za-z_][A-Za-z0-9_]*$`.

La corrección preserva dos verdades distintas:

```text
EVIDENCIA VISUAL        IDENTIFICADOR DEL MODELO
Categoría               Categoria
Préstamo                Prestamo
añoPublicacion           anoPublicacion
```

La adaptación ocurre determinísticamente en la frontera del compiler; no se restringe la transcripción del VLM y no se relaja el dominio. El benchmark añadió un executable gate para evitar que un plan semánticamente exacto sea declarado válido si no puede producir un preview aceptado por el dominio.

## Smoke manual final

Los cinco checks de producto fueron ejecutados y aprobados:

1. **Happy path:** la misma pizarra real del benchmark llegó a `READY`, se previsualizó, aplicó y persistió tras reload/reopen.
2. **Preparación de imagen:** selector/drag & drop y controles rotate/crop/reset/cancel no mutaron el proyecto antes de Apply.
3. **Fail-closed + retry:** con Vision detenido no hubo preview ni mutación; `Analizar nuevamente` permitió recuperarse al restaurar el runtime.
4. **Stale plan:** un plan generado sobre una revisión vieja fue rechazado y pudo regenerarse.
5. **Permisos:** OWNER y EDITOR pudieron usar el flujo; un usuario sin edición no pudo mutar el proyecto.

## Evidencia de benchmark focal

Después de fix-011, antes del smoke de canonicalización, el fixture `library-whiteboard-realistic` obtuvo 3/3 Exact con todas las métricas estructurales al 100 %. Cal-017 cerró posteriormente la brecha de aplicabilidad descubierta por la UI.

## Validación adicional diferida

Se había propuesto medir al menos dos pizarras reales adicionales para obtener un porcentaje de generalización. Esa campaña no se ejecutó antes de la decisión de cierre y no se presenta como evidencia realizada. También quedó sin una ejecución archivada post-Cal-017 del agregador completo `assistant-vision-acceptance.ps1`.

La decisión de cierre acepta explícitamente ese riesgo residual porque el caso de uso funcional, su seguridad de mutación, concurrencia, persistencia y smoke real quedaron demostrados.

## Estado PUDS final

```text
C2-cu09-001: COMPLETADO
C2-cu09-002: COMPLETADO
C2-cu09-003: COMPLETADO
CU-09: CERRADO
Ciclo 2: CERRADO
```

La evidencia detallada está en `docs/evidence/cu09/cu09-closure-report.md`.
