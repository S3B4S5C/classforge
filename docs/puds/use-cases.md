# Casos de uso de ClassForge — especificación vigente

**Corte:** 5 de septiembre de 2026.

Este documento es la fuente normativa de los casos de uso.

La bitácora acumulativa anterior se conserva en `history/use-cases-pre-cycle1-normalization-2026-08-28.md`.

## 1. Convención PUDS

En ClassForge llamamos **Ciclo** a una **iteración PUDS**.

```text
Fase: Elaboración
Ciclo 1: CERRADO
Ciclo 2: CERRADO
```

## 2. Actores

### Visitante
Puede registrarse e iniciar el proceso de autenticación.

### Modelador / Usuario autenticado
Crea proyectos, modela UML, valida, colabora y utiliza el Asistente UML.

### Propietario
Usuario autenticado cuyo `User.id` coincide con `Project.ownerId`. Conserva administración exclusiva: renombrar e invitar.

### Colaborador
Usuario autenticado con `ProjectMembership` EDITOR aceptada. Puede abrir, editar, guardar y validar proyectos compartidos; no administra ownership ni invitaciones.

### Usuario de aplicación generada
Actor futuro de CU-19..23. No debe confundirse con el usuario del CASE ClassForge.

### Sistema
Participa en validación, persistencia, auditoría y generación.

## 3. Leyenda de estado

- **CERRADO:** implementado y aceptado.
- **INFRAESTRUCTURA:** capacidad técnica implementada, aunque el escenario futuro que la reutiliza no esté cerrado.
- **EN PROGRESO:** implementación dividida en incrementos; el CU aún no cumple su Definition of Done completa.
- **PLANIFICADO:** especificado, no implementado.

## 4. Catálogo vigente

| CU | Nombre | Estado al corte |
|---|---|---|
| CU-01 | Crear proyecto de modelado | CERRADO |
| CU-02 | Abrir y guardar proyecto | CERRADO |
| CU-03 | Modelar diagrama de clases manualmente | CERRADO |
| CU-04 | Validar modelo UML | CERRADO |
| CU-05 | Deshacer y rehacer cambios | CERRADO |
| CU-06 | Colaborar en tiempo real | CERRADO |
| CU-07 | Visualizar presencia colaborativa | CERRADO |
| CU-08 | Crear/modificar UML mediante lenguaje natural y voz | CERRADO |
| CU-09 | Crear UML desde imagen/fotografía | CERRADO |
| CU-10 | Importar XMI de Enterprise Architect | PLANIFICADO |
| CU-11 | Exportar XMI para Enterprise Architect | PLANIFICADO |
| CU-12 | Transformar UML a modelo relacional | PLANIFICADO |
| CU-13 | Generar backend Spring Boot/JPA | PLANIFICADO |
| CU-14 | Generar API CRUD expresiva | PLANIFICADO |
| CU-15 | Generar OpenAPI y Postman | PLANIFICADO |
| CU-16 | Generar Domain Manifest | PLANIFICADO |
| CU-17 | Generar frontend web Angular | PLANIFICADO |
| CU-18 | Generar frontend mobile Android/Capacitor | PLANIFICADO |
| CU-19 | Consultar datos mediante voz | PLANIFICADO |
| CU-20 | Crear datos mediante voz | PLANIFICADO |
| CU-21 | Ejecutar operación relacionada mediante voz | PLANIFICADO |
| CU-22 | Actualizar información mediante voz | PLANIFICADO |
| CU-23 | Eliminar información mediante voz | PLANIFICADO |
| CU-24 | Ejecutar STT local | INFRAESTRUCTURA |
| CU-25 | Ejecutar IA local | INFRAESTRUCTURA |
| CU-26 | Registrar cambios del proyecto | PLANIFICADO |
| CU-27 | Generar demo reproducible | PLANIFICADO |
| CU-28 | Registrar cuenta | CERRADO |
| CU-29 | Iniciar sesión | CERRADO |
| CU-30 | Acceder a proyectos propios | CERRADO |
| CU-31 | Invitar colaborador y crear membresía | CERRADO |

