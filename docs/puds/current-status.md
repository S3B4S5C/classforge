# Estado actual PUDS

**Fecha de corte:** 29 de agosto de 2026.

```text
Fase PUDS: Elaboración
Ciclo 1: CERRADO
Ciclo 2: ABIERTO
CU-31: CERRADO
CU-09: EN PROGRESO
```

## Objetivo alcanzado del Ciclo 1

Estabilizar la arquitectura ejecutable de ClassForge mediante un incremento que demuestre que el mismo modelo UML canónico puede ser manipulado desde UI manual, colaboración en tiempo real y lenguaje natural/voz sin introducir rutas alternativas de mutación.

## Casos cerrados en el Ciclo 1

| Caso | Estado | Resultado principal |
|---|---|---|
| CU-01 Crear proyecto | CERRADO | proyecto persistente con UUID y owner |
| CU-02 Abrir/guardar | CERRADO | `ProjectDocument`, revisión y conflicto |
| CU-03 Modelado manual | CERRADO | clases, atributos, relaciones, multiplicidades, JointJS |
| CU-04 Validar UML | CERRADO | validación explícita y diagnósticos |
| CU-05 Undo/Redo | CERRADO | Command Bus e historial reversible |
| CU-06 Colaboración realtime | CERRADO | STOMP, servidor autoritativo y revisión |
| CU-07 Presencia | CERRADO | presencia efímera, selección y cursor |
| CU-08 Voz/lenguaje natural a UML | CERRADO | texto/voz -> plan -> BATCH -> preview -> aplicar |
| CU-28 Registrar cuenta | CERRADO | identidad local, BCrypt y JWT |
| CU-29 Iniciar sesión | CERRADO | autenticación stateless |
| CU-30 Proyectos propios | CERRADO | ownership persistente; posteriormente ampliado por CU-31 |

## Ciclo 2 — CU-31

C2-cu31-001 está completado:

- `ProjectMembership` EDITOR persistente;
- `ProjectAccessRole` OWNER/EDITOR/NONE;
- política central de acceso;
- REST y biblioteca con proyectos propios + compartidos;
- renombrar reservado al OWNER.

C2-cu31-002 está completado:

- `ProjectInvitation` persistente por correo normalizado;
- invitaciones internas sin SMTP/Internet;
- pending/accept/decline/cancel;
- aceptación transaccional que crea membership EDITOR;
- bandeja de invitaciones en biblioteca;
- dialog de Colaboradores en workspace;
- owner/editors visibles según permisos;
- aceptación sin modificar revisión UML.

C2-cu31-003 está completado:

- STOMP usa directamente la política central: SUBSCRIBE exige lectura y SEND exige edición;
- OWNER y EDITOR reales intercambian operaciones entre cuentas distintas; NONE es rechazado;
- presencia revalida acceso también dentro del controller y se prueba OWNER + EDITOR;
- Assistant texto/voz queda membership-aware y NONE se rechaza antes de invocar LLM/STT;
- invitaciones serializan mutaciones por proyecto para cerrar carreras de invite/accept;
- CU-31 queda formalmente CERRADO.

## Arquitectura validada

```text
adaptador UI / realtime / Assistant
              |
              v
       UmlCommand / BATCH
              |
              v
        Command Bus
              |
              v
     ProjectDocument
       /          \
  UmlModel    DiagramLayout
```

Además:

- backend autoritativo;
- revisión exacta para operaciones colaborativas;
- presencia fuera de persistencia;
- LLM sin autoridad de escritura;
- preview antes de aplicar IA;
- grounding y validación determinista;
- membership separada de ownership;
- invitación separada del permiso efectivo.

## Persistencia

- Spring Data JPA/Hibernate;
- H2 archivo en desarrollo;
- H2 memoria en tests;
- PostgreSQL permanece planificado;
- `project_memberships` y `project_invitations` son incorporaciones aditivas.

## Limitaciones vigentes

