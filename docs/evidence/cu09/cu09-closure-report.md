# CU-09 — Informe de cierre: Imagen/fotografía -> UML

**Caso de uso:** CU-09 — Crear UML desde imagen/fotografía  
**Estado:** CERRADO  
**Fase PUDS:** Elaboración  
**Ciclo:** 2 — Colaboración real y entrada visual  
**Fecha de cierre:** 5 de septiembre de 2026

---

## 1. Resumen ejecutivo

CU-09 incorpora a ClassForge una nueva forma de entrada: una imagen o fotografía de un diagrama de clases UML puede analizarse localmente y transformarse en una propuesta estructurada, editable y aplicable sobre el mismo modelo canónico que utiliza el modelado manual, la colaboración realtime y el Asistente de texto/voz.

La solución final no delega toda la reconstrucción a un único modelo multimodal. Se adopta una arquitectura híbrida:

- **Qwen3-VL-4B** para interpretación semántica y lectura visual local;
- **OpenCV** para regiones físicas y topología de conectores en diagramas densos;
- **Java** para contratos, grounding, parser de multiplicidades, canonicalización de identificadores, revisión, comandos y persistencia.

El caso de uso se cierra después de validar:

- estabilidad focal 3/3 Exact sobre una fotografía de pizarra realista;
- topología física 6/6 y 0 extras;
- multiplicidades 10/10 esperadas y 0 inesperadas por intento final;
- política productiva fail-closed;
- E2E canónico plan -> preview -> Apply colaborativo -> persistencia -> reopen;
- rechazo de planes stale sin mutación parcial;
- smoke manual de UX completo;
- corrección de nombres visuales Unicode antes de entrar al dominio de código.

El cierre registra de forma explícita que la campaña estadística con dos pizarras reales adicionales y una ejecución archivada post-Cal-017 del agregador completo de acceptance no se realizaron antes de la decisión final. No se presentan como evidencia ejecutada.

---

## 2. Necesidad que resuelve CU-09

Antes de CU-09, un usuario podía construir o modificar UML mediante:

- edición manual;
- colaboración realtime;
- lenguaje natural;
- voz local.

Faltaba poder reutilizar diagramas dibujados o fotografiados sin reescribirlos manualmente.

El riesgo arquitectónico principal era que Imagen -> UML terminara creando una segunda fuente de verdad o una ruta especial de persistencia. CU-09 se diseñó desde el inicio para evitarlo.

### Objetivo funcional

> Dada una imagen/fotografía con UML de clases visible, ClassForge debe producir una propuesta estructurada que el usuario pueda revisar y aplicar de forma segura sobre el `ProjectDocument` actual.

### Restricción estructural

> La IA visual nunca escribe directamente en el modelo. Toda salida debe atravesar el pipeline semántico/canónico normal de ClassForge.

---

## 3. Alcance funcional final

CU-09 incluye:

- selección de imagen;
- drag & drop;
- clipboard;
- cámara compatible;
- rotación;
- crop conservador;
- reset;
- cancelación y retry;
- análisis local con VLM multimodal;
- detección de clases, atributos, relaciones y multiplicidades;
- evidence visual;
- estados seguros `READY`, `NO_CHANGES` y `NO_ACTIONABLE_UML`;
- preview antes de mutación;
- aplicación como BATCH canónico;
- protección por revisión;
- persistencia y reopen;
- fail-closed ante errores de Vision;
- permisos OWNER/EDITOR/NONE.

La imagen no se persiste en `ProjectDocument`.

---

## 4. Flujo end-to-end de una imagen

La siguiente secuencia representa el camino completo desde archivo hasta modelo listo para ClassForge:

