# Pipeline visual — CU-09

**Estado:** CU-09 EN PROGRESO. Cal-014 activa hybrid-CV por defecto para diagramas densos; aceptación de producto y broader-board permanecen pendientes.

## Ruta actual

```text
Image -> semantic VLM first pass -> class/attribute proposal
  < 4 classes -> semantic result
  >= 4 classes -> OpenCV class regions -> closed Bx -> classRef mapping
                -> OpenCV physical topology -> per-edge classification
                -> conditioned multiplicity transcription -> attribution
                -> Java multiplicity parser -> assembled proposal
```

Hybrid-CV está habilitado por defecto. Geometry es la autoridad de topología
física. Producción no retiene diagnostics pesados; benchmark sí puede hacerlo.
Cuando el routing selecciona hybrid-CV, un fallo devuelve `VISION` fail-closed
por defecto, sin proposal ni preview. El rollback operativo explícito es
`CLASSFORGE_ASSISTANT_VISION_HYBRID_FALLBACK=true`; los diagramas con menos de
cuatro clases siguen semantic-only por estrategia, no por fallback.

## Regla de arquitectura

La imagen nunca modifica `ProjectDocument` directamente.

```text
PNG / JPEG / WEBP
        |
        v
AssistantImageInputValidator
        |
        v
VisionImageNormalizer
        |
        v
VisionModelGateway
        |
        v
VisionUmlProposal + evidencia
        |
        v
VisionProposalGroundingValidator
        |
        v
VisionProposalCompiler
        |
        v
AssistantSemanticPlan
        |
        v
UmlAssistantCommandResolver
        |
        v
BATCH -> preview -> Apply -> Command Bus
        |
        v
ProjectDocument
```

## Entrada

Endpoint:

```http
POST /api/projects/{projectId}/assistant/image/plan
Content-Type: multipart/form-data
```

Campos:

- `image`: PNG, JPEG o WEBP;
- `baseRevision`: revisión exacta sobre la que se solicita el análisis.

Restricciones iniciales:

- máximo 10 MiB;
- dimensión mínima 64 px por lado;
- dimensión máxima 8192 px por lado;
- MIME declarado debe coincidir con la firma real;
- JPEG/PNG se normalizan internamente a PNG RGB;
- orientación EXIF JPEG se aplica antes del análisis;
- WEBP se valida por cabecera y se conserva, porque ImageIO estándar no incluye decoder WEBP.

La imagen no se persiste en C2-cu09-001.

## Contrato visual

`VisionModelGateway` recibe la imagen normalizada y un `VisionProjectContext` que contiene únicamente contexto estructurado del proyecto actual.

Devuelve `VisionUmlProposal`:

- clases con `ref` temporal, nombre, atributos y evidencia;
- relaciones entre refs temporales o clases existentes;
- multiplicidades;
- warnings;
- confidence global.

Los refs temporales permiten representar en una sola imagen varias clases nuevas relacionadas sin que el VLM genere UUID.

## Provenance y fail-closed

Toda clase y atributo propuestos deben incluir `VisionEvidence` con etiqueta y confidence. Los bounding boxes quedan preparados en el contrato y son opcionales en C2-cu09-001.

ClassForge rechaza:

- refs duplicados;
- nombres vacíos;
- evidencia que no respalda el símbolo declarado;
- confidence fuera de `[0,1]`;
- referencias de relación que no existen ni en la propuesta ni en el `ProjectDocument`;
- tipos/visibilidades/relaciones no soportados;
- multiplicidades inválidas;
- previews que no pasan `ProjectDocumentValidator`.

Una clase visual cuyo nombre coincide exactamente —ignorando case/diacríticos/separadores— con una clase existente se trata como referencia existente y no se duplica. No se usa fuzzy matching visual en C2-cu09-001 para evitar sustituciones silenciosas.

## Concurrencia

`baseRevision` se comprueba antes de invocar el VLM y nuevamente al terminar la inferencia. Si el proyecto cambió mientras el VLM procesaba la imagen, se responde conflicto y el preview se descarta.

## VLM

