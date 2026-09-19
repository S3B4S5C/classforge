# Historial acumulativo de actualizaciones de producto

Este archivo conserva, sin reinterpretar, las actualizaciones históricas que anteriormente estaban anexadas al documento de producto vigente. Las afirmaciones de estado aquí son válidas para su corte histórico y **no sustituyen** `../product.md`, `../../puds/current-status.md` ni `../../puds/use-cases.md`.

# Actualizacion de producto — Autenticacion, propiedad y colaboracion

## Identidad de usuario

ClassForge incorpora cuentas locales de usuario mediante registro e inicio de sesion.

Cada cuenta posee UUID, nombre visible, correo unico, hash de contrasena y fecha de creacion.

Las contrasenas nunca se almacenan en texto plano. El backend utiliza Spring Security y BCrypt.

## Sesion

La API utiliza autenticacion Bearer mediante JWT firmado por el backend.

El frontend puede persistir el access token en `localStorage`. Este almacenamiento representa exclusivamente la sesion del navegador; no determina la propiedad ni la existencia de proyectos.

Borrar `localStorage` implica cerrar la sesion local. Al autenticarse nuevamente con la misma cuenta, el backend vuelve a entregar los proyectos del usuario.

## Propiedad de proyectos

Cada proyecto nuevo posee un `ownerId` persistido en la base de datos.

`GET /api/projects` devuelve los proyectos propios y aquellos para los que el usuario posee `ProjectMembership` EDITOR.

El UUID por sí solo no concede acceso: la política central resuelve OWNER, EDITOR o NONE.

## Membresías e invitaciones — Ciclo 2

ClassForge conserva `Project.ownerId` como ownership explícito e incorpora `ProjectMembership` para colaboradores EDITOR.

Las invitaciones son internas y persistentes por correo normalizado; no se envían emails reales y no existen links/tokens públicos.

Flujo cerrado con C2-cu31-003:

1. OWNER introduce el correo del colaborador.
2. Se crea `ProjectInvitation` PENDING.
3. El destinatario puede registrarse después con ese mismo correo.
4. Su bandeja interna muestra la invitación.
5. Aceptar crea `ProjectMembership` EDITOR y marca la invitación ACCEPTED de forma transaccional.
6. El proyecto aparece en su biblioteca como compartido.
7. Decline/Cancel no conceden acceso.

Modelo:

- `Project -> ownerId`
- `ProjectMembership -> projectId + userId + role=EDITOR`
- `ProjectInvitation -> projectId + invitedEmail + invitedByUserId + status + timestamps`

No se incluye todavía eliminación de membership activa, VIEWER, roles personalizados, SMTP ni transferencia de ownership.

C2-cu31-003 valida OWNER/EDITOR/NONE en STOMP operations, presence y Assistant, y serializa invite/accept por proyecto para evitar duplicados concurrentes. CU-31 queda CERRADO. Posteriormente, CU-09 también se cierra dentro del Ciclo 2 mediante el pipeline visual híbrido documentado en `docs/architecture/vision-input-pipeline.md`.