```text
[1] Usuario selecciona/captura imagen
              |
              v
[2] Preparación local UI
    rotate / crop / reset
              |
              v
[3] POST image/plan + baseRevision
              |
              v
[4] Validación de entrada
    firma / MIME / bytes / dimensiones
              |
              v
[5] Normalización de imagen
              |
              v
[6] Semantic first pass Qwen3-VL
    clases + atributos + evidence
              |
         +----+----+
         |         |
      <4 clases   >=4 clases
         |         |
         |         v
         |   [7] OpenCV class regions
         |         |
         |   [8] closed Bx -> classRef mapping
         |         |
         |   [9] OpenCV physical topology
         |         |
         |  [10] per-edge type/marker
         |         |
         |  [11] conditioned endpoint transcription
         |         |
         |  [12] explicit edge attribution
         |         |
         |  [13] Java multiplicity parser
         |         |
         +----+----+
              |
              v
[14] VisionUmlProposal + VisionEvidence
              |
              v
[15] Grounding + evidence bounds
              |
              v
[16] Canonicalización de identificadores
              |
              v
[17] VisionProposalCompiler
              |
              v
[18] AssistantSemanticPlan
              |
              v
[19] UmlAssistantCommandResolver
              |
              v
[20] BATCH canónico
              |
              v
[21] Preview sobre baseRevision
              |
              v
[22] READY / revisión del usuario
              |
              v
[23] Apply
              |
              v
[24] Command Bus / ProjectOperation
              |
              v
[25] ProjectCollaborationService
     permiso + revisión + ejecución
              |
              v
[26] ProjectDocumentValidator
              |
              v
[27] Persistencia autoritativa
              |
              v
[28] revision + 1 / reopen
```

---

## 5. Entrada y preparación de imagen

### Formatos

El contrato de entrada admite:

- PNG;
- JPEG;
- WEBP.

Backend valida firma real y no confía sólo en la extensión o MIME declarado.

### Preparación en frontend

El usuario puede:

- cargar archivo;
- arrastrar/soltar;
- pegar desde clipboard;
- usar una fuente de cámara compatible;
- rotar;
- recortar;
- restaurar.

Estas operaciones preparan la evidencia visual; no mutan el proyecto.

### `baseRevision`

La solicitud incluye la revisión exacta sobre la que se generará el plan. Esto permite invalidar el resultado si el proyecto cambia durante la inferencia.

---

## 6. Runtime visual seleccionado

Después de comparar Qwen3-VL-2B y Qwen3-VL-4B se seleccionó:

```text
Qwen3-VL-4B-Instruct Q4_K_M
mmproj Q8_0
llama.cpp multimodal
127.0.0.1:8094
alias vision-model
```

Configuración final usada por la ruta calibrada:

```text
semantic completion tokens  3200
mapping tokens              1200
relationship tokens          512
multiplicity tokens          128
hybrid enabled              true
min semantic classes           4
fallback-to-semantic       false
```

El 2B quedó descartado para la pizarra real densa porque 2/2 intentos truncaron aun con un budget mayor. El 4B completó la salida estructurada y mantuvo el pico de VRAM alrededor de 5.4 GiB.

---

## 7. Por qué el pipeline dejó de ser VLM-only

La pizarra realista mostró que el VLM podía leer muy bien contenido local, pero era menos estable al realizar simultáneamente:

- OCR/lectura;
- localización de cajas;
- seguimiento de líneas;
- interpretación de cruces;
- atribución de multiplicidades pequeñas.

Se ensayaron varias alternativas:

- prompt geométrico más detallado;
- segunda pasada global de relaciones;
- crop completo;
- tiles.

Ninguna resolvió consistentemente el problema de topología. La decisión arquitectónica fue separar percepción geométrica y semántica.

---

## 8. Cal-010 y el aprendizaje de la frontera espacial

La primera versión hybrid-CV pedía al VLM bbox de clases. Las coordenadas no coincidían suficientemente con las cajas reales y la máscara geométrica quedaba degradada.

La conclusión no fue “subir tokens”, sino retirar esa autoridad al modelo.

Cal-010 queda como antecedente superseded.

---

## 9. Cal-011 — regiones físicas de clases

OpenCV pasa a detectar las cajas físicas `B1..B6`; Qwen sólo realiza un mapping cerrado entre geometry IDs y refs semánticas.

Resultado focal:

| Región | BBox | IoU aprox. |
|---|---|---:|
| B1 Categoría | `(349,182 166x290)` | 0.9632 |
| B2 Préstamo | `(542,234 254x350)` | 0.9180 |
| B3 Usuario | `(383,723 211x245)` | 0.9618 |
| B4 Biblioteca | `(652,753 199x246)` | 0.9523 |
| B5 Libro | `(485,1134 291x347)` | 0.9659 |
| B6 Autor | `(246,1203 215x255)` | 0.9390 |