C2-cu09-001 no adopta un modelo multimodal concreto. `UnconfiguredVisionModelGateway` falla explícitamente en etapa `VISION`. Los tests usan gateways fake para demostrar que todo el pipeline posterior funciona.

C2-cu09-002 debe seleccionar un VLM mediante benchmark local de:

- precisión estructural UML;
- VRAM;
- latencia;
- resolución útil;
- compatibilidad con llama.cpp;
- calidad de evidencia/provenance.

<!-- C2-CU09-002-REAL-VLM -->
## C2-cu09-002 — VLM real

El adapter oficial de evaluación es `LlamaCppVisionModelGateway`. El baseline se ejecuta con Qwen3-VL-2B-Instruct Q4_K_M y mmproj Q8_0 en 8094; el 4B se prueba como challenger, no como reemplazo automático.

El request multimodal contiene un prompt visual dedicado y `image_url` Base64. `VisionUmlProposalJsonSchema` limita clases, atributos, tipos, relaciones y evidence mediante `additionalProperties=false`. La respuesta debe ser JSON directamente deserializable; ClassForge no corrige la salida del VLM.

`VisionPromptBuilder` serializa del proyecto solo nombres de clases y atributos. Los UUID continúan siendo exclusivamente responsabilidad de Java. El contexto existente no prueba que un símbolo aparezca en la imagen.

El benchmark mantiene regression y holdout separados y compara el plan semántico posterior al grounding/compiler, por lo que mide el efecto funcional real sobre ClassForge sin aplicar cambios al proyecto.


<!-- C2-CU09-003-VISION-HARDENING -->
## C2-cu09-003 — UX, estados seguros y hardening

La preparación de imagen del frontend (rotate/crop/reset) ocurre antes del upload y no conoce `ProjectDocument`. Backend vuelve a validar formato, tamaño, dimensiones y evidencia. Los bounding boxes opcionales deben estar completos y además caber dentro de `width × height` de la imagen normalizada.

El pipeline reconoce estados terminales sin comando:

```text
proposal sin símbolos accionables -> NO_ACTIONABLE_UML -> command=null
proposal equivalente al documento -> NO_CHANGES       -> command=null
proposal con cambios válidos       -> READY            -> BATCH/preview
```

Los atributos o relaciones existentes que contradicen explícitamente a la imagen no se actualizan de forma implícita: se omiten con warning. Esta política evita convertir incertidumbre visual en edición destructiva.

`regression`, `holdout` y `hardening` miden planes semánticos y pueden ejecutarse en modo exploratorio con thresholds 0 mientras se itera el VLM. `assistantVisionAcceptance` prueba además con modelo real que una imagen produzca un comando canónico que pueda persistirse y reabrirse. Ninguna de esas suites dependientes del VLM forma parte del build determinista ordinario.


<!-- CU09-CAL-001-PROMPT-SAFETY-UML-SEMANTICS -->
## Calibración visual: safety y semántica UML

La primera corrida hardening real mostró que el contrato técnico puede estar verde y aun existir errores semánticos sistemáticos. El prompt visual aplica desde cal-001 un procedimiento conservador: primero decide si existe UML de clases accionable; notas, listas, círculos y flechas libres no bastan. Ante duda devuelve una propuesta vacía que el backend convierte en `NO_ACTIONABLE_UML`.

Los tipos escritos después de `:` se preservan mediante un mapeo explícito (`UUID`, `Boolean`, `Decimal`, etc.) y STRING deja de ser un fallback permitido cuando hay un tipo visible distinto. Las multiplicidades se leen por extremo y GENERALIZATION se canonicaliza con `source=subclase`, `target=superclase`, porque el triángulo hueco apunta a la superclase. Estas reglas permanecen en el adaptador/prompt; no agregan una ruta de mutación ni autoridad nueva al VLM.

El dataset también se audita como parte de la calibración: `shadow-association.png` representa visualmente `Cliente — Factura`, por lo que su oracle debe reflejar ese diagrama y no otro fixture.

<!-- CU09-CAL-010-HYBRID-CV-GEOMETRY -->
## Cal-010 — geometría híbrida para diagramas densos (histórico)

