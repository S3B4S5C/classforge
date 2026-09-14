# Casos de uso de ClassForge — especificación vigente

**Corte:** 13 de septiembre de 2026.

Este documento es la fuente normativa de los casos de uso.

La bitácora acumulativa anterior se conserva en `history/use-cases-pre-cycle1-normalization-2026-08-28.md`.

## 1. Convención PUDS

En ClassForge llamamos **Ciclo** a una **iteración PUDS**.

```text
Fase: Construcción
Ciclo 1: CERRADO
Ciclo 2: CERRADO
Ciclo 3: CERRADO
Ciclo 4: CERRADO
Ciclo 5: CERRADO
Ciclo 6: CERRADO
Ciclo 7: CERRADO
Ciclo 8: CERRADO
Ciclo 9: CERRADO
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
Actor de CU-19. Interactúa con el sistema generado por formulario, chat o voz; no debe confundirse con el usuario del CASE ClassForge.

### Sistema
Participa en validación, persistencia, auditoría y generación.

## 3. Leyenda de estado

- **CERRADO:** implementado y aceptado.
- **INFRAESTRUCTURA:** capacidad técnica implementada, aunque el escenario futuro que la reutiliza no esté cerrado.
- **EN PROGRESO:** implementación dividida en incrementos; el CU aún no cumple su Definition of Done completa.
- **PLANIFICADO:** especificado, no implementado.
- **ABSORBIDO:** alcance implementado dentro de otro CU para conservar trazabilidad.
- **DESCARTADO / FUERA DE ALCANCE:** caso conservado históricamente pero retirado del producto a implementar.

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
| CU-10 | Importar XMI de Enterprise Architect | CERRADO |
| CU-11 | Exportar XMI para Enterprise Architect | CERRADO |
| CU-12 | Transformar UML a modelo relacional | CERRADO |
| CU-13 | Generar backend Spring Boot/JPA | CERRADO |
| CU-14 | Generar API CRUD expresiva — CRUD simple / Sistema de Información con Auth | CERRADO |
| CU-15 | Generar OpenAPI y Postman | CERRADO |
| CU-16 | Generar Domain Manifest | CERRADO |
| CU-17 | Generar frontend web Angular | CERRADO |
| CU-18 | Generar frontend mobile Flutter | CERRADO |
| CU-19 | Interactuar con datos mediante lenguaje natural y voz | CERRADO |
| CU-20 | Crear datos mediante voz | ABSORBIDO POR CU-19 |
| CU-21 | Ejecutar operación relacionada mediante voz | ABSORBIDO POR CU-19 |
| CU-22 | Actualizar información mediante voz | ABSORBIDO POR CU-19 |
| CU-23 | Eliminar información mediante voz | ABSORBIDO POR CU-19 |
| CU-24 | Ejecutar STT local | INFRAESTRUCTURA |
| CU-25 | Ejecutar IA local | INFRAESTRUCTURA |
| CU-26 | Registrar cambios del proyecto | DESCARTADO / FUERA DE ALCANCE |
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
**Estado: CERRADO.** Importa XMI 2.1 al `ProjectDocument` canónico mediante parser XML seguro, UUIDs deterministas, validación, preview token y `Apply` protegido por revisión. El subconjunto incluye Package (aplanado), Class, Property/DataType, Association, Aggregation, Composition, Generalization y Multiplicity. DTD/XXE y payloads mayores a 5 MiB fallan cerrados.

### CU-11 — Exportar XMI
**Estado: CERRADO.** Exporta el mismo subconjunto como XMI 2.1 UTF-8 determinista. Los UUID de ClassForge se serializan en IDs XMI recuperables; el acceptance exige igualdad byte a byte para entradas iguales y round-trip semántico `ProjectDocument -> XMI -> ProjectDocument`. La metadata propietaria de diagramas/perfiles de EA queda fuera del alcance; el smoke manual con la instalación del examen se incorpora a CU-27.

### CU-12 — UML a modelo relacional
**Estado: CERRADO.** Transformación determinista e interna de `UmlModel` a `RelationalModel`. La IR no es visible al usuario, no se persiste y es consumida por CU-13.

### CU-13 — Generar backend Spring Boot/JPA
**Estado: CERRADO.** C3-cu13-001 cerró la IR y planner; C3-cu13-002 el proyecto virtual y rendering; C3-cu13-003 la generación interna y ZIP determinista; C3-cu13-004 el export HTTP autorizado; C3-cu13-005 la UX de descarga; C3-cu13-005a el fallback PK explícito y efímero; y C3-cu13-006 cerró `GeneratedProjectValidator` y el acceptance ejecutable. La evidencia final genera ZIPs desde `UmlModel`, los extrae en temporales, ejecuta el Gradle Wrapper generado, exige `contextLoads()` sobre H2 y prueba igualdad SHA-256 para entradas iguales. El cierre consolidado está en `docs/evidence/cu13/cu13-closure-report.md`.

### CU-14 — Generar API CRUD expresiva

**Estado:** CERRADO — C3-cu14-001.

**Actor principal:** Modelador / Usuario autenticado de ClassForge.

**Objetivo:** extender el backend generado por CU-13 con controllers, services, DTOs/mapping y una API CRUD expresiva, permitiendo elegir explícitamente entre dos modos de aplicación generada.

#### Modo A — CRUD simple

Genera la API CRUD sin autenticación de aplicación. Incluye, cuando corresponda:

- CREATE, READ by id, UPDATE, DELETE y LIST;
- búsqueda y filtros por propiedades;
- ordenamiento;
- paginación;
- conteo;
- navegación de relaciones.

Este modo **no** agrega Spring Security, endpoint de login, JWT, `PasswordEncoder` ni tratamiento especial de un atributo llamado `password`. La aplicación generada queda como backend CRUD simple.

#### Modo B — Sistema de Información con Auth

Genera las mismas capacidades CRUD y además un perfil de autenticación de aplicación. Antes de exportar, ClassForge debe pedir al usuario seleccionar:

1. **tabla/entidad de autenticación**: una tabla de `RelationalModel` cuyo origen sea una clase UML, no una tabla de unión;
2. **atributo de usuario/login**: un atributo escalar perteneciente a la tabla/entidad seleccionada;
3. **atributo de contraseña**: un atributo escalar perteneciente a esa misma tabla/entidad y distinto del atributo de usuario/login.

La UI puede presentar nombres de tabla/entidad y atributos, pero el contrato de generación debe identificar la clase y los atributos mediante identidades estables del modelo canónico/origen relacional, no mediante texto libre. La configuración es efímera para esa exportación: **no modifica `UmlModel`, `RelationalModel`, `ProjectDocument` ni la revisión del proyecto**.

El perfil Auth debe añadir como mínimo Spring Security, almacenamiento seguro de contraseña mediante `PasswordEncoder`, endpoint de login y JWT. La contraseña seleccionada no debe exponerse en DTOs de respuesta y todo write que la trate como credencial debe evitar persistir texto plano.

La selección Auth debe validarse de forma fail-closed. Si la tabla/entidad o cualquiera de los atributos ya no existe, no pertenece a la entidad elegida, representa una FK/columna sintética o la selección usuario/contraseña es inválida, no se genera un ZIP parcial.

**Decisión de producto:** no existe un checkbox ambiguo de “auth opcional”. La experiencia de generación presenta dos modos mutuamente excluyentes: **CRUD simple** o **Sistema de Información con Auth**.

#### Política ejecutable cerrada

- Los atributos de usuario y contraseña deben ser `STRING`, pertenecer directamente a la clase seleccionada y ser distintos.
- En el target Auth ambos campos se generan `nullable = false`; username además es `unique = true`.
- CREATE hashea el password con BCrypt; UPDATE sólo reemplaza el hash cuando llega una contraseña no vacía; responses nunca contienen password.
- `POST /api/auth/bootstrap` es público únicamente mientras la tabla de autenticación esté vacía y permite crear la primera cuenta usando el DTO completo de esa entidad. No hay registro público posterior en CU-14.
- `POST /api/auth/login` valida usuario/password y entrega JWT Bearer con expiración de 3600 segundos. No hay refresh token en CU-14.
- En modo Auth todos los demás endpoints quedan protegidos por Spring Security stateless. CU-14 no introduce roles; cualquier JWT válido tiene acceso al API generado.
- LIST acepta `q`, `filter.<campo>`, `sort`, `direction`, `page` y `size` (máximo 200); cada recurso expone además `/count`.
- Relaciones se representan mediante IDs en los DTOs para evitar ciclos de serialización y conservar navegación hacia el endpoint de la entidad relacionada.


### CU-15 — Generar OpenAPI y Postman

**Actor:** OWNER o EDITOR con permiso de exportación.

**Precondición:** CU-14 puede producir un `SpringApiGenerationPlan` válido en modo `SIMPLE_CRUD` o `AUTH_INFORMATION_SYSTEM`.

CU-15 amplía el mismo ZIP de backend generado. No introduce un export separado ni un checkbox adicional.

Flujo:

1. el usuario solicita generar el sistema Spring Boot desde la UX existente;
2. backend valida acceso, revisión y configuración CU-14;
3. CU-12/CU-13/CU-14 construyen sus IR/planes efímeros;
4. CU-15 deriva un contrato HTTP canónico determinista;
5. del mismo contrato se renderizan `openapi.yaml` y `postman_collection.json`;
6. ambos artefactos se incorporan a la raíz del ZIP antes de `GeneratedProjectValidator`/archivo final;
7. la generación permanece read-only respecto de `ProjectDocument`, `UmlModel`, `RelationalModel` y revisión.

**OpenAPI**

- formato OpenAPI `3.0.3`;
- representa exactamente los endpoints emitidos por CU-14;
- documenta DTOs request/response, paginación, filtros, sorting, count, IDs simples/compuestos y errores 400/404/409;
- en `AUTH_INFORMATION_SYSTEM` define `bearerAuth` JWT y seguridad global, dejando `POST /api/auth/bootstrap` y `POST /api/auth/login` como operaciones públicas;
- el password de la entidad Auth es de escritura y no aparece en schemas de respuesta;
- en `SIMPLE_CRUD` no se declara security scheme ni requisito de autenticación.

**Postman**

- colección JSON schema v2.1;
- variable `baseUrl` con default `http://localhost:8080`;
- una carpeta por entidad con list/count/create/get/update/delete y parámetros coherentes con OpenAPI;
- en Auth agrega carpeta de autenticación con bootstrap y login;
- login/bootstrap guardan `accessToken` en la variable de colección `jwt`;
- requests protegidos usan `Bearer {{jwt}}`;
- en Simple no existe token ni carpeta Auth.

