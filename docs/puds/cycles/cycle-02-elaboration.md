# Ciclo 2 — Colaboración real y entrada visual

**Fase PUDS:** Elaboración  
**Estado:** CERRADO  
**Inicio:** 28 de agosto de 2026  
**Cierre:** 5 de septiembre de 2026

## Objetivo

Cerrar dos riesgos que permanecían después del Ciclo 1:

1. convertir la colaboración de multisesión owner-only en colaboración entre cuentas reales;
2. demostrar que una imagen/fotografía puede converger de forma segura en el mismo modelo UML canónico sin crear una ruta alternativa de mutación.

## Casos del ciclo

| Caso | Estado final | Resultado |
|---|---|---|
| CU-31 Membresía e invitaciones | CERRADO | OWNER/EDITOR/NONE, invitaciones internas, realtime/presencia y Assistant membership-aware |
| CU-09 Imagen a UML | CERRADO | VLM + OpenCV + Java -> propuesta -> BATCH -> preview -> Apply canónico -> persistencia |

CU-10 y CU-11 de interoperabilidad XMI/Enterprise Architect se difieren deliberadamente a un ciclo posterior.

## Desarrollo del ciclo

```text
CU-31
  -> C2-cu31-001 membership + política OWNER/EDITOR       [COMPLETADO]
  -> C2-cu31-002 invitaciones internas + UI              [COMPLETADO]
  -> C2-cu31-003 hardening realtime/presencia/Assistant  [COMPLETADO]

CU-09
  -> C2-cu09-001 contrato de imagen + convergencia canónica   [COMPLETADO]
  -> C2-cu09-002 runtime VLM + benchmark + selección 4B       [COMPLETADO]
  -> C2-cu09-003 UX + hardening + E2E + cierre                [COMPLETADO]
  -> Cal-011..017 cierre de percepción, producción y seguridad [CERRADO]
```

## Resultado de CU-31

El ciclo consolidó colaboración real entre cuentas:

- `ProjectMembership` EDITOR persistente;
- política central OWNER/EDITOR/NONE;
- invitaciones internas por correo normalizado;
- bandeja de pendientes, aceptar/rechazar/cancelar;
- creación transaccional de membership;
- OWNER y EDITOR colaboran en STOMP y presencia;
- NONE es rechazado;
- Assistant texto/voz respeta membership;
- mutaciones de invitaciones serializadas por proyecto.

CU-31 cierra el primer riesgo del ciclo.

## Resultado de CU-09

CU-09 demuestra que una imagen converge en la misma autoridad documental:

```text
imagen
 -> validación/normalización
 -> Qwen3-VL semantic pass
 -> hybrid-cv cuando el diagrama es denso
 -> VisionUmlProposal + evidence
 -> grounding/compiler
 -> identificadores de dominio canónicos
 -> AssistantSemanticPlan
 -> BATCH + preview
 -> Apply
 -> ProjectOperation
 -> ProjectCollaborationService
 -> ProjectCommandExecutor
 -> ProjectDocument
```

No existe `VisionApply`, Image Command Bus ni persistencia directa desde el VLM.

### Decisiones que cerraron el riesgo

- **Cal-011:** OpenCV, no el VLM, posee las regiones físicas de clases.
- **Cal-012:** geometry posee la topología física de conectores.
- **Cal-013:** Qwen anota cada edge y endpoint de forma local; Java controla el contrato y el parser.
- **Cal-014:** `hybrid-cv` se activa por defecto para diagramas con >=4 clases semánticas.
- **Cal-015:** si el híbrido falla, producción falla cerrado; el semantic fallback queda sólo como rollback explícito.
- **Cal-016:** el BATCH de Imagen -> UML atraviesa la autoridad colaborativa real y se persiste una sola vez.
- **Cal-017:** los labels visuales Unicode se conservan como evidencia, pero se canonicalizan determinísticamente antes de ingresar al modelo de código.

## Evidencia del caso visual focal

`library-whiteboard-realistic`, Qwen3-VL-4B-Instruct Q4_K_M, `hybrid-cv`, estrategia original:

```text
3/3 PASS Exact
Transport        100.0 %
Schema valid     100.0 %
Grounding        100.0 %
Classes          18/18 expected, 0 unexpected
Attributes       51/51 expected, 0 unexpected
Relationships    18/18 expected, 0 unexpected
Multiplicity     30/30 expected, 0 unexpected
Semantic exact   100.0 %
Safety invalid   100.0 %
GPU peak         5455 MiB
```

La corrida demuestra estabilidad focal, no una tasa estadística general sobre cualquier pizarra.

## Validación de producto

Además del benchmark focal:

- el plan no persiste antes de `Apply`;
- preview y documento reabierto son exactamente iguales;
- un BATCH incrementa una sola revisión;
- un plan stale se rechaza sin mutación parcial;
- un cambio de revisión durante visión impide `READY`;
- la UI real completó happy path, preparación de imagen, retry fail-closed, stale plan y permisos OWNER/EDITOR/NONE;
- el mismo fixture realista fue aplicado y reabierto correctamente después de Cal-017.

## Riesgo residual aceptado

Durante el trabajo se propuso ampliar la medición con al menos dos pizarras reales adicionales. Esa campaña y una ejecución archivada del agregador completo `assistant-vision-acceptance.ps1` no se ejecutaron antes del cierre funcional. Tampoco quedó registrada una ejecución separada del build final de frontend posterior al último ajuste.

El cierre del Ciclo 2 **no presenta esas actividades como realizadas**. Se acepta el riesgo residual porque el objetivo del ciclo era demostrar convergencia segura al modelo canónico y esa propiedad quedó cubierta por benchmark focal, tests deterministas, full backend gates, E2E canónico y smoke interactivo. La validación multi-pizarra se conserva como recomendación de robustez para un ciclo posterior.

## Riesgos principales y resolución

| Riesgo | Resolución |
|---|---|
| autorización inconsistente REST/STOMP/Assistant | política OWNER/EDITOR/NONE central, validada por CU-31 |
| crear otra fuente de verdad desde imagen | todo converge a `ProjectDocument` mediante BATCH/Command Bus |
| VLM inventando topología | geometry OpenCV/Java autoritativa en hybrid-cv |
| multiplicidades contaminadas por conectores cercanos | transcription condicionada + attribution explícita + parser Java |
| fallback silencioso a topología semántica | fail-closed por defecto |
| plan obsoleto / colaboración concurrente | `baseRevision` antes/después de inferencia y en Apply |
| nombres visuales incompatibles con el dominio | canonicalización determinista en la frontera compiler |

## Criterio de cierre alcanzado

El ciclo se considera cerrado porque ambos casos objetivo están implementados y aceptados, y la arquitectura mantiene una única autoridad de mutación para UI manual, realtime, Assistant textual/voz e Imagen -> UML.

```text
Ciclo 2: CERRADO
CU-31: CERRADO
CU-09: CERRADO
```

## Siguiente ciclo

Cualquier trabajo sobre XMI, modelo relacional, generación o validación estadística ampliada debe abrirse como un nuevo Ciclo con objetivo, riesgos y criterios de salida propios.
