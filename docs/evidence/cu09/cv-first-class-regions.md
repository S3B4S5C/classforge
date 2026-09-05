# CU-09 Cal-011 — CV-first class regions + closed VLM mapping

**Estado:** CLOSED.  
**Rol en la arquitectura final:** autoridad espacial de cajas UML.

## Problema de Cal-010

La primera versión híbrida pedía al VLM `x/y/width/height` para cada clase. En `library-whiteboard-realistic`, las cajas devueltas tenían tamaños/posiciones poco consistentes con los rectángulos físicos. OpenCV heredaba esas regiones incorrectas y la reconstrucción de conectores quedaba degradada.

El threshold de la imagen sí mostraba bordes recuperables con visión clásica.

## Cambio de autoridad

Cal-011 elimina coordenadas del contrato VLM:

```text
Qwen semantic pass
  -> c1..cn + nombres/atributos

OpenCV
  -> B1..Bn + bbox físicos

Qwen sobre overlay B1..Bn
  -> SOLO Bx -> classRef

Java
  -> exige mapping biyectivo
```

El VLM no puede crear cajas ni coordenadas. OpenCV no conoce nombres UML.

## Detector final

`OpenCvUmlClassRegionDetector` combina evidence de contours/raster/Hough para reconstruir marcos externos aunque la fotografía esté rotada, los bordes tengan discontinuidades o los compartimentos internos se detecten como rectángulos separados.

Hardening relevante:

- candidatos estrictos vs. hipótesis de marco completo;
- companion chains para unir compartimentos;
- cierre de bordes con Hough;
- soporte de dividers internos;
- binary bridge para continuidad local;
- fixed-point/maximal chains;
- fail-closed cuando la evidencia no es completa.

## Gate focal

Oracle final de seis regiones:

| Región | BBox final | Mecanismo | IoU aprox. |
|---|---|---|---:|
| B1 Categoría | `(349,182 166x290)` | COMPANION | 0.9632 |
| B2 Préstamo | `(542,234 254x350)` | HOUGH | 0.9180 |
| B3 Usuario | `(383,723 211x245)` | COMPANION | 0.9618 |
| B4 Biblioteca | `(652,753 199x246)` | COMPLETE | 0.9523 |
| B5 Libro | `(485,1134 291x347)` | COMPANION | 0.9659 |
| B6 Autor | `(246,1203 215x255)` | COMPANION | 0.9390 |

El test sintético también se endureció para exigir un IoU alto del outer frame.

## Mapping

Con las cajas fijadas por Java, Qwen recibe un overlay con `B1..Bn` y sólo devuelve correspondencias a refs semánticas existentes. `VisionGeometryClassMappingValidator` exige:

- mismo número de regiones y clases;
- cada `geometryId` exactamente una vez;
- cada `classRef` exactamente una vez;
- ningún valor inventado.

## Resultado

Cal-011 cierra la frontera espacial. Las calibraciones posteriores no vuelven a modificar el detector salvo regresión demostrada.

Artifacts de diagnóstico típicos:

```text
class-regions.json
class-regions-threshold.png
class-regions.png
mapping.json
```
