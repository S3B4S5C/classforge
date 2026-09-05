# C2-cu09-001 — Entrada de imagen y contrato visual

**Caso de uso:** CU-09 — Crear UML desde imagen/fotografía  
**Estado final:** COMPLETADO  
**Fecha de apertura:** 29 de agosto de 2026  
**Cierre funcional integrado en CU-09:** 5 de septiembre de 2026

## Objetivo

Abrir CU-09 sin crear una segunda ruta de mutación. La imagen debía converger en la misma representación semántica, preview, BATCH y autoridad de persistencia ya validadas por CU-08.

## Alcance entregado

- `POST /api/projects/{projectId}/assistant/image/plan` multipart;
- `baseRevision` obligatorio;
- PNG/JPEG/WEBP con verificación de firma real;
- límites de bytes y dimensiones;
- normalización de imagen y orientación EXIF cuando aplica;
- abstracción `VisionModelGateway` desacoplada del proveedor;
- `VisionUmlProposal` con refs temporales, clases, atributos, relaciones, multiplicidades, warnings, confidence y evidence;
- grounding fail-closed;
- compilación a `AssistantSemanticPlan`;
- reutilización de `UmlAssistantCommandResolver`, BATCH, preview y `ProjectDocumentValidator`;
- doble verificación de revisión antes y después de la inferencia;
- OWNER/EDITOR acceden; NONE se rechaza antes de invocar Vision;
- primera superficie frontend para seleccionar, previsualizar y analizar imagen.

## Decisión arquitectónica

Desde el primer incremento se fijó la regla que permaneció hasta el cierre:

```text
imagen
 -> propuesta visual
 -> validación/grounding
 -> plan semántico
 -> BATCH
 -> preview
 -> Apply
 -> autoridad normal del proyecto
```

El VLM interpreta; Java y `ProjectDocument` conservan autoridad. La imagen no se persiste dentro del documento y el VLM no genera UUID ni ejecuta mutaciones.

## Riesgos cerrados por este incremento

- evitar escritura directa desde IA;
- evitar que la imagen omita control de revisión;
- permitir referencias entre clases nuevas sin UUID mediante refs temporales;
- mantener provenance visual para revisar lo que el modelo creyó ver;
- asegurar que el mismo compiler/resolver usado por otras entradas procese la propuesta visual.

## Trabajo diferido que se resolvió después

Este incremento no seleccionaba un VLM definitivo ni resolvía percepción geométrica densa. Esos riesgos pasaron a C2-cu09-002/003 y Cal-011..017.

## Resultado final visto desde el cierre

La frontera creada aquí se mantuvo intacta. Las calibraciones posteriores cambiaron cómo se obtiene `VisionUmlProposal`, pero no crearon una ruta alternativa hacia el modelo. Cal-016 confirmó además que el BATCH generado por esta ruta se aplica por `ProjectCollaborationService` y se persiste con una única revisión.