1. No existe eliminación de membership activa ni revocación inmediata de una sesión STOMP ya conectada; sigue fuera del alcance de CU-31.
2. CU-09 — Imagen → UML — EN PROGRESO. Cal-011 class regions, Cal-012 topology, Cal-013 focal hardening, Cal-014 production activation, Cal-015 hybrid fail-closed policy y Cal-016 image product E2E están cerrados. `library-whiteboard-realistic` obtuvo 3/3 Exact; hybrid-CV está habilitado por defecto para >=4 clases semánticas, los diagramas pequeños siguen semantic-only y un fallo hybrid devuelve error `VISION` con retry. Evidencia determinista Cal-016: image plan non-mutating, preview == persisted/reopened document, BATCH = exactly one revision, stale Apply rejected with no partial mutation, revision change during inference rejected y real-VLM acceptance wired to canonical collaboration Apply. Pendiente: PUNTO 5 UX smoke real, PUNTO 6 broader boards + acceptance, PUNTO 7 final builds y PUNTO 8 closure.
3. XMI/Enterprise Architect no está implementado.
4. Modelo relacional y generadores no están implementados.
5. La aplicación generada y su asistente de voz aún no existen.
6. Auditoría histórica completa CU-26 permanece pendiente.
7. `docs/uml/` tiene catálogo preparado, pero los diagramas académicos aún deben elaborarse.

## Siguiente paso

```text
CU-09 — Imagen -> UML (EN PROGRESO)
  C2-cu09-001 -> COMPLETADO: contrato de entrada visual + compiler/preview canónico
  C2-cu09-002 -> COMPLETADO TÉCNICAMENTE: runtime + schema + benchmark; Qwen3-VL-4B Q4_K_M seleccionado
  C2-cu09-003 -> IMPLEMENTADO: UX final + hardening + E2E/acceptance tooling
  Cal-011 -> CLOSED: class regions
  Cal-012 -> CLOSED: physical topology
  Cal-013 -> CLOSED: focal hardening
  Cal-014 -> CLOSED: hybrid-CV production activation
  Cal-015 -> CLOSED: hybrid fail-closed policy
  Cal-016 -> CLOSED: image product E2E
```

CU-31 está cerrado. El Ciclo 2 permanece ABIERTO hasta cerrar CU-09.

## Hardening CU-08 previo a CU-09 — evidencia de migración

fix-013 introdujo el candidato native tools y lo endureció en v1.2/v1.3. La evidencia progresó de `tools=70 %` a `82 %` y finalmente, en la corrida de decisión de 20 intentos por categoría, `tools=100 %` frente a `legacy=94 %`, con safety en `100 %`. Esa evidencia autoriza el cutover de fix-014.

<!-- CU08-FIX-014-NATIVE-TOOLS-CUTOVER -->
### CU-08 hardening final — native tools oficial

`C2-cu08-fix-014` completa el cutover arquitectónico previo a CU-09. El benchmark A/B de decisión con 20 intentos por categoría obtuvo `legacy=94 %`, `tools=100 %`, `SAFETY_UNKNOWN_REFERENCE=100 %`. El planner JSON legacy se retira del runtime; Qwen2.5-3B-Instruct Q4_K_M + llama.cpp native tools pasa a ser la única planificación LLM. Texto y voz convergen en el mismo planner. Se incorpora planificación compuesta mediante previews efímeros y una suite holdout separada de la regresión conocida. CU-08 permanece CERRADO y CU-09 continúa siendo el siguiente caso funcional del Ciclo 2.


<!-- CU08-FIX-014-V1.3-HOLDOUT-HARDENING -->
### Evidencia holdout y hardening jerarquico

La primera ejecucion holdout posterior al cutover obtuvo 39.4 % (13/33). El resultado no reabre CU-08 funcionalmente, pero bloqueo el inicio de CU-09 hasta corregir el hardening: la mayoria de los fallos eran `LLM HTTP 400` porque el fallback de catalogo completo generaba ~5.1k tokens sobre un contexto de 4096; ademas, Qwen devolvia varias `tool_calls` validas en peticiones compuestas y el planner exigia artificialmente exactamente una.

Fix-014 v1.3 sustituye ese fallback por routing native jerarquico (`route_uml_request` sin enums del proyecto -> una tool UML pesada por step), acepta/deferiere llamadas extra de la misma ronda y liga multiplicidades explicitas desde el texto. CU-09 permanece como siguiente caso funcional, condicionado a una nueva corrida `regression` + `holdout` verde.