**Invariantes**

- OpenAPI y Postman no se mantienen manualmente como contratos independientes;
- ambos nacen del mismo contrato canónico derivado del plan CU-14;
- orden, nombres, operationIds y contenido son deterministas;
- no se ejecuta el backend generado ni una herramienta externa para producir los archivos;
- CU-15 no genera Domain Manifest ni cliente TypeScript.

**Postcondición:** el ZIP contiene un backend compilable más `openapi.yaml` y `postman_collection.json` coherentes y reproducibles.

### CU-16 — Generar Domain Manifest

**Estado:** CERRADO — implementado y aceptado en C5-cu16-001.
**Actor:** Sistema.
**Disparador:** exportación exitosa de un proyecto con API CU-14.

**Objetivo:** producir `domain-manifest.json` schema `1.0` como proyección semántica determinista para CU-17/CU-18/CU-19, sin crear una segunda fuente de verdad.

**Autoridad:**

```text
SpringGenerationModel
SpringApiGenerationPlan
SpringApiContract
        |
        v
DomainManifestPlan
        |
        v
domain-manifest.json
```

No se reconstruye el dominio parseando `openapi.yaml` ni `postman_collection.json`.

**Contenido mínimo:**

- modo `SIMPLE_CRUD` / `AUTH_INFORMATION_SYSTEM`;
- UUID fuente de clases, atributos y relaciones cuando exista;
- entidades, nombres lógico/código, tabla, endpoint y displayName;
- aliases vacíos por defecto, sin pluralización heurística;
- IDs simples/compuestos;
- herencia explícita;
- atributos con tipo semántico, nullability, lectura/escritura, mutabilidad, search/filter/sort y validaciones básicas;
- relaciones con target estable, tipo UML/API, optionalidad y campo request;
- capacidades CRUD/query;
- operaciones usando exactamente los `operationId` de CU-15;
- metadata Auth cuando el modo la habilite.

