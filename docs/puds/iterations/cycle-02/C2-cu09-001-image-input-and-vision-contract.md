# C2-cu09-001 — Image input and vision contract

**Caso de uso:** CU-09 Imagen → UML  
**Estado:** COMPLETADO como infraestructura contractual; CU-09 permanece EN PROGRESO.  
**Fecha:** 29 de agosto de 2026.

## Objetivo

Abrir CU-09 sin introducir una segunda ruta de mutación. La imagen debe converger en la misma IR, preview, BATCH y Command Bus ya validados por CU-08.

## Alcance entregado

- `POST /api/projects/{projectId}/assistant/image/plan` multipart;
- `baseRevision` obligatorio;
- PNG/JPEG/WEBP con validación por firma real;
- límites de bytes y dimensiones;
- normalización PNG/JPEG a PNG RGB y orientación EXIF;
- `VisionModelGateway` desacoplado del proveedor;
- fallback `UnconfiguredVisionModelGateway` explícito hasta C2-cu09-002;
- `VisionUmlProposal` con refs temporales, clases, atributos, relaciones, multiplicidades, warnings y confidence;
- `VisionEvidence` para provenance visual;
- `VisionProposalGroundingValidator` fail-closed;
- `VisionProposalCompiler -> AssistantSemanticPlan`;
- reutilización de `UmlAssistantCommandResolver`, preview y `ProjectDocumentValidator`;
- doble chequeo de revisión antes/después del VLM;
- OWNER/EDITOR alcanzan el pipeline; NONE se rechaza antes de invocar visión;
- frontend con selección, miniatura, análisis y reutilización del mismo preview/Apply;
- fixtures visuales y suite focal `assistant-vision-contract.ps1`.

## Fuera de alcance

- selección del VLM definitivo;
- OCR propio;
- benchmark real image→UML;
- overlays de bounding boxes;
- cámara/crop avanzado;
- persistencia de imágenes.

## Decisión

El VLM interpreta; ClassForge mantiene autoridad estructural.

```text
imagen
 -> VisionUmlProposal
 -> provenance/validación
 -> AssistantSemanticPlan
 -> BATCH
 -> preview
 -> Apply
 -> Command Bus
 -> ProjectDocument
```

## Siguiente incremento

`C2-cu09-002`: seleccionar y conectar el VLM local, definir prompt/schema multimodal y construir benchmark cuantitativo sobre los fixtures y un conjunto holdout visual.