<!-- CU08-FIX-014-V1.4-HOLDOUT-ADJUDICATION -->
### Segunda evidencia holdout — route adjudication

Tras fix-014 v1.3 el holdout subio a `19/33 = 57.6 %`: desaparecieron los errores por contexto y `SAFETY_UNKNOWN_REFERENCE` quedo en `100 %`. Los fallos restantes mostraron pasos routed extra en peticiones simples y bursts de 20+ tool calls en un unico step.

Fix-014 v1.4 agrega adjudicacion project-aware de la ruta, colapsa peticiones simples a una unica operacion, ordena compuestos por dependencia, descarta tool calls repetidas y reintenta una vez cuando llama.cpp ignora `tool_choice=required` y responde texto. CU-09 continua bloqueado hasta repetir holdout y regression con esta revision.

<!-- CU08-FIX-014-V1.5-HOLDOUT-STATELESS -->
### Tercera evidencia holdout — compound stateless

Tras fix-014 v1.4 el holdout subió a `28/33 = 84.8 %`; `RENAME_CLASS`, `DELETE_CLASS`, `ADD_ATTRIBUTES`, `UPDATE_ATTRIBUTE`, `DELETE_ATTRIBUTE`, `CREATE_RELATIONSHIP`, `DELETE_RELATIONSHIP` y `SAFETY_UNKNOWN_REFERENCE` quedaron en `100 %`. Los únicos fallos fueron una creación natural mal detectada como compound, un extremo de multiplicidad conversacional y las tres solicitudes multi-tool dentro del gateway.

Fix-014 v1.5 elimina el historial `assistant/tool` entre steps compuestos y usa únicamente el `ProjectDocument` efímero como estado, corrige la detección `Añade ... una nueva clase` y amplía el binding de multiplicidad `ninguna/varias`. CU-09 continúa bloqueado hasta repetir holdout y regression.

<!-- CU08-FIX-014-V1.6-HOLDOUT-LAZY-PARSE -->
### Cuarta evidencia holdout — aislamiento final del gateway

Tras fix-014 v1.5 el holdout alcanzo `30/33 = 90.9 %`. `CREATE_CLASS`, `RENAME_CLASS`, `DELETE_CLASS`, `ADD_ATTRIBUTES`, `UPDATE_ATTRIBUTE`, `DELETE_ATTRIBUTE`, `CREATE_RELATIONSHIP`, `UPDATE_RELATIONSHIP`, `DELETE_RELATIONSHIP` y `SAFETY_UNKNOWN_REFERENCE` quedaron en `100 %`. El unico ERROR fue `MULTI_TOOL_COMPOUND=0/3`.

Los tres fallos compuestos fueron `UnexpectedEndOfInputException`: el gateway parseaba llamadas repetidas sobrantes y una de ellas quedaba truncada por el limite de completion, aunque la primera tool call util ya fuera valida. Fix-014 v1.6 aplica parse perezoso de la primera llamada compatible y un unico retry de truncamiento con 512 completion tokens. CU-09 permanece bloqueado hasta repetir holdout y regression con esta revision.

<!-- CU08-FIX-014-V1.6-FINAL-HOLDOUT -->
### Evidencia final holdout post-v1.6

La repetición de `assistant-tool-holdout` después de fix-014 v1.6 obtuvo `33/33 = 100.0 %`. Las once categorías quedaron en `100 %`, incluyendo `SAFETY_UNKNOWN_REFERENCE=3/3` y `MULTI_TOOL_COMPOUND=3/3`, y la tarea terminó con `AssistantE2EReliabilityIntegrationTest PASSED` y `BUILD SUCCESSFUL`.

Con esta corrida queda cerrada la puerta holdout que bloqueaba CU-09: el planner oficial demuestra generalización sobre redacciones no usadas para ajustar fix-013, mantiene fail-closed y soporta peticiones compuestas. La evidencia holdout habilita la apertura de `C2-cu09-001`. La corrida `assistant-tool-regression -Attempts 20` post-v1.6 permanece como confirmación recomendada de no regresión del Assistant textual, pero CU-09 ya puede avanzar sin alterar ese planner. La arquitectura visual reutiliza la misma IR/preview/BATCH/Command Bus.