**Privacidad Auth:** password sensible/write-only, no readable/searchable/filterable/sortable, requerido en create/bootstrap y opcional en update.

**Postcondición:** el ZIP generado contiene un `domain-manifest.json` parseable, referencialmente válido y determinista, coherente con el proyecto Spring, OpenAPI y Postman.

**Fuera de alcance:** Angular generado, mobile, ejecución del asistente, roles/refresh, aliases inferidos por IA o nuevas reglas de negocio.

### CU-17 — Frontend web Angular

**Actor:** Modelador / usuario con permiso de edición.

**Objetivo:** generar dentro del mismo ZIP una aplicación Angular standalone funcional y coherente con Domain Manifest + API canónica.

Por entidad se generan archivos específicos de models, API service, list, detail y form. No se usa una única pantalla CRUD metadata-driven.

Capacidades:

- dashboard con `count` por entidad y accesos directos;
- listados con búsqueda, filtros, orden y paginación;
- crear/ver/editar/eliminar;
- controles por tipo semántico;
- relaciones to-one con selector simple;
- many-to-many con selección múltiple;
- IDs simples y compuestos;
- rutas y requests alineados con CU-14/CU-15;
- color primario `#RRGGBB` elegido en el diálogo de exportación.

En `SIMPLE_CRUD` no se genera Auth. En `AUTH_INFORMATION_SYSTEM` se añaden login, bootstrap de primera cuenta, almacenamiento Bearer JWT, interceptor, guard, logout y redirección a login ante 401. Password se trata como secreto y nunca como dato readable.

