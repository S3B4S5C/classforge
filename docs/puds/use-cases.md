# Casos de uso de ClassForge — especificación vigente

**Corte:** 28 de agosto de 2026.

Este documento es la fuente normativa de los casos de uso.

La bitácora acumulativa anterior se conserva en `history/use-cases-pre-cycle1-normalization-2026-08-28.md`.

## 1. Convención PUDS

En ClassForge llamamos **Ciclo** a una **iteración PUDS**.

```text
Fase: Elaboración
Ciclo 1: CERRADO
Ciclo 2: NO ABIERTO
```

## 2. Actores

### Visitante
Puede registrarse e iniciar el proceso de autenticación.

### Modelador / Usuario autenticado
Crea proyectos, modela UML, valida, colabora y utiliza el Asistente UML.

### Propietario
Usuario autenticado que posee un proyecto. Actualmente es el único rol autorizado a abrirlo y colaborar sobre él.

### Colaborador
Rol previsto para CU-31. Todavía no existe `ProjectMembership`.

### Usuario de aplicación generada
Actor futuro de CU-19..23. No debe confundirse con el usuario del CASE ClassForge.

### Sistema
Participa en validación, persistencia, auditoría y generación.

## 3. Leyenda de estado

- **CERRADO:** implementado y aceptado.
- **INFRAESTRUCTURA:** capacidad técnica implementada, aunque el escenario futuro que la reutiliza no esté cerrado.
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
| CU-09 | Crear UML desde imagen/fotografía | PLANIFICADO |
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
| CU-31 | Invitar colaborador y crear membresía | PLANIFICADO |

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

Limitación vigente: no existe membresía; la demostración se realiza como multisesión del propietario.

### CU-08 — Crear o modificar UML mediante lenguaje natural y voz

Texto:

```text
texto
 -> Gemma
 -> AssistantSemanticPlan
 -> grounding
 -> normalización
 -> resolver Java
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

## 6. Casos planificados

### CU-09 — Imagen a UML
Interpretar fotografía/imagen y producir propuesta estructurada editable.

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
La infraestructura llama.cpp/Gemma ya existe para CU-08 y debe reutilizarse con `DomainManifest`.

### CU-26 — Registrar cambios
Auditoría persistente con operación, usuario, fecha, revisión anterior/nueva y elemento afectado.

### CU-27 — Demo reproducible
Proyecto veterinaria capaz de demostrar de extremo a extremo las capacidades terminadas.

### CU-31 — Invitar colaborador
Introducir `ProjectMembership` e invitaciones seguras para que la colaboración deje de ser owner-only.

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

## 11. Apertura de Ciclo 2

Ciclo 2 no se considera abierto solo por comenzar a programar otro CU.

Antes se debe crear su documento PUDS indicando objetivo, casos de uso, riesgos, arquitectura afectada, criterios de salida y pruebas previstas.

El historial del plan original se conserva en `history/`.