## 5. Casos cerrados del Ciclo 1

### CU-01 — Crear proyecto de modelado

**Actor:** Usuario autenticado.

1. El usuario solicita crear proyecto.
2. Introduce nombre.
3. Backend crea UUID.
4. Backend asigna `ownerId`.
5. Se persiste el proyecto.
6. El proyecto aparece en la biblioteca del propietario.

**Postcondición:** existe un proyecto persistente y aislado por ownership.

### CU-02 — Abrir y guardar proyecto

**Actor:** Propietario.

Apertura:

1. Angular solicita proyecto.
2. Backend verifica ownership.
3. Recupera `ProjectDocument`.
4. Frontend reconstruye estado y canvas.

Guardado:

1. Frontend envía documento + `baseRevision`.
2. Backend compara revisión.
3. Si coincide, valida y persiste.
4. Incrementa revisión.
5. Si no coincide, rechaza el guardado como obsoleto.

### CU-03 — Modelar diagrama manualmente

Permite crear/renombrar/eliminar clases, editar atributos, mover elementos y modelar Association, Aggregation, Composition, Generalization y multiplicidades.

`identifier` y `nullable` son perfil ClassForge, no propiedades UML puras.

**Regla:** JointJS es una proyección del `ProjectDocument`.

### CU-04 — Validar modelo UML

Backend analiza el documento sin persistir, devuelve violaciones con severidad y la UI permite navegar al elemento afectado.

Validar no incrementa revisión.

### CU-05 — Deshacer y rehacer cambios

```text
UI
 -> UmlCommand
 -> UmlCommandBus
 -> UmlCommandExecutor
 -> ProjectDocument
```

Incluye Undo, Redo, shortcuts e historial acotado.

En realtime, Undo/Redo se materializa como operaciones compensatorias.

### CU-06 — Colaborar en tiempo real

```text
cliente
 -> STOMP
 -> Spring autoritativo
 -> validar acceso
 -> validar baseRevision
 -> ejecutar comando
 -> validar UML
 -> persistir revisión
 -> broadcast
```

Los clientes envían operaciones, no JSON de JointJS ni el documento completo.

### CU-07 — Visualizar presencia colaborativa

Eventos efímeros:

- USER_JOINED;
- USER_LEFT;
- USER_SELECTED_ELEMENT;
- USER_MOVED_CURSOR;
- PRESENCE_SNAPSHOT.

No persiste y no incrementa revisión.

C2-cu31-003 valida presencia entre cuentas OWNER/EDITOR reales y rechaza NONE mediante la misma política central.

### CU-08 — Crear o modificar UML mediante lenguaje natural y voz

Texto:

```text
texto
 -> Qwen native tool calling
 -> tool UML semántica
 -> grounded reference resolver / UUID
 -> AssistantSemanticPlan interno
 -> grounding + normalización
 -> BATCH
 -> preview
 -> validación
 -> confirmación
 -> Command Bus
```

Voz:

```text
micrófono
 -> WAV 16 kHz
 -> whisper.cpp
 -> transcript
 -> mismo pipeline de texto
```

Reglas:

- IA nunca modifica directamente el documento;
- LLM no genera UUID;
- Java resuelve entidades;
- un prompt puede producir varias acciones dentro de un BATCH;
- preview es read-only;
- aplicar requiere revisión vigente;
- cambio remoto invalida plan obsoleto;
- errores pueden incluir etapa, transcript y plan intentado.

Incrementos:

- CU08-001: texto + plan semántico + BATCH;
- CU08-002: micrófono + whisper.cpp;
- CU08-003: health, diagnóstico y hardening colaborativo.

### CU-28 — Registrar cuenta

Nombre, correo y contraseña se validan; correo se normaliza; contraseña se codifica con BCrypt; backend emite JWT.

### CU-29 — Iniciar sesión

Backend valida credenciales y emite JWT para sesión stateless.

### CU-30 — Acceder a proyectos propios

Backend deriva usuario actual del token y solo devuelve proyectos cuyo `ownerId` coincide.