Las corridas de la pizarra real mostraron una separación estable de capacidades: Qwen3-VL-4B reconoce clases y atributos, pero seguir líneas largas/cruzadas y leer multiplicidades pequeñas es menos fiable. Cal-010 deja de pedir al VLM que sea simultáneamente OCR, detector geométrico y reconstruidor del grafo.

El modo entonces experimental `hybrid-cv` usaba tres fuentes de evidencia:

```text
imagen original
   |
   +--> semantic pass Qwen3-VL-4B
   |      -> VisionClassProposal[] + atributos
   |
   +--> class localization pass (lista cerrada de refs)
          -> bbox por clase
          -> VisionClassLocalizationValidator
                 |
                 v
          OpenCvUmlDiagramGeometryAnalyzer
          - adaptive threshold
          - supresión completa de cajas/clase
          - cierre morfológico 0/45/90/135 grados
          - HoughLinesP
          - merge de fragmentos casi colineales
          - clustering de endpoints
          - crossing != junction
          - contacto contra borde
          - sólo componentes que tocan exactamente 2 clases
                 |
                 v
          VisionGeometryEdgeCandidate[]
                 |
                 v
          RelationshipEvidenceSheetRenderer
          -> crop del par + crops ampliados de ambos endpoints
                 |
                 v
          Qwen local annotation
          -> edgeId fijo + type + markerAt + multiplicidades
                 |
                 v
          VisionHybridProposalAssembler
                 |
                 v
          VisionUmlProposal canónico
```

### Fronteras de autoridad

- La pasada semántica puede declarar clases/atributos, pero sus relaciones se reemplazan en modo híbrido.
- La localización recibe refs cerrados; no puede crear ni renombrar clases.
- OpenCV no conoce nombres UML: sólo `B1`, `B2`, segmentos y contactos.
- La anotación local sólo puede devolver `edgeId` generados por Java. No puede escoger un tercer endpoint.
- Un componente que toca más de dos cajas se considera ambiguo y se descarta en vez de inferir una conexión.
- Una intersección de segmentos en su parte media no es un junction; sólo endpoints suficientemente próximos se agrupan.
- JSON truncado, refs/edgeIds inventados, marker incompatible o multiplicidad inválida siguen siendo fail-closed.

### Por qué OpenCV se mantiene CPU-only

Qwen3-VL-4B ya usa aproximadamente 5.4 GiB de la GTX 1660 SUPER. Cal-010 no añade YOLO/HAWP/PyTorch ni otro modelo GPU. `org.openpnp:opencv:4.9.0-0` empaqueta bindings Java + natives y sólo se carga cuando el modo híbrido se ejecuta.

### Activación

Esta etapa conservaba `dense-hybrid.enabled=false` hasta obtener evidencia. Cal-014 activa hybrid-CV por defecto; `fallback-to-semantic=true` conserva el retorno al semantic pass con warning.

<!-- CU09-CAL-011-CV-FIRST-CLASS-REGIONS -->
## Cal-011 — CV-first class regions

La evidencia de Cal-010 mostró que el VLM reconoce nombres/contenido de las clases pero no debe ser autoridad de coordenadas de píxel. El pipeline híbrido invierte esa frontera:

```text
Qwen semantic pass
    -> clases/atributos
OpenCV class-region detector
    -> B1..Bn + bbox físicos
Qwen closed mapper
    -> Bx -> classRef (sin coordenadas)
Java bijection validator
    -> regiones mapeadas
OpenCV geometry analyzer
    -> masking + segmentos + edge candidates
```

`OpenCvUmlClassRegionDetector` detecta rectángulos exteriores con contours sobre threshold adaptativo y suprime compartimentos/nested rectangles. Se exige `detectedBoxes == semanticClasses`; Java no rellena cajas faltantes por proximidad. El mapper VLM recibe un overlay con labels B1..Bn y sólo puede escoger refs existentes. Una mapping repetida, incompleta o inventada falla cerrado.

Para depuración, el benchmark escribe `class-regions.json`, `class-regions-threshold.png`, `class-regions.png` y `mapping.json` antes de la fase de relaciones. `-HybridGeometryOnly` detiene el experimento después de reconstruir la geometría para validar esta frontera de forma aislada.