<!-- C2-CU09-001-IMAGE-VISION-CONTRACT -->
## CU-09 abierto — C2-cu09-001

C2-cu09-001 implementa la frontera visual sin seleccionar todavía un VLM definitivo. El backend acepta PNG/JPEG/WEBP, valida firma/tamaño/dimensiones, normaliza PNG/JPEG, conserva `baseRevision`, expone `VisionModelGateway` y compila `VisionUmlProposal` con evidence/provenance hacia `AssistantSemanticPlan`. La salida reutiliza `UmlAssistantCommandResolver`, BATCH, preview, `ProjectDocumentValidator` y Apply/Command Bus.

El frontend ya puede seleccionar y previsualizar una imagen y consumir el endpoint `/assistant/image/plan`. Mientras C2-cu09-002 no configure un VLM, `UnconfiguredVisionModelGateway` responde explícitamente en etapa `VISION`; no existe fallback que escriba UML directamente ni se persisten imágenes. CU-09 queda EN PROGRESO.

<!-- C2-CU09-002-QWEN3-VL-RUNTIME-BENCHMARK -->
## CU-09 — C2-cu09-002 implementado, evidencia local pendiente

C2-cu09-002 conecta `VisionModelGateway` con un `LlamaCppVisionModelGateway` real en `127.0.0.1:8094`. El baseline documentado es Qwen3-VL-2B-Instruct Q4_K_M + `mmproj` Q8_0; Qwen3-VL-4B Q4_K_M queda como challenger medible. El planner textual de CU-08 continúa separado en 8092 y whisper.cpp en 8093.

La petición multimodal usa `image_url` Base64 y salida restringida por JSON Schema. El prompt visual no recibe UUID de dominio y la respuesta se deserializa directamente a `VisionUmlProposal`; no existe reparación heurística de JSON. Health agrega `readyForImage` y exige alias correcto + `modalities.vision=true` en `/props`.

Se incorporan smoke multimodal, regression y holdout image→UML con reportes separados de transporte, schema, grounding, clases, atributos, relaciones, multiplicidades, semantic exact, safety, latencia y VRAM observada. **Este documento no marca todavía C2-cu09-002 como COMPLETADO:** falta ejecutar la evidencia contra el VLM real en la GTX 1660 SUPER y registrar el resultado antes de avanzar al cierre de CU-09.


<!-- C2-CU09-003-VISION-UX-HARDENING -->
## CU-09 — C2-cu09-003 implementado, validación final diferida

C2-cu09-003 completa el alcance funcional de Imagen → UML sin adelantar artificialmente la selección/calibración del VLM. La UI incorpora selección, drag & drop, pegado desde portapapeles, captura de cámara cuando el navegador la soporte, rotación, recorte conservador, reset, cancelación/retry y overlay de `VisionEvidence` cuando existen bounding boxes. La confidence se presenta únicamente como señal informativa; nunca autoriza Apply.

El backend distingue `READY`, `NO_CHANGES` y `NO_ACTIONABLE_UML`. Propuestas vacías o diagramas ya representados no generan `BATCH` vacío, y conflictos explícitos contra atributos/relaciones existentes se omiten con warning en lugar de producir updates silenciosos. Bounding boxes opcionales se validan además contra las dimensiones normalizadas de la imagen. Toda mutación accionable continúa convergiendo en `UmlAssistantCommandResolver` → BATCH → preview → Apply → Command Bus → `ProjectDocument`.

Se añade un dataset `hardening`, runner exploratorio sin gates y una suite E2E/aceptación real que comprueba imagen → plan → comando canónico → persistencia/reapertura. **Estas pruebas dependientes del VLM no se ejecutan al aplicar el parche.** La estrategia deliberada es terminar primero CU09-003 y después iterar modelo/prompt con `assistant-vision-explore.ps1`. Solo una corrida posterior de `assistant-vision-acceptance.ps1` con gates verdes permitirá cambiar `CU-09` a `CERRADO`.


<!-- CU09-CAL-001-PROMPT-SAFETY-UML-SEMANTICS -->
### Primera calibración real de Vision — prompt/safety