## 6. CU-09 cerrado y casos posteriores

### CU-09 — Crear UML desde imagen/fotografía

**Estado:** CERRADO.  
**Actor principal:** Modelador con permiso de edición (`OWNER` o `EDITOR`).  
**Actor de soporte:** Sistema local de visión (`llama.cpp` + Qwen3-VL), backend ClassForge y servicios de colaboración.

#### Propósito

Permitir que el usuario capture, pegue o seleccione una imagen/fotografía de un diagrama de clases UML y obtenga una propuesta estructurada, revisable y aplicable sobre el mismo `ProjectDocument` utilizado por el modelado manual, realtime y el Asistente de texto/voz.

El caso de uso no convierte la imagen ni el VLM en fuente de verdad. La salida visual debe converger en `AssistantSemanticPlan -> BATCH -> preview -> Apply -> ProjectDocument`.

#### Precondiciones

1. El usuario está autenticado.
2. El usuario tiene acceso de edición al proyecto como OWNER o EDITOR.
3. El proyecto está abierto y dispone de una revisión conocida.
4. La imagen cumple el contrato PNG/JPEG/WEBP y los límites de tamaño/dimensiones.
5. Para el flujo con modelo real, el runtime multimodal local está disponible en la configuración de Vision.

#### Flujo principal

1. El usuario prepara una imagen mediante selector, drag & drop, clipboard o cámara compatible.
2. Puede rotar, recortar conservadoramente o restaurar la imagen antes de enviarla.
3. Frontend envía la imagen junto con `baseRevision`.
4. Backend valida firma, formato, tamaño y dimensiones; normaliza orientación y representación cuando corresponde.
5. Qwen3-VL ejecuta una primera lectura semántica de clases y atributos visibles.
6. Si se detectan menos de cuatro clases, ClassForge conserva la estrategia semantic-only.
7. Si se detectan cuatro o más clases, entra `hybrid-cv`:
   - OpenCV detecta regiones físicas de clases `B1..Bn`;
   - Qwen realiza un mapping cerrado `Bx -> classRef`;
   - OpenCV reconstruye pares físicos de relaciones;
   - Qwen clasifica por edge el tipo/marker;
   - cada endpoint usa transcription condicionada por competidores;
   - una atribución explícita decide a qué edge pertenece el label;
   - Java parsea multiplicidades de forma determinista.
8. El resultado se ensambla como `VisionUmlProposal` con evidencia visual.
9. La frontera de compilación adapta identificadores visuales a nombres compatibles con código sin modificar la evidencia literal.
10. Grounding/compiler producen `AssistantSemanticPlan`.
11. `UmlAssistantCommandResolver` genera un `BATCH` canónico y calcula preview sobre la revisión base.
12. Si no hay cambios accionables se devuelve `NO_ACTIONABLE_UML` o `NO_CHANGES` con `command=null`.
13. Si hay cambios válidos se devuelve `READY` con `baseRevision`, plan, evidencia, warnings, `BATCH` y preview.
14. El usuario revisa y pulsa `Apply`.
15. El mismo `BATCH` pasa por Command Bus/`ProjectOperation` y `ProjectCollaborationService`.
16. Backend verifica permisos y revisión, ejecuta el BATCH, valida `ProjectDocument`, persiste una vez e incrementa la revisión exactamente una vez.
17. Al reabrir el proyecto, el `ProjectDocument` persistido reproduce el preview aceptado.

#### Flujos alternos y fallos seguros

- **Imagen sin UML accionable:** `NO_ACTIONABLE_UML`; no hay comando ni mutación.
- **La imagen no aporta cambios nuevos:** `NO_CHANGES`; no hay comando ni mutación.
- **Runtime Vision no disponible / transporte:** error `VISION` con `visionReason=TRANSPORT`; no hay semantic fallback después de haber seleccionado hybrid-CV; la UI permite `Analizar nuevamente`.
- **Salida VLM/contrato inválido:** error `VISION` con `visionReason=OUTPUT_CONTRACT`; no hay proposal aplicable.
- **Revisión cambia durante la inferencia:** se rechaza antes de preview.
- **Plan se vuelve stale antes de Apply:** `REVISION_CONFLICT`; ningún child del BATCH se persiste parcialmente.
- **Usuario sin edición:** se rechaza antes de mutar el proyecto.
- **Conflicto con elemento existente:** se aplica la política conservadora del compiler; no se fuerza un update silencioso.

