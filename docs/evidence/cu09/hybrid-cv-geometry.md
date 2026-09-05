# CU-09 — Evidencia de evolución hybrid-CV

**Estado:** pipeline híbrido productivo cerrado.  
**Cobertura:** Cal-010 a Cal-017.  
**Fixture focal:** `library-whiteboard-realistic`.

## 1. Motivación

La pizarra real mostró una separación estable de capacidades:

- Qwen3-VL-4B reconoce bien clases, atributos y semántica local;
- seguir conectores largos/cruzados, reconstruir topología y asignar multiplicidades pequeñas es menos fiable en una sola inferencia global.

Los experimentos de prompt geométrico, segunda pasada global y crop/tiling no superaron de forma consistente el mejor single-pass. El diseño final separó responsabilidades:

```text
VLM    -> semántica visible/local
OpenCV -> regiones y conectividad física
Java   -> contratos, matching, parser, fail-closed y ensamblado
```

## 2. Cal-010 — primera separación híbrida

Cal-010 probó CV para conectividad y VLM para anotación local. La primera frontera seguía pidiendo al VLM bbox de clases.

La corrida real demostró que `Qwen -> bbox` no era suficientemente fiable y que OpenCV heredaba una máscara incorrecta. Cal-010 queda **superseded** por Cal-011, pero conserva valor histórico: mostró que el problema debía descomponerse y que no hacía falta añadir otro modelo GPU.

## 3. Cal-011 — class regions físicas

Cal-011 mueve la autoridad de coordenadas a `OpenCvUmlClassRegionDetector` y limita Qwen a mapping cerrado `Bx -> classRef`.

Resultado focal: 6/6 cajas físicas con IoU alto y 0 extras. Ver `cv-first-class-regions.md`.

## 4. Cal-012 — topología física

`OpenCvUmlDiagramGeometryAnalyzer` reconstruye conectores a partir de segmentos y contacto con class regions.

Hardening relevante:

- endpoint bridge sólo con soporte raster suficiente;
- attachment raster local, no propagación global de componentes;
- mayor autoridad de contacto Hough directo;
- guard de cardinalidad para que fallback local no convierta un par binario confirmado en componente ambiguo;
- edge pairs no dirigidos en geometry.

### Oracle físico auditado

La fotografía contiene exactamente estos seis pares:

1. Autor — Libro
2. Biblioteca — Libro
3. Biblioteca — Usuario
4. Categoría — Libro
5. Préstamo — Libro
6. Préstamo — Usuario

No existe un conector Usuario — Libro. Ese vínculo estaba en un oracle antiguo y fue retirado después de auditar la imagen.

Resultado `HybridGeometryOnly`:

```text
physical pairs = 6/6
unexpected     = 0
```

Cal-012 queda CLOSED y geometry pasa a ser autoridad de topología física.

## 5. Cal-013 — anotación per-edge

La primera anotación global de relaciones truncaba respuestas. Se sustituyó por inferencia stateless por edge físico.

Cada edge recibe un panel local y un schema cerrado. El VLM decide tipo/marker; no puede devolver `edges=[]` para eliminar un conector confirmado por geometry.

Budgets finales:

```text
relationship = 512 tokens por edge
```

Con schemas acotados desapareció el truncamiento de esta etapa.

## 6. Multiplicidades — transcription independiente

La anotación conjunta de ambos extremos copiaba a veces una multiplicidad de un lado al otro. La solución separó:

```text
endpoint A -> inferencia stateless
endpoint B -> inferencia stateless
```

Qwen devuelve únicamente `rawLabel`; Java usa `VisionMultiplicityParser`:

```text
null/blank -> null
*          -> 0..*
N          -> N..N
N..M       -> N..M
N..*       -> N..*
malformed  -> reject
```

Budget final:

```text
multiplicity = 128 tokens por endpoint
```

## 7. El último falso positivo: E6-B

Después de recuperar todas las multiplicidades esperadas quedó un único falso positivo:

```text
E6 Biblioteca—Libro
endpoint Biblioteca
rawLabel = "1"
```

Ese `1` existía en la foto, pero pertenecía a E4 Biblioteca—Usuario.

No era correcto introducir una regla `AGGREGATION => no multiplicity`; UML permite multiplicidades en aggregations. El problema era de ownership visual.

## 8. Attribution experiments

Se probaron, sin resolver de forma robusta el caso E6-B:

- dirección/corridor;
- competitors coloreados;
- whole-class context;
- source crop + class context;
- edge IDs explícitos;
- owner enum `edgeId | AMBIGUOUS | NONE`;
- localización VLM del label.

La localización VLM fue descartada cuando todas las respuestas devolvían aproximadamente `(500,500)` normalizado, por lo que no se usó como guard determinista.