La primera corrida exploratoria `hardening` con el runtime multimodal real confirmó infraestructura estable (`transport=100 %`, `schema=100 %`, `grounding=100 %`) y un pico observado de 5254 MiB de VRAM. La calidad semántica inicial fue `42.9 %`, con `safety invalid=0 %`; por tanto CU-09 permanece EN PROGRESO y no se ejecuta todavía el gate de aceptación.

Los fallos son mayormente sistemáticos: tipos explícitos degradados a STRING, multiplicidades omitidas, dirección de GENERALIZATION invertida e imagen no-UML convertida en comandos. C2-cu09-cal-001 endurece el prompt con un gate negativo explícito, mapeo literal de tipos, reglas de multiplicidad y dirección UML. Además corrige el oracle del caso `shadow-association`: el fixture visible contiene `Cliente(email:String) — Factura(total:Decimal)`, no `Pedido — LineaPedido`.

La siguiente acción es repetir `assistant-vision-explore.ps1` con el mismo Qwen3-VL-2B antes de considerar el challenger 4B.


<!-- CU09-CAL-006-MODEL-SELECTION -->
### CU-09 — selección del VLM y Cal-006

La comparación focal de la pizarra real descarta Qwen3-VL-2B Q4_K_M como runtime principal: con 4000 completion tokens truncó 2/2 respuestas (`OUTPUT_CONTRACT`). Qwen3-VL-4B Q4_K_M completó 2/2 respuestas con JSON válido usando 3200 tokens/180 s y mantuvo picos de VRAM compatibles con la GTX 1660 SUPER (~5.46 GiB observados). Antes de esta pizarra, el 4B obtuvo regression 100 %, holdout 100 % y safety 100 %.

Cal-006 fija por tanto Qwen3-VL-4B Q4_K_M como modelo visual seleccionado y concentra la calibración restante en diagramas densos: evidencia de atributos, tipos no explícitos, trazado físico de conexiones, multiplicidades y coherencia de `NO_ACTIONABLE_UML`. CU-09 permanece EN PROGRESO hasta que la pizarra focal y la acceptance completa queden verdes; seleccionar el modelo no equivale a cerrar el caso de uso.


<!-- CU09-CAL-007-DENSE-TOPOLOGY-COMPARATOR -->
### CU-09 — Cal-007, topología densa y medición parcial

La corrida focal post-Cal-006 ya llega a plan semántico con el 4B: transporte/schema/grounding 100 %, seis clases y sus atributos reconocidos, agregación `Biblioteca—Libro` correcta, pero una relación `Usuario—Libro` omitida, una `Categoría—Préstamo` espuria y multiplicidades manuscritas todavía ausentes.

Cal-007 mantiene Qwen3-VL-4B Q4_K_M como modelo seleccionado y no relaja fail-closed. El benchmark pasa a normalizar diacríticos igual que el backend y considera `ASSOCIATION` no dirigida conservando las multiplicidades ligadas a cada extremo. También reporta elementos matched/expected/unexpected para clases, atributos, topología y multiplicidades, evitando que un único diagrama parcialmente correcto se vea como 0 % absoluto. El prompt sólo refuerza seguimiento físico de líneas y lectura local de multiplicidades. CU-09 sigue EN PROGRESO hasta que la pizarra focal y acceptance queden verdes.

<!-- CU09-CAL-008-DENSE-TWO-PASS -->
### CU-09 — Cal-008, segunda pasada exclusiva de relaciones

Cal-007 demostró que forzar más razonamiento geométrico dentro del prompt monolítico empeoraba la topología de la pizarra real aunque clases/atributos siguieran al 100 %. Cal-008 conserva la medición semántica mejorada de Cal-007, revierte únicamente ese refuerzo relacional al prompt conservador de Cal-006 y añade un modo two-pass para diagramas densos.

La primera inferencia sigue produciendo clases/atributos y un proposal completo. Cuando hay al menos cuatro clases confirmadas, una segunda llamada al mismo Qwen3-VL-4B analiza solo conectores usando una lista cerrada de refs y un JSON Schema relationships-only. La salida no puede introducir nuevas clases: refs desconocidos, duplicados contractuales, truncamiento o JSON inválido fallan cerrado. El merge sustituye solo las relaciones; toda mutación continúa por grounding → compiler → IR → preview → Command Bus.

