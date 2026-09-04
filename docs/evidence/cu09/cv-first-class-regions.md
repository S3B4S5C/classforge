# CU-09 Cal-011 — CV-first class regions + closed VLM mapping

**Estado:** experimental; no cierra CU-09.

## Problema observado en Cal-010

La primera ejecución híbrida demostró que pedir al VLM coordenadas `x/y/width/height` era una frontera incorrecta: las seis cajas se devolvieron con tamaños uniformes y posiciones que no coincidían con los rectángulos UML reales. OpenCV heredó esas regiones incorrectas, la máscara no eliminó los textos de las clases y la geometría produjo un único edge candidato.

El `threshold.png`, en cambio, mostró que los bordes físicos de las seis cajas y las líneas manuscritas son claramente recuperables con CV clásico.

## Cambio de autoridad

Cal-011 elimina coordenadas del contrato VLM:

```text
semantic pass Qwen
  -> c1..c6 + nombres/atributos

OpenCV
  -> detecta físicamente B1..B6
  -> x/y/width/height quedan fijados por Java

Qwen sobre overlay B1..B6
  -> SOLO mapping Bx -> cx
  -> sin coordenadas
  -> bijección exacta

Java
  -> enlaza geometryId + classRef
  -> masking real
  -> reconstrucción geométrica
```

El mapeo falla cerrado si OpenCV no detecta exactamente el mismo número de cajas que clases semánticas o si el VLM repite/inventa `geometryId`/`classRef`.

## Detector de regiones

`OpenCvUmlClassRegionDetector` usa adaptive threshold + contours y filtra candidatos por tamaño, rectangularidad y aspect ratio. Los compartimentos internos se suprimen por containment/overlap y se prefieren rectángulos exteriores de mayor área. El detector no conoce nombres UML.

El modo focal puede detenerse después de geometría con `-HybridGeometryOnly`; así se validan primero `class-regions.png`, `mapping.json`, `geometry.json` y `overlay.png` sin gastar tiempo ni tokens en la anotación local de relaciones.

## Gate de Cal-011

Antes de volver a optimizar relaciones se exige:

```text
CV class regions = 6/6
mapping Bx->cx    = 6/6 bijectivo
unexpected boxes = 0
```

Sólo con ese gate verde se continúa con masking/edge reconstruction y, posteriormente, marker/multiplicidades.