#### Reglas de autoridad

1. `ProjectDocument/UmlModel` es la fuente de verdad.
2. JointJS es proyección.
3. El VLM nunca escribe directamente.
4. En `hybrid-cv`, OpenCV/Java posee la topología física; el VLM no puede crear endpoints arbitrarios.
5. Java posee grounding, parser de multiplicidad, canonicalización de identificadores, validación de comandos y control de revisión.
6. La evidencia visual puede conservar Unicode natural (`Categoría`, `Préstamo`, `añoPublicacion`), pero los identificadores que ingresan al modelo cumplen el contrato de código.
7. Un BATCH completo representa una única operación colaborativa y una única revisión persistida.

#### Postcondiciones

- Si el usuario no aplica, el proyecto permanece intacto.
- Si aplica un plan vigente y válido, el modelo canónico queda actualizado y persistido en una revisión nueva.
- La imagen no se incorpora al `ProjectDocument`.
- Un fallo o conflicto no produce mutación parcial.

#### Evidencia de aceptación

La evidencia final está consolidada en `docs/evidence/cu09/cu09-closure-report.md` y `docs/evidence/cu09/cu09-acceptance.json`. El fixture realista `library-whiteboard-realistic` obtuvo 3/3 intentos Exact con 100 % en transporte, schema, grounding, clases, atributos, relaciones, multiplicidades, semantic exact y safety; Cal-016 cerró el E2E canónico y el smoke manual final validó happy path, preparación de imagen, fail-closed/retry, stale plan y permisos.

La generalización estadística con dos pizarras adicionales y la ejecución archivada del agregador completo de acceptance se registran como validación adicional diferida y no se presentan como ejecutadas. La aceptación funcional de CU-09 fue concedida con ese riesgo residual explícito.

### CU-10 — Importar XMI
Importar subconjunto XMI 2.1 compatible con Enterprise Architect.

### CU-11 — Exportar XMI
Exportar el mismo subconjunto y probar round-trip con Enterprise Architect.

### CU-12 — UML a modelo relacional
Transformación determinista de clases, atributos, identificadores y relaciones a `RelationalModel`.

### CU-13 — Generar backend Spring Boot/JPA
Generar proyecto compilable desde el modelo.

### CU-14 — Generar API CRUD expresiva
CRUD, búsqueda, filtros, ordenamiento, paginación, conteo y navegación de relaciones.

### CU-15 — OpenAPI y Postman
Entregar contrato OpenAPI y colección Postman reproducible.

### CU-16 — Domain Manifest
Producir manifest de entidades, atributos, tipos, relaciones, endpoints y capacidades.

### CU-17 — Frontend web
Generar Angular reutilizando template dirigido por Domain Manifest.

### CU-18 — Frontend mobile
Empaquetar Angular generado con Capacitor para Android.

### CU-19 — Consultar datos mediante voz
Ejemplo: "Quiero ver los últimos 5 animales".

### CU-20 — Crear datos mediante voz
Ejemplo: "Añade un animal llamado Luna de especie perro".

### CU-21 — Operación relacionada mediante voz
Ejemplo: "Añade una cita para Luna mañana a las 4".

### CU-22 — Actualizar información mediante voz
Resolver registro, campo y nuevo valor antes de ejecutar update.

### CU-23 — Eliminar información mediante voz
Requiere confirmación antes de acciones destructivas.

### CU-24 — STT local
La infraestructura whisper.cpp ya existe para CU-08 y debe reutilizarse en la futura aplicación generada.

### CU-25 — IA local
La infraestructura llama.cpp/Qwen2.5 native tools ya existe para CU-08 y debe reutilizarse con `DomainManifest`, manteniendo Java como autoridad de resolución/validación.