`assistant-vision-whiteboard.ps1` usa `two-pass` por defecto y permite `-VisionMode single-pass` para comparar ambos comportamientos con reportes separados. CU-09 continúa EN PROGRESO hasta que el caso realista y la acceptance final queden verdes.


<!-- CU09-CAL-009-DENSE-INPUT-CROP-TILING -->
### CU-09 — Cal-009, rollback two-pass y experimento crop/tiling

La corrida focal de Cal-008 confirmó que `relationships-only` no mejora el caso realista: por intento mantuvo las seis clases y 17 atributos, pero solo recuperó ~4/7 relaciones, añadió una espuria y siguió en 0/12 multiplicidades. El single-pass conservador de Cal-006 había sido mejor (~6/7 relaciones + 1 extra).

Cal-009 desactiva `dense-two-pass` por defecto y conserva su código solo como experimento opt-in. La calibración se desplaza a la entrada visual: `assistant-vision-whiteboard.ps1` puede comparar la fotografía original, una versión `board-crop` orientada y recortada, y cuatro `tiles` solapados/ampliados. Crop/tiles existen únicamente en el benchmark; producción continúa con una única imagen normalizada → gateway → grounding → compiler → preview/Command Bus. Qwen3-VL-4B Q4_K_M permanece seleccionado y CU-09 sigue EN PROGRESO hasta validar la pizarra y la acceptance completa.


<!-- CU09-CAL-010-HYBRID-CV-GEOMETRY -->
## CU-09 — Cal-010 híbrido CV + VLM local

Las calibraciones Cal-007/008/009 demostraron que añadir más razonamiento textual, una segunda pasada global o un crop completo no mejora de forma estable la topología de la pizarra. Se adopta por ello un experimento híbrido inspirado en pipelines open source de reconstrucción de diagramas: **la geometría física deja de ser autoridad del VLM**.

Cal-010 incorpora OpenCV CPU en Java y separa responsabilidades:

```text
Qwen3-VL-4B -> clases + atributos
Qwen cerrado -> bbox de las clases ya confirmadas
OpenCV       -> máscara de cajas + líneas + componentes + endpoints candidatos
Qwen local   -> marker UML + multiplicidades sobre evidence sheet
Java         -> merge fail-closed
              -> VisionUmlProposal
              -> Grounding -> Compiler -> Preview -> Command Bus
```

Históricamente, el modo `hybrid-cv` permanecía deshabilitado mientras se validaba con `library-whiteboard-realistic`. Cal-014 lo activa por defecto para diagramas densos. OpenCV no crea clases ni nombres y Qwen local no puede cambiar los pares de endpoints propuestos por la geometría. Componentes de línea que tocan más de dos clases se consideran ambiguos y se omiten; los cruces geométricos no se convierten automáticamente en junctions.

La primera ejecución recomendada es únicamente el caso focal. Los diagnósticos `threshold.png`, `segments.png`, `overlay.png`, `relationship-sheet.png`, `geometry.json`, `localization.json` y `annotation.json` permiten atribuir cada fallo a localización, geometría o anotación local antes de promover el modo híbrido. CU-09 permanece EN PROGRESO.

<!-- CU09-CAL-011-CV-FIRST-CLASS-REGIONS -->
### CU-09 — Cal-011, detección CV-first de cajas

La primera corrida Cal-010 confirmó que el VLM no debe localizar cajas mediante coordenadas: produjo bboxes uniformes/desplazados y OpenCV terminó enmascarando zonas incorrectas. Los diagnósticos mostraron, sin embargo, que el threshold CV conserva con claridad las seis cajas reales.

Cal-011 mueve la autoridad espacial a OpenCV. Java detecta `B1..Bn` físicamente y Qwen3-VL-4B sólo realiza un mapeo cerrado `geometryId -> classRef`, sin poder cambiar `x/y/width/height`. El mapeo debe ser biyectivo y cubrir exactamente el mismo número de cajas que clases semánticas. El modo `-HybridGeometryOnly` permite validar primero las seis cajas y el mapping antes de ejecutar anotación local de relaciones. Qwen3-VL-4B Q4_K_M permanece seleccionado y CU-09 continúa EN PROGRESO.