**Postcondición:** el ZIP contiene `frontend/` y los frontends representativos Simple/Auth pasan `ng build` de producción.

**Fuera de alcance:** Flutter mobile (CU-18), IA/voz (CU-19), roles/refresh y dashboards de negocio inferidos.

### CU-18 — Generar frontend mobile Flutter

**Objetivo:** generar dentro del mismo ZIP una aplicación Flutter independiente y coherente con Domain Manifest + API canónica, sin reutilizar Angular/Capacitor.

Por entidad se generan archivos específicos de model, API, list, detail y form. Incluye dashboard con conteos, búsqueda, CRUD, relaciones, IDs simples/compuestos y el mismo color primario seleccionado para CU-17.

En `SIMPLE_CRUD` no se genera Auth. En `AUTH_INFORMATION_SYSTEM` se añaden bootstrap, login, JWT Bearer almacenado mediante `flutter_secure_storage`, protección de navegación, logout implícito por limpieza de token y manejo de 401.

`to-one` usa selector simple; colecciones many-to-many/one-to-many usan selector múltiple. Android es la plataforma formalmente aceptada mediante `flutter build apk --debug`; iOS/desktop/web Flutter quedan fuera del cierre de CU-18.

**Postcondición:** el ZIP contiene `mobile/` y los frontends representativos Simple/Auth pasan `flutter analyze`, `flutter test` y build APK debug.

### CU-19 — Interactuar con datos mediante lenguaje natural y voz

**Estado: CERRADO.**

**Objetivo:** permitir que el usuario de la aplicación generada consulte y modifique datos mediante chat o voz reutilizando los mismos runtimes locales de ClassForge: whisper.cpp para STT y Qwen/llama.cpp con native tool calling para planificación.

Texto y voz convergen en el mismo planner. El Spring generado es la única capa que contacta `127.0.0.1:8092`/`:8093`; Angular y Flutter consumen `/api/assistant/plan`, `/voice` y `/apply`. Java mantiene la autoridad mediante metadata derivada del Domain Manifest, validación de entidades/campos/tipos/relaciones/capacidades y resolución fail-closed.

