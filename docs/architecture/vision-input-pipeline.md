# Pipeline visual — CU-09 Imagen -> UML

**Estado:** arquitectura vigente de CU-09 CERRADO.  
**Último corte:** 5 de septiembre de 2026.

## 1. Objetivo arquitectónico

CU-09 permite interpretar una imagen/fotografía de un diagrama de clases UML sin introducir una segunda fuente de verdad ni una ruta alternativa de mutación.

La regla principal es:

> La imagen y el VLM sólo producen evidencia y propuesta. El proyecto cambia únicamente después de compilar esa propuesta a comandos canónicos, previsualizarlos y aplicarlos por la misma autoridad colaborativa que usa el resto de ClassForge.

## 2. Flujo completo

```text
PNG / JPEG / WEBP
        |
        v
AssistantImageInputValidator
- firma real
- MIME
- bytes
- dimensiones
        |
        v
VisionImageNormalizer
- orientación
- representación normalizada
        |
        v
VisionModelGateway (@Primary HybridVisionModelGateway)
        |
        v
Qwen3-VL semantic first pass
- clases
- atributos
- evidence
        |
        +------------------------------+
        |                              |
 semantic classes < 4           semantic classes >= 4
        |                              |
        |                              v
        |                     OpenCV class regions
        |                     B1..Bn + bbox físicos
        |                              |
        |                              v
        |                     closed VLM mapping
        |                     Bx -> classRef
        |                              |
        |                              v
        |                     Java bijection validator
        |                              |
        |                              v
        |                     OpenCV relationship geometry
        |                     unordered physical pairs
        |                              |
        |                              v
        |                     per-edge Qwen annotation
        |                     type + marker
        |                              |
        |                              v
        |                     per-endpoint conditioned crop
        |                     competing connector suppression
        |                              |
        |                              v
        |                     Qwen raw multiplicity transcription
        |                              |
        |                              v
        |                     explicit edge attribution
        |                              |
        |                              v
        |                     Java VisionMultiplicityParser
        |                              |
        +---------------+--------------+
                        |
                        v
                VisionUmlProposal
                + VisionEvidence
                        |
                        v
        VisionProposalGroundingValidator
        VisionEvidenceBoundsValidator
                        |
                        v
          VisionProposalCompiler
          + deterministic identifier
            canonicalization
                        |
                        v
             AssistantSemanticPlan
                        |
                        v
        UmlAssistantCommandResolver
                        |
                        v
             canonical BATCH
                        |
                        v
                     preview
                        |
                  user reviews
                        |
                      Apply
                        |
                        v
               local Command Bus
                        |
                        v
              ProjectOperation
                        |
                        v
       ProjectCollaborationService
       - permission
       - baseRevision
       - execute BATCH
       - validate document
       - persist once
                        |
                        v
              ProjectDocument
              revision + 1
```

## 3. Entrada y normalización

Endpoint productivo:

```http
POST /api/projects/{projectId}/assistant/image/plan
Content-Type: multipart/form-data
```

Campos:

- `image`;
- `baseRevision`.

El contrato inicial admite PNG, JPEG y WEBP. Backend valida firma real, tamaño y dimensiones. La preparación de UI (rotate/crop/reset) no reemplaza la validación backend.

La imagen no se guarda dentro de `ProjectDocument`.

## 4. Primera pasada semántica

Qwen3-VL recibe la imagen y un contexto estructurado reducido del proyecto. La primera pasada identifica principalmente clases, atributos y evidencia visual.

No recibe autoridad para generar UUID ni mutar el proyecto.

El routing productivo usa:

```text
CLASSFORGE_ASSISTANT_VISION_HYBRID=true
CLASSFORGE_ASSISTANT_VISION_HYBRID_MIN_CLASSES=4
```

- menos de 4 clases semánticas: semantic-only;
- 4 o más: hybrid-CV.