## 9. Fix-011 — competitor-conditioned transcription

La solución final mueve parte del ownership **antes** de transcribir.

Para el endpoint actual se construye un current support mask. Para cada conector incidente competidor se construye un footprint. Sólo se blanquean píxeles exclusivos del competitor:

```text
current:
  guide length   90
  half width     36
  contact radius 40

competitor:
  guide length   90
  half width     28
  contact radius 40

suppressionMask = competitorMask AND NOT currentSupportMask
```

La selección de competitor es footprint-aware: incluso si su centerline queda unos píxeles fuera del crop, se conserva si el raster de su footprint intersecta realmente el canvas.

Esto es simétrico:

- E4-A conserva su `1` legítimo;
- E6-B elimina el `1` que sólo pertenece a E4.

Attribution explícita permanece como segunda defensa cuando transcription devuelve un label.

## 10. Resultado final de Cal-013

Primera corrida posterior al fix:

```text
1/1 PASS Exact
```

Corrida de estabilidad:

```text
3/3 PASS Exact
Transport        100.0 %
Schema valid     100.0 %
Grounding        100.0 %
Classes          18/18, 0 unexpected
Attributes       51/51, 0 unexpected
Relationships    18/18, 0 unexpected
Multiplicity     30/30, 0 unexpected
Semantic exact   100.0 %
Safety invalid   100.0 %
GPU peak         5455 MiB
Elapsed          8m45s
```

Cal-013 cierra el focal hardening.

## 11. Cal-014 — activación productiva

Después del cleanup de caminos superseded:

```text
hybrid enabled   = true
minClasses       = 4
mapping tokens   = 1200
relationship     = 512
multiplicity     = 128
semantic tokens  = 3200
```

Routing:

```text
hybrid=false      -> semantic-only
hybrid=true,<4    -> semantic-only
hybrid=true,>=4   -> hybrid-CV
```

Producción usa diagnostics pesados deshabilitados; benchmark conserva overlays/masks.

## 12. Cal-015 — fail-closed

El fallback semántico después de un fallo híbrido podría reintroducir una topología que ya no ha pasado por la autoridad física de geometry.

Default final:

```text
fallback-to-semantic=false
```

Un fallo hybrid produce error `VISION` y no proposal/preview. La UI ofrece retry.

`fallback=true` permanece como rollback/debug explícito.

## 13. Cal-016 — E2E de Apply canónico

El acceptance antiguo ejecutaba `ProjectCommandExecutor` y persistía directamente en el test, saltándose la autoridad real de colaboración.

Cal-016 migra la evidencia a:

```text
image plan
 -> BATCH
 -> ProjectOperationRequest
 -> ProjectCollaborationService.apply
 -> ProjectCommandExecutor
 -> validation/save
 -> reopen
```

Se demuestra:

- plan no muta persistencia;
- preview == documento persistido/reabierto;
- BATCH = exactamente una nueva revisión;
- stale operation = `REVISION_CONFLICT`;
- 0 child commands parciales tras stale rejection;
- cambio de revisión durante inferencia impide `READY`.

## 14. Cal-017 — identificadores ejecutables

El smoke de UI encontró que el benchmark podía considerar equivalentes:

```text
Categoría ~ Categoria
Préstamo  ~ Prestamo
añoPublicacion ~ anoPublicacion
```

pero `ProjectDocumentValidator` sólo acepta identificadores compatibles con código.

La solución conserva raw evidence y canonicaliza en la frontera de compilación. Además el benchmark añade un gate de aplicabilidad:

```text
plan
 -> resolve
 -> preview
 -> ProjectDocumentValidator
```

Un plan no ejecutable ya no puede considerarse Exact sólo por equivalencia semántica.

El mismo fixture realista pasó posteriormente el smoke de producto.

## 15. Artifacts de diagnóstico

El benchmark puede conservar, según etapa:

```text
class-regions.json
class-regions-threshold.png
class-regions.png
mapping.json
geometry.json
threshold.png
segments.png
overlay.png
relationship-sheet.png
relationship-*.png
multiplicity-*-transcription-conditioned.png
multiplicity-*-current-support-mask.png
multiplicity-*-competitor-*-mask.png
multiplicity-*-final-suppression-mask.png
multiplicity-*-mask-debug-overlay.png
multiplicity-*-attribution.png
multiplicity-observations.json
```

Estos artifacts son de diagnóstico; producción no los retiene por defecto.

## 16. Conclusión

La arquitectura final no depende de que un VLM general resuelva simultáneamente percepción, conectividad y semántica. La robustez proviene de asignar autoridad a la herramienta adecuada y hacer fail-closed en cada frontera.

Cal-010 se conserva como historia; Cal-011..017 representan el camino que llevó al cierre funcional de CU-09.