Intents soportados: `QUERY`, `COUNT`, `GET`, `CREATE`, `UPDATE`, `DELETE`, `SET_RELATION`, `ADD_RELATION`, `REMOVE_RELATION`. Las consultas se ejecutan inmediatamente. Las mutaciones retornan un resumen sanitizado y un preview token opaco con TTL; únicamente `/apply` ejecuta la operación. Campos sensibles como password se enmascaran y no viajan como comando crudo al cliente.

**Postcondición:** los exports Simple/Auth incluyen Assistant generado en Spring + Angular + Flutter, son deterministas, sus backends compilados pasan tests y las regresiones CU-13..18 permanecen verdes.

### CU-20 — Crear datos mediante voz
**Estado: ABSORBIDO POR CU-19.** El escenario CREATE forma parte del intent `CREATE` con preview y confirmación.

### CU-21 — Operación relacionada mediante voz
**Estado: ABSORBIDO POR CU-19.** Las operaciones de relación se cubren mediante `SET_RELATION`, `ADD_RELATION` y `REMOVE_RELATION`; no se inventan operaciones de negocio no modeladas.

### CU-22 — Actualizar información mediante voz
**Estado: ABSORBIDO POR CU-19.** El intent `UPDATE` exige resolución no ambigua y confirmación.

### CU-23 — Eliminar información mediante voz
**Estado: ABSORBIDO POR CU-19.** `DELETE` exige resolución no ambigua, preview sanitizado y confirmación explícita.

### CU-24 — STT local
La infraestructura whisper.cpp ya existe para CU-08 y CU-19 la reutiliza en la aplicación generada.

### CU-25 — IA local
La infraestructura llama.cpp/Qwen2.5 native tools ya existe para CU-08 y CU-19 la reutiliza con `DomainManifest`, manteniendo Java como autoridad de resolución/validación.

### CU-26 — Registrar cambios
**Estado: DESCARTADO / FUERA DE ALCANCE.** Se conserva para trazabilidad PUDS, pero la auditoría histórica persistente no se implementará en el alcance actual.

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
                                                        +-> CU19 (absorbe CU20..23)
```

CU24/25 son infraestructura reutilizable y CU-19 las consume sin duplicar los procesos locales Whisper/Qwen.

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

## 12. Ciclo 3

El Ciclo 3 está formalmente **CERRADO** en fase de Construcción.

```text
CU-12: CERRADO
CU-13: CERRADO
CU-14: CERRADO — CRUD simple / Sistema de Información con Auth
```

CU-12 estableció la IR relacional interna y determinista. CU-13 consume esa IR y entrega un backend Spring Boot/JPA reproducible mediante proyecto virtual validado, ZIP determinista, export OWNER/EDITOR y acceptance que compila los proyectos generados y carga Spring sobre H2.

La evidencia de cierre de CU-13 está en `docs/evidence/cu13/cu13-closure-report.md` y `docs/evidence/cu13/cu13-acceptance.json`. El Ciclo 3 queda cerrado con CU-12, CU-13 y CU-14 aceptados.

## 13. Ciclo 4

El Ciclo 4 está **CERRADO** en fase de Construcción con CU-15 aceptado.

Criterios de salida:

- `openapi.yaml` y `postman_collection.json` se generan siempre junto con ambos modos CU-14;
- Simple no declara seguridad;
- Auth documenta y prueba bootstrap/login/Bearer JWT sin exponer password;
- la colección Postman representa todas las operaciones OpenAPI relevantes y no inventa endpoints;
- ambos artefactos son deterministas;
- proyectos generados Simple/Auth continúan compilando y cargando contexto sobre H2;
- CU-13 y CU-14 no regresionan.

El alcance técnico quedó fijado en `docs/puds/iterations/cycle-04/C4-cu15-000-openapi-postman-scope.md` y el cierre ejecutable en `docs/puds/iterations/cycle-04/C4-cu15-001-openapi-postman-generation.md`.