La mapping debe ser biyectiva y cerrada; si falta una región o se repite una ref, se rechaza.

---

## 10. Cal-012 — topología física

OpenCV reconstruye la geometría de conectores sin conocer nombres UML.

El oracle físico auditado de la foto contiene:

1. Autor — Libro;
2. Biblioteca — Libro;
3. Biblioteca — Usuario;
4. Categoría — Libro;
5. Préstamo — Libro;
6. Préstamo — Usuario.

Un oracle anterior incluía Usuario — Libro, pero la auditoría visual demostró que ese conector no estaba dibujado. Se corrigió el oracle en lugar de ajustar código contra una relación inexistente.

Gate final:

```text
physical pairs = 6/6
unexpected     = 0
```

Desde ese punto, geometry posee la topología física.

---

## 11. Cal-013 — relaciones per-edge

La anotación global se sustituyó por inferencias pequeñas, independientes y stateless.

### Tipo/marker

Cada edge confirmado por OpenCV recibe exactamente una clasificación. El VLM no puede responder “no existe” para borrar un edge confirmado.

### Multiplicidad por endpoint

Cada extremo se transcribe por separado para evitar contaminación A/B.

Qwen devuelve `rawLabel`; Java interpreta la sintaxis.

Ejemplos:

```text
*    -> 0..*
1    -> 1..1
1..* -> 1..*
0..* -> 0..*
```

---

## 12. El caso E6-B y por qué no se usó una regla semántica

El único error que permaneció durante varias iteraciones fue:

```text
Biblioteca — Libro (aggregation)
endpoint Biblioteca
false positive = "1"
```

El `1` sí existía en la foto, pero pertenecía al conector Biblioteca — Usuario.

No se adoptó una regla `aggregation => no multiplicity`, porque sería incorrecta en UML general. La solución debía resolver ownership visual de forma genérica.

---

## 13. Competitor-conditioned transcription

Después de probar attribution post-transcription con varios layouts, se movió parte de la decisión antes de leer el texto.

Para el edge actual y sus competidores se generan footprints. Sólo se suprime evidencia que pertenece exclusivamente a competidores.

```text
current:    length 90, half-width 36, contact radius 40
competitor: length 90, half-width 28, contact radius 40

suppression = competitorMask AND NOT currentSupportMask
```

La selección de competitors es footprint-aware. Esto corrigió el caso en el que una centerline estaba apenas fuera del crop pero su evidencia visual sí entraba.

Resultado:

- E4 conserva el `1` legítimo;
- E6 deja de ver ese `1` como evidencia propia.

---

## 14. Benchmark focal final

Comando utilizado:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-whiteboard.ps1 `
  -VisionMode hybrid-cv `
  -ImageStrategy original `
  -Attempts 3 `
  -ModelLabel "Qwen3-VL-4B Q4_K_M" `
  -TimeoutSeconds 180 `
  -MaxCompletionTokens 3200 `
  -HybridLocalizationTokens 1200 `
  -HybridRelationshipTokens 512 `
  -HybridMultiplicityTokens 128 `
  -VerboseAttempts