### CU-26 — Registrar cambios
Auditoría persistente con operación, usuario, fecha, revisión anterior/nueva y elemento afectado.

### CU-27 — Demo reproducible
Proyecto veterinaria capaz de demostrar de extremo a extremo las capacidades terminadas.

### CU-31 — Invitar colaborador

**Estado: CERRADO.**

**Actor principal:** Propietario.  
**Actor secundario:** Usuario invitado / Colaborador.

Flujo vigente cerrado con C2-cu31-003:

1. OWNER abre el diálogo Colaboradores.
2. Introduce un correo.
3. Backend normaliza el correo y crea `ProjectInvitation` PENDING.
4. La invitación puede existir aunque la cuenta todavía no exista.
5. Al iniciar sesión o registrarse con ese correo, el usuario ve su bandeja pendiente.
6. Solo esa cuenta puede aceptar o rechazar la invitación.
7. Aceptar crea `ProjectMembership` EDITOR y marca la invitación ACCEPTED en una única transacción.
8. El proyecto aparece como `Compartido · Editor`.
9. Rechazar o cancelar no crea membership.

Reglas ya implementadas:

- `Project.ownerId` permanece intacto;
- OWNER es implícito y EDITOR se persiste;
- no hay SMTP, link/token público ni Internet requerido;
- self-invite rechazado;
- pending duplicado rechazado;
- usuario ya miembro no puede ser invitado;
- EDITOR no puede renombrar, invitar, listar pendientes administrativos ni cancelar;
- aceptar no modifica `ProjectDocument` ni la revisión UML;
- no se elimina membership activa en CU-31.

C2-cu31-003 demuestra OWNER + EDITOR + NONE sobre STOMP operations, presencia y Assistant y endurece carreras concurrentes de invitaciones. La eliminación de memberships activas continúa explícitamente fuera de alcance.

## 7. Dependencias principales

```text
CU01 -> CU02 -> CU03 -> CU04
                  |
                  +-> CU06 -> CU07
                  |
                  +-> CU08
                  |
                  +-> CU09
                  |
                  +-> CU10/11
                  |
                  +-> CU12 -> CU13 -> CU14 -> CU15 -> CU16
                                                   |
                                                   +-> CU17/18
                                                        |
                                                        +-> CU19..23
```

CU24/25 son infraestructura reutilizable.

## 8. Definition of Done

Un CU se cierra cuando, según corresponda:

1. existe implementación funcional;
2. hay pruebas unitarias de dominio;
3. hay prueba de integración para infraestructura externa;
4. se manejan errores;
5. documentación vigente se actualiza;
6. arquitectura y UML no contradicen el código;
7. existe evidencia de ejecución;
8. el incremento compila;
9. si genera artefactos, estos compilan y ejecutan;
10. el estado se refleja en `current-status.md`.

## 9. Trazabilidad

```text
Necesidad
 -> CU
 -> Ciclo
 -> incremento técnico
 -> arquitectura/UML
 -> código
 -> prueba
 -> evidencia
```

## 10. Escenario de referencia

```text
Propietario 1 ---- * Animal
Animal      1 ---- * Cita
Veterinario 1 ---- * Cita
```

Entidades de referencia:

- Propietario;
- Animal;
- Veterinario;
- Cita.

## 11. Ciclo 2

El Ciclo 2 está formalmente **CERRADO**.

```text
CU-31: CERRADO
CU-09: CERRADO
```

C2-cu31-001/002/003 cerraron colaboración entre cuentas reales. C2-cu09-001/002/003 y las calibraciones Cal-011..017 cerraron la entrada visual al modelo canónico.

La evidencia detallada del cierre de Imagen -> UML está en `docs/evidence/cu09/cu09-closure-report.md`.

La validación multi-pizarra adicional y una corrida archivada post-Cal-017 del agregador completo de acceptance quedan registradas como riesgo residual aceptado, no como evidencia ejecutada.

El historial de decisiones previas a CU-09 se conserva en `history/` y en las iteraciones técnicas correspondientes.