El camino pequeño no se considera fallback; es una estrategia explícita.

## 5. Class regions: OpenCV es autoridad espacial

La primera aproximación híbrida (Cal-010) pidió al VLM coordenadas de cajas. La pizarra real mostró que esa frontera era incorrecta.

Cal-011 fija la autoridad así:

```text
OpenCV
 -> detecta B1..Bn físicos

Qwen
 -> sólo mapea Bx -> classRef

Java
 -> exige bijección cerrada
```

`OpenCvUmlClassRegionDetector` reconstruye rectángulos exteriores y compartimentos mediante threshold/contours, companion chains y soporte Hough/raster. No conoce nombres de clases.

El VLM no puede inventar nuevos `geometryId`, repetir mappings ni producir coordenadas.

## 6. Topología: geometry es autoridad de conectividad

Cal-012 establece que `OpenCvUmlDiagramGeometryAnalyzer` es la autoridad de pares físicos.

Principios:

- un crossing sin junction no crea conexión;
- se aceptan bridges/fallback raster únicamente con evidencia local suficiente;
- la geometría trabaja con pares de regiones físicas, todavía sin decidir semántica UML;
- el VLM no puede eliminar un edge físicamente confirmado ni crear un tercer endpoint.

En la pizarra focal la auditoría final identificó 6 conectores físicos reales y 0 extras. El antiguo oracle `Usuario-Libro` se corrigió porque ese conector no estaba dibujado.

## 7. Anotación per-edge

Cada edge físico se clasifica de forma independiente y stateless.

La etapa decide:

- `ASSOCIATION` / `AGGREGATION` / `COMPOSITION` / `GENERALIZATION` cuando corresponde;
- marker/orientación permitida por el contrato.

El schema exige exactamente una clasificación para el edge dado. La inferencia no decide existencia física.

## 8. Multiplicidades: transcription + attribution

### 8.1 Transcription

Cada endpoint se procesa de forma independiente. Qwen sólo devuelve un `rawLabel` pequeño (`1`, `*`, `0..*`, `1..*`, etc.) o `null`.

Java convierte posteriormente ese string mediante `VisionMultiplicityParser`.

### 8.2 Competitor-conditioned source

Un problema real apareció cuando dos conectores incidían cerca de la misma clase. Un label legítimo de E4 contaminaba E6.

La solución final construye máscaras geométricas deterministas:

```text
current support:
  guide length = 90 px
  half width   = 36 px
  contact rad  = 40 px

competitor:
  guide length = 90 px
  half width   = 28 px
  contact rad  = 40 px

suppression = competitorMask AND NOT currentSupportMask
```

La visibilidad de un competitor se decide por el footprint rasterizado que realmente entra al crop, no sólo por si su centerline está dentro.

### 8.3 Attribution

Si transcription devuelve label no nulo, una segunda inferencia recibe:

- LABEL SOURCE;
- CLASS CONTEXT;
- current edge;
- competing edge IDs.

Devuelve `owner = edgeId | AMBIGUOUS | NONE`.

Java sólo acepta la multiplicidad cuando:

```text
owner == current edgeId
```

No existe regla especial que elimine multiplicidades por ser aggregation/composition; UML permite multiplicidades en asociaciones de agregación/composición.

## 9. Identificadores visuales y contrato de código

Cal-017 descubrió una frontera adicional durante el smoke real:

```text
texto leído de la imagen    identificador válido del modelo
Categoría                    Categoria
Préstamo                     Prestamo
añoPublicacion                anoPublicacion
```

La evidencia visual debe permanecer literal. No se obliga al VLM a “escribir sin tildes”.

Antes de cruzar desde `VisionUmlProposal` a `AssistantSemanticPlan`, el compiler canonicaliza determinísticamente nuevos nombres de clase, atributos y custom types al contrato de código del dominio.

El `ProjectDocumentValidator` no se relajó.

## 10. Grounding, compiler y executable gate