```

Resultado:

```text
[library-whiteboard-realistic #1] PASS · Exact
[library-whiteboard-realistic #2] PASS · Exact
[library-whiteboard-realistic #3] PASS · Exact

Transport:      100.0 %
Schema valid:   100.0 %
Grounding:      100.0 %
Classes:        18/18 expected · 0 unexpected
Attributes:     51/51 expected · 0 unexpected
Relationships:  18/18 expected · 0 unexpected
Multiplicity:   30/30 expected · 0 unexpected
Semantic exact: 100.0 %
Safety invalid: 100.0 %
GPU peak:       5455 MiB
Elapsed:        8m45s
```

Esta evidencia muestra estabilidad focal en tres repeticiones de la misma fotografía.

---

## 15. Cal-014 — promoción a producción

Después de retirar código experimental superseded y separar diagnostics pesados del runtime normal:

```text
hybrid enabled = true
minClasses = 4
```

Tests de routing verificaron:

- `enabled=false` -> semantic-only;
- 3 clases -> semantic-only;
- exactamente 4 -> hybrid-CV;
- más de 4 -> hybrid-CV.

Producción no genera relationship sheets/mask diagnostics innecesarios; benchmark sí puede hacerlo.

---

## 16. Cal-015 — fail-closed

Durante Cal-014 se conservó temporalmente `fallback-to-semantic=true`. La revisión arquitectónica posterior mostró que ese fallback podía devolver una topología que no hubiera pasado por la autoridad OpenCV.

La política final es:

```text
fallback-to-semantic=false
```

Si hybrid-CV falla:

- `TRANSPORT` conserva la reason;
- fallos contractuales/internos -> `OUTPUT_CONTRACT`;
- no existe semantic proposal de respaldo;
- no hay preview;
- no hay Apply;
- la UI muestra retry.

`fallback=true` permanece sólo como rollback/debug explícito.

---

## 17. Cal-016 — E2E del producto real

La evidencia antigua aplicaba el comando directamente en test. Cal-016 la alineó con la autoridad productiva real:

```text
image plan
 -> BATCH
 -> ProjectOperationRequest
 -> ProjectCollaborationService.apply
 -> ProjectCommandExecutor
 -> validate
 -> save
 -> reopen
```

### Caso determinista usado

Clases:

```text
Cliente
- email : STRING

Factura
- total : DECIMAL
```

Relación:

```text
Cliente -> Factura
ASSOCIATION
Cliente: 1..1
Factura: 0..*
```

### Propiedades verificadas

- revisión inicial 0;
- `plan()` no persiste;
- preview del mismo BATCH;
- Apply produce revisión 1;
- reopen produce revisión 1;
- documento reabierto == preview;
- múltiples child commands siguen siendo una sola revisión.

### Concurrencia

Un cambio externo antes del Apply deja el plan stale:

```text
REVISION_CONFLICT
currentRevision = 1
```

Después del rechazo sólo permanece el cambio externo; no se persiste `Cliente`, `Factura` ni la relación del Assistant.

Un cambio de revisión durante la inferencia también impide devolver `READY`.

---

## 18. Cal-017 — labels visuales vs. identificadores de código

El primer smoke manual sobre la misma imagen del benchmark falló en `ProjectDocumentValidator`:

```text
CLASS_NAME_INVALID
CLASS_NAME_INVALID
ATTRIBUTE_NAME_INVALID
```

La causa era que el VLM podía transcribir literalmente:

```text
Categoría
Préstamo
añoPublicacion
```

mientras el dominio exige:

```text
^[A-Za-z_][A-Za-z0-9_]*$
```

### Decisión

La evidencia permanece fiel a la imagen; la canonicalización ocurre al cruzar hacia el plan de dominio.

Ejemplos esperados:

```text
Categoría        -> Categoria
Préstamo         -> Prestamo
añoPublicacion    -> anoPublicacion
Línea Pedido     -> Linea_Pedido
2FA              -> _2FA
```

No se relaja `ProjectDocumentValidator` y no se obliga al VLM a omitir diacríticos.

### Benchmark executable gate

Se añadió una segunda dimensión de validez:

```text
semantic comparison
AND
resolve -> preview -> ProjectDocumentValidator
```

De este modo una propuesta no puede declararse Exact sólo porque sea semánticamente equivalente si después el producto no puede aplicarla.

Después de la corrección, la misma pizarra real completó correctamente el smoke de producto.

---

## 19. UX smoke final

### Check 1 — Happy path

Resultado: PASS.

La imagen realista:

- llegó a `READY`;
- mostró preview;
- no mutó antes de Apply;
- se aplicó;
- persistió después de reload/reopen.

### Check 2 — Preparación de imagen

Resultado: PASS.

Rotate/crop/reset/cancel no mutaron el modelo antes de Apply.

### Check 3 — Fail-closed + retry

Resultado: PASS.

Con Vision detenido:

- error visible;
- sin preview;
- sin mutación;
- `Analizar nuevamente` disponible.

Al restaurar el runtime, retry recuperó el flujo.

### Check 4 — Plan stale

Resultado: PASS.

Un cambio concurrente invalidó el plan antiguo; no hubo aplicación parcial y fue posible regenerar el plan.

### Check 5 — Permisos

Resultado: PASS.

- OWNER: analizar/aplicar;
- EDITOR: analizar/aplicar;
- sin permiso de edición: no muta el proyecto.

---

## 20. Estados seguros del caso de uso

CU-09 diferencia explícitamente:

```text
READY
NO_CHANGES
NO_ACTIONABLE_UML
VISION/TRANSPORT
VISION/OUTPUT_CONTRACT
REVISION_CONFLICT
```

Esto evita representar cualquier respuesta del modelo como una propuesta aplicable.

---

## 21. Trazabilidad arquitectónica

| Necesidad | Componente/decisión |
|---|---|
| validar imagen | `AssistantImageInputValidator` |
| normalizar | `VisionImageNormalizer` |
| abstraer proveedor | `VisionModelGateway` |
| routing productivo | `HybridVisionModelGateway` |
| leer semántica | Qwen3-VL / `LlamaCppVisionModelGateway` |
| detectar class regions | `OpenCvUmlClassRegionDetector` |
| mapear Bx -> classRef | `VisionGeometryClassMappingValidator` |
| reconstruir topología | `OpenCvUmlDiagramGeometryAnalyzer` |
| anotar edges | `LlamaCppVisionHybridModelGateway` |
| condicionar multiplicidades | `VisionRelationshipEvidenceSheetRenderer` |
| parsear multiplicidad | `VisionMultiplicityParser` |
| ensamblar proposal | `VisionHybridProposalAssembler` |
| grounded compilation | `VisionProposalCompiler` |
| adaptar identifiers | canonicalización determinista en frontera compiler |
| generar comando | `UmlAssistantCommandResolver` |
| preview | resolver/executor temporal |
| Apply autoritativo | `ProjectCollaborationService` |
| mutación | `ProjectCommandExecutor` |
| validar dominio | `ProjectDocumentValidator` |

---

## 22. Principios que quedaron establecidos

1. La evidencia visual y el identificador de código no son necesariamente el mismo string.
2. OpenCV/Java debe poseer topología física cuando el diagrama es suficientemente denso para usar hybrid-CV.
3. Un VLM no debe decidir existencia física de un edge después de que geometry lo confirmó.
4. Las multiplicidades se leen por endpoint, no por par global.
5. Attribution visual no sustituye un parser determinista.
6. No se introducen reglas de dominio falsas para resolver errores de percepción.
7. Un fallback no puede saltarse la autoridad establecida por la ruta principal.
8. Un benchmark semántico debe demostrar también que la salida es ejecutable por el producto.
9. Plan y preview no persisten.
10. Apply usa la misma autoridad colaborativa que cualquier otra edición.
11. BATCH es atómico respecto a revisión/persistencia.
12. Un plan stale se descarta; no se rebasea silenciosamente.

---

## 23. Evidencia de aceptación consolidada

La copia estructurada vive en `cu09-acceptance.json`.

Evidencia positiva registrada:

- focal hardening 3/3 Exact;
- schema/grounding/safety 100 % en esa corrida;
- 6/6 physical relationships;
- multiplicidades finales exactas en el fixture;
- backend full gates reportados durante Cal-014/015/016;
- E2E canónico determinista;
- fail-closed y retry;
- smoke manual 5/5;
- misma imagen real aplicada/persistida después de Cal-017.

---

## 24. Validaciones no ejecutadas antes del cierre

Para no falsear la evidencia, el cierre deja registradas como diferidas:

1. dos pizarras reales adicionales para estimar una tasa de generalización;
2. ejecución archivada post-Cal-017 de `assistant-vision-acceptance.ps1` completa;
3. build final separado del frontend después del último ajuste, aunque la aplicación se levantó y el smoke interactivo funcionó.

Estas actividades fortalecen la confianza estadística/transicional, pero no representan una pieza funcional faltante del caso de uso según la decisión de aceptación del 5 de septiembre de 2026.

---

## 25. Decisión de cierre

Con la evidencia disponible se acepta CU-09 funcionalmente, con riesgo residual explícito sobre amplitud estadística del benchmark.

```text
CU-09: CERRADO
C2-cu09-001: COMPLETADO
C2-cu09-002: COMPLETADO
C2-cu09-003: COMPLETADO
Cal-011..017: CERRADAS
Ciclo 2: CERRADO
```

El próximo trabajo funcional debe abrir un nuevo Ciclo PUDS.