## Endpoints iniciales

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/projects` autenticado
- `POST /api/projects` autenticado
- `GET /api/projects/{id}` autenticado y limitado por OWNER/EDITOR
- `POST /api/projects/{id}/invitations` OWNER
- `GET /api/projects/{id}/invitations` pendientes administrativas, OWNER
- `DELETE /api/projects/{id}/invitations/{invitationId}` cancelar PENDING, OWNER
- `GET /api/projects/{id}/collaborators` OWNER/EDITOR
- `GET /api/project-invitations` invitaciones PENDING del correo autenticado
- `POST /api/project-invitations/{id}/accept`
- `POST /api/project-invitations/{id}/decline`

## Landing publica

La ruta raiz de ClassForge es publica y presenta las capacidades centrales del producto y enlaza a registro e inicio de sesion.

<!-- PROJECT-DOCUMENT-V1 -->
# Actualizacion de producto — Documento de proyecto

ClassForge persiste cada proyecto como un `ProjectDocument` versionado.

El documento contiene dos partes independientes:

- `UmlModel`: significado UML.
- `DiagramLayout`: informacion visual.

Esta separacion garantiza que:

- la UI no define el dominio;
- JointJS puede reemplazarse sin perder modelos;
- importacion XMI, IA y voz operan sobre el mismo dominio;
- la colaboracion puede sincronizar operaciones semanticas;
- el layout puede cambiar sin reinterpretar UML.

Cada guardado del documento utiliza revision optimista y puede detectar modificaciones concurrentes.

La iconografia de ClassForge utiliza Material Symbols Rounded self-hosted mediante npm para mantener disponibilidad offline.

<!-- TYPED-UML-DOMAIN-V1 -->
# Actualizacion de producto — Modelo UML tipado

ClassForge deja de representar clases y atributos como objetos JSON genericos.

El modelo canonico incorpora tipos explicitos para clases, atributos, tipos de datos, visibilidad, identificadores, relaciones, multiplicidades y layout.

CU03-001 ofrece un editor estructurado Angular para clases y atributos. CU03-002 agregara JointJS como proyeccion del mismo ProjectDocument.

Las respuestas HTTP 400 del backend son estructuradas y contienen violations apropiadas para UI, pruebas y trazabilidad.

<!-- JOINTJS-CANVAS-V1 -->
# Actualizacion de producto — Canvas UML

CU03-002 incorpora un canvas JointJS open-source (`@joint/core`) como representacion interactiva del modelo UML canonico.

La aplicacion permite:

- visualizar clases y atributos;
- crear clases desde el canvas;
- editar clases mediante doble interaccion;
- mover elementos;
- zoom;
- pan;
- ajustar el contenido a la vista.

El layout se persiste de forma independiente a la semantica UML.

Las transformaciones de viewport no se guardan.

JointJS nunca sustituye a ProjectDocument como fuente de verdad.

<!-- UML-RELATIONSHIPS-INSPECTOR-V1 -->
# Actualizacion de producto — Relaciones UML manuales

ClassForge completa el editor manual de diagramas con:

- asociaciones;
- agregaciones;
- composiciones;
- generalizaciones;
- multiplicidades;
- inspector contextual.

La creacion de relaciones se realiza seleccionando origen y destino directamente sobre el canvas y configurando despues su semantica en Angular Material.

JointJS proyecta los links y labels, mientras ProjectDocument continua siendo la fuente de verdad.

El backend protege la integridad del modelo, incluyendo deteccion de ciclos de generalizacion.

<!-- EXPLICIT-UML-VALIDATION-V1 -->
# Actualizacion de producto — Validacion UML

ClassForge incorpora validacion explicita del modelo sin persistencia.

El usuario puede validar el draft actual y recibir:

- errores;
- advertencias;
- informacion;
- codigos estables;
- mensajes legibles;
- rutas de campo;
- UUID del elemento afectado cuando existe.

Los diagnosticos navegables seleccionan la clase o relacion correspondiente en el canvas.

El guardado conserva validacion automatica y rechaza solo errores; las advertencias no bloquean la persistencia.

<!-- COMMAND-BUS-UNDO-REDO-V1 -->
# Actualizacion de producto — Command Bus y CU-05

El editor manual despacha mutaciones tipadas al `UmlCommandBus`.

CU-05 agrega Undo/Redo local con un historial máximo inicial de 100 operaciones y shortcuts de teclado.

Los snapshots before/after son internos al historial; `UmlCommand` continúa siendo el contrato reutilizable para colaboración e IA.

<!-- IMPLEMENTATION-STATUS-CU05-V2 -->
# Estado de implementacion verificado hasta CU-05

| Caso | Estado | Evidencia principal |
|---|---|---|
| CU-01 Crear proyecto | Cerrado | API, UUID, ownership y JPA |
| CU-02 Abrir/guardar | Cerrado | ProjectDocument, revision y conflicto 409 |
| CU-03 Diagramar manualmente | Cerrado | clases, atributos, JointJS, relaciones, multiplicidades e inspector |
| CU-04 Validar UML | Cerrado | motor unico, endpoint explicito y diagnosticos navegables |
| CU-05 Undo/Redo | Cerrado | Command Bus, executor e historial local |
| CU-06+ | Pendiente | backlog PUDS |

## Fuente de verdad

```text
ProjectDocument
├── UmlModel
└── DiagramLayout
```

JointJS sigue siendo una proyeccion.

## Command Bus

La deuda arquitectonica detectada despues de CU-04 queda resuelta en CU-05.

El historial before/after pertenece a Undo/Redo; `UmlCommand` es el contrato reutilizable.

## Perfil ClassForge

`nullable` e `identifier` son metadatos de generacion asociados al atributo.

No deben presentarse academicamente como propiedades UML 2.5.1 puras.

## Subconjunto UML implementado

- Class;
- Attribute/Property;
- Visibility;
- tipos;
- Association;
- Aggregation;
- Composition;
- Generalization;
- Multiplicity;
- layout separado.

Operaciones, enums y packages siguen pendientes.

<!-- REALTIME-COLLABORATION-CU06-001-V1 -->
# Actualizacion de producto — CU06-001

ClassForge incorpora el servidor de colaboración STOMP.

Las operaciones remotas reutilizan conceptualmente el contrato Command de CU-05 y se aplican sobre el mismo `ProjectDocument` canónico.

El servidor:

1. autentica JWT;
2. verifica acceso al proyecto;
3. bloquea la fila del proyecto;
4. compara `baseRevision`;
5. ejecuta el comando;
6. valida el documento;
7. persiste;
8. incrementa revisión;
9. difunde el comando aceptado.

No se intercambia JSON de JointJS y no se introduce CRDT en esta etapa.

La integración automática Angular pertenece a CU06-002.

<!-- REALTIME-COLLABORATION-CU06-002-V1 -->
# Actualizacion de producto — CU06-002

El workspace Angular mantiene sincronización realtime con Spring mediante STOMP.

La edición es optimista: el usuario ve su cambio inmediatamente y el servidor confirma la revisión mediante broadcast.

El cliente conserva por separado:

- draft visible;
- documento confirmado;
- revisión confirmada;
- operaciones pending.

Ante gap, rechazo o intercalación conflictiva se recupera el estado autoritativo por REST en lugar de realizar merge implícito.

Si WebSocket no está disponible, la edición local y el guardado REST de CU-02 continúan disponibles.

Undo/Redo colaborativo permanece para CU06-003.

<!-- REALTIME-COLLABORATION-CU06-CLOSED-V1 -->
# Actualización de producto — CU-06 cerrado

ClassForge dispone de colaboración realtime autoritativa sobre STOMP.

El editor sincroniza operaciones `UmlCommand`, persiste cada operación aceptada y usa revisión explícita.

CU06-003 completa Undo/Redo colaborativo mediante comandos inversos.

El borrado de una clase utiliza `RESTORE_CLASS` como comando compensatorio atómico para recuperar su agregado visual/relacional sin transmitir `ProjectDocument` completo.

Las operaciones remotas invalidan el historial local para evitar deshacer intenciones antiguas sobre trabajo de terceros.

CU-07 agregará presencia efímera; la presencia no modificará la revisión ni el modelo UML.

<!-- REALTIME-PRESENCE-CU07-CLOSED-V1 -->
# Actualización de producto — CU-07 cerrado

ClassForge diferencia colaboración persistente de presencia efímera.

Las operaciones UML siguen Command Bus → Spring → revisión → JPA.

La presencia utiliza `ProjectPresenceRegistry` en memoria y no modifica `ProjectDocument`.

El workspace muestra sesiones conectadas, selección remota y cursores remotos sobre el canvas.

Los cursores son overlays y nunca JointJS cells.

El acceso multiusuario real al mismo proyecto dependerá de ProjectMembership; CU-07 ya está preparado para múltiples actores autenticados.

<!-- ASSISTANT-CU08-001-V1 -->
# Actualización producto — CU08-001

El workspace incorpora un chat del Assistant UML debajo del Inspector.

Texto libre se interpreta localmente con llama.cpp, se transforma a un plan semántico y Java lo resuelve a un BATCH validado.

El usuario revisa el plan antes de Aplicar.

Una intención compleja produce una sola operación colaborativa y una sola entrada de Undo/Redo.

La captura real de voz se añade en CU08-002.

<!-- ASSISTANT-CU08-001-RICH-INTENT -->
## Assistant UML — intención rica

El Assistant interpreta lenguaje natural en una estructura semántica independiente de la forma exacta de la frase.

Los atributos de una clase forman parte de la intención `CREATE_CLASS`; Java los expande después a comandos de dominio.

El preview muestra si el tipo de cada atributo fue explícito, inferido o predeterminado.

El runtime recomendado para la estación de trabajo de referencia es Qwen2.5-3B-Instruct Q4_K_M mediante llama.cpp con `--jinja` y native tool calling.

MCP no forma parte de CU08-001.

<!-- CU08-FIX-014 -->
### Assistant local — contrato definitivo
El Assistant usa Qwen2.5-3B-Instruct Q4_K_M mediante native function calling de llama.cpp. El modelo selecciona tools UML semánticas; ClassForge resuelve elementos existentes a UUID, preserva literales nuevos y genera preview/BATCH. Texto y voz convergen en el mismo planner y las peticiones compuestas se planifican sobre un documento efímero antes de Apply.

<!-- C2-CU09-001-IMAGE-VISION-CONTRACT -->
## Addendum CU-09 — contrato visual

C2-cu09-001 abre Imagen → UML con un contrato desacoplado del VLM. PNG/JPEG/WEBP se validan y normalizan; el modelo multimodal futuro debe devolver `VisionUmlProposal` con refs temporales y `VisionEvidence`. Java valida provenance, referencias, tipos, relaciones y multiplicidades, compila a `AssistantSemanticPlan` y conserva preview/BATCH/Command Bus como única ruta de mutación.

Este bloque describe la apertura histórica de CU-09. La decisión posterior de C2-cu09-002 seleccionó Qwen3-VL-4B-Instruct Q4_K_M sobre llama.cpp; la imagen continúa sin persistirse dentro de `ProjectDocument`.

<!-- C2-CU09-002-QWEN3-VL -->
## Addendum CU-09 — runtime visual y benchmark

C2-cu09-002 materializa el adapter multimodal sin cambiar la autoridad del dominio. `LlamaCppVisionModelGateway` envía imagen Base64 + prompt UML a llama.cpp, restringe la respuesta por JSON Schema y rechaza cualquier salida truncada o no deserializable sin repair heurístico. El prompt recibe únicamente nombres de clases/atributos existentes, nunca UUID internos.

El health del Assistant separa `readyForImage` de texto/voz y exige que el runtime de 8094 publique el modelo esperado y `modalities.vision=true`. El frontend bloquea Analizar cuando Vision no está READY.

La suite `assistantVisionBenchmark` compara planes semánticos contra ground truth en regression y holdout y produce métricas de transporte, schema, grounding, exactitud estructural, safety, latencia y memoria GPU observada. La evidencia posterior seleccionó Qwen3-VL-4B Q4_K_M y el benchmark terminó incorporando hardening focal y un executable gate antes del cierre de CU-09.


<!-- C2-CU09-003-VISION-UX-HARDENING -->
## Addendum CU-09 — experiencia visual completa y validación diferida

C2-cu09-003 completa la experiencia de Imagen → UML sin declarar prematuramente que el baseline visual es definitivo. El usuario puede seleccionar, arrastrar, pegar o capturar una imagen, realizar rotación/recorte conservador y analizarla. Cuando el VLM entrega bounding boxes, el preview visual puede resaltar la evidencia asociada a clases, atributos y relaciones.

El resultado puede ser `READY`, `NO_CHANGES` o `NO_ACTIONABLE_UML`. Solo `READY` expone un comando aplicable; los demás estados son éxitos seguros sin mutación. Conflictos con el modelo UML existente se omiten con advertencias antes que convertir Imagen → UML en una ruta de edición destructiva.

La calibración posterior cerró CU-09 el 5 de septiembre de 2026. La solución final usa Qwen3-VL-4B + OpenCV + Java, fail-closed, E2E por la autoridad colaborativa y canonicalización determinista de identificadores visuales. El hardening focal obtuvo 3/3 Exact. La validación con dos pizarras adicionales y una corrida archivada post-Cal-017 del agregador completo quedaron registradas como validación adicional diferida, no como evidencia ejecutada.