Antes de producir un comando:

- las refs deben existir;
- evidence debe respaldar símbolos;
- bounding boxes, cuando existen, deben estar dentro de la imagen;
- conflictos con elementos existentes se resuelven conservadoramente;
- nombres del modelo deben cumplir el contrato de código.

El benchmark de estrategia `original` incorpora un executable gate:

```text
AssistantSemanticPlan
 -> resolve BATCH
 -> preview
 -> ProjectDocumentValidator
```

Un plan semánticamente equivalente pero no ejecutable no puede considerarse Exact.

## 11. Estados de salida

```text
sin UML accionable           -> NO_ACTIONABLE_UML, command=null
sin cambios respecto modelo  -> NO_CHANGES,       command=null
cambios válidos              -> READY,            BATCH + preview
fallo híbrido                -> VISION error,      no preview
conflicto revisión           -> revision conflict, no Apply
```

## 12. Fail-closed y rollback

Default productivo:

```text
CLASSFORGE_ASSISTANT_VISION_HYBRID_FALLBACK=false
```

Si el routing ya seleccionó hybrid-CV:

- error de transporte se conserva como `TRANSPORT`;
- error contractual/interno se representa como `OUTPUT_CONTRACT`;
- no se retorna la propuesta semantic-only inicial;
- la UI ofrece `Analizar nuevamente`.

Rollback/debug explícito:

```text
CLASSFORGE_ASSISTANT_VISION_HYBRID_FALLBACK=true
```

## 13. Concurrencia

La revisión se protege en varias fronteras:

1. antes de iniciar Vision;
2. después de terminar la inferencia y antes de devolver preview;
3. frontend antes de Apply;
4. `ProjectCollaborationService` al recibir `ProjectOperation`.

Un plan stale no se rebasea automáticamente y ningún child del BATCH se persiste parcialmente.

## 14. Apply y persistencia

`AssistantImagePlanService` no persiste.

`READY` contiene el mismo BATCH que se usa para preview. Al aceptar:

```text
BATCH
 -> Command Bus
 -> ProjectOperation(baseRevision)
 -> ProjectCollaborationService.apply
 -> ProjectCommandExecutor
 -> ProjectDocumentValidator
 -> repository/save
```

Un BATCH completo incrementa la revisión exactamente una vez, aunque contenga múltiples child commands.

Cal-016 demostró que el documento reabierto es exactamente igual al preview determinista del BATCH aplicado.

## 15. Diagnostics

Producción usa `collectDiagnostics=false` y evita generar/retener artifacts pesados que sólo sirven para calibración.

Benchmark/geometry mode conserva diagnostics como:

- class regions;
- mapping;
- geometry;
- segments/overlay;
- relationship panels;
- conditioned transcription panels;
- mask diagnostics;
- attribution panels;
- multiplicity observations.

## 16. Runtime validado

```text
Model: Qwen3-VL-4B-Instruct Q4_K_M
Vision endpoint: 127.0.0.1:8094
semantic tokens: 3200
mapping:         1200
relationship:     512
multiplicity:     128
```

La corrida focal final observó ~5.4 GiB de VRAM (5455 MiB en la corrida 3/3).

## 17. Evidencia y trazabilidad

- especificación del CU: `docs/puds/use-cases.md`;
- cierre del Ciclo 2: `docs/puds/cycles/cycle-02-elaboration.md`;
- incrementos: `docs/puds/iterations/cycle-02/C2-cu09-001..003`;
- selección de modelo: `docs/evidence/cu09/vision-model-selection.md`;
- class regions: `docs/evidence/cu09/cv-first-class-regions.md`;
- evolución híbrida: `docs/evidence/cu09/hybrid-cv-geometry.md`;
- cierre integral: `docs/evidence/cu09/cu09-closure-report.md`;
- evidencia estructurada: `docs/evidence/cu09/cu09-acceptance.json`.
