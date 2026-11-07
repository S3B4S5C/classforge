# Asistente colaborativo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Entregar RF24/RF28/RF29 sobre la base existente y publicar la rama `tiago`.
**Architecture:** Fachada de proyecto con conversación JPA, gateways conversacionales separados y eventos STOMP. Angular reutiliza colaboración, presencia y preview/Apply existentes.
**Tech Stack:** Java 21, Spring Boot 4.0.8, JPA, Angular 22, STOMP.
**Spec:** `docs/superpowers/specs/2026-11-07-asistente-colaborativo-persona-b-design.md`, aprobado por el usuario.

## Global Constraints

- No depender de ProjectActivity, ProjectSession, ProjectNotification, ProjectUserVisit.
- Reutilizar AssistantPlanService, BATCH, preview, Apply y presencia existente.
- ASK/REVIEW/SUMMARY_CURRENT no escriben UML.
- Contexto reciente: últimos 20 mensajes; Node >=22.22.3.
- Git: trabajar en `tiago`, no cambiar main; push autorizado al terminar.

## Review Focus

- Reintento concurrente con mismo requestId: una sola respuesta persistida.
- Fallo del proveedor: conservar pregunta y permitir reintentar sin duplicados.
- Reconexión entre historial y suscripción: reconciliar por ID sin perder mensajes.
- Cambio de proyecto con peticiones en vuelo: no mezclar estado ni presencia.
- EDIT con revisión distinta durante planificación: utilizar baseRevision del plan.

## Contrato compartido

Paquete Java base `com.classforge.projectassistant`; DTOs públicos en archivos
independientes. `AssistantMode`: ASK, REVIEW, EDIT, SUMMARY_CURRENT.
`ProjectAiMessage`: `(UUID id, long sequence, UUID projectId, UUID requestId,
UUID authorUserId, String authorDisplayName, String role, AssistantMode type,
String content, long projectRevision, Instant createdAt, AssistantReview review,
AssistantPlanResponse plan)`.
`AssistantReview`: `(UUID id, UUID projectId, long projectRevision,
UUID requestedBy, String summary, List<AssistantReviewFinding> findings,
Instant createdAt)`.
`AssistantReviewFinding`: `(String severity, String category, String message,
String elementRef)`.
`ProjectAssistantContext`: `(UUID projectId, String projectName,
long currentRevision, ProjectDocument document, List<Member> members,
List<ProjectAiMessage> recentMessages)`; Member `(UUID userId,
String displayName, String role)`.
`AssistantConversationResponse`: `(String content,
List<AssistantReviewFinding> findings)`.
`ProjectAssistantConversationGateway.respond(ProjectAssistantContext context,
String userMessage, AssistantMode mode)` devuelve AssistantConversationResponse.

HTTP POST messages: `{content, mode, requestId}`; respuesta
`{userMessage, assistantMessage, projectRevision}`. Review y plan viajan en
assistantMessage. GET messages `?limit=50&before=<sequence>` devuelve
`{messages, hasMore}` en orden ascendente, página más reciente por defecto.
Evento STOMP `{type, projectId, message}` en topic assistant; types
ASSISTANT_MESSAGE_CREATED, ASSISTANT_RESPONSE_CREATED,
ASSISTANT_REVIEW_COMPLETED. Ningún cliente invoca directamente al LLM.

### Task 1: Backend de conversación, contexto, permisos y EDIT

**Files:** Crear `backend/src/main/java/com/classforge/projectassistant/*.java`,
`persistence/*.java`, `backend/src/test/java/com/classforge/projectassistant/ProjectAssistantIntegrationTests.java`.
**Interfaces:** Produce el contrato compartido y API; consume ProjectService,
ProjectAccessService, membresías, usuarios y AssistantPlanService existentes.

- [x] Escribir pruebas HTTP de historial, acceso ajeno, modes, revisión,
  persistencia, reintento, fallos y no mutación; ejecutar y comprobar fallo
  por endpoint inexistente.
- [x] Implementar registros, entidad con orden e idempotencia persistidos,
  repositorios, contexto limitado, transacciones cortas, fachada y controller.
- [x] Verificar con `backend/gradlew.bat -p backend test --tests '*ProjectAssistantIntegrationTests'`.

### Task 2: Gateways conversacionales

**Files:** Crear `projectassistant/llm/*.java` y pruebas correspondientes.
**Interfaces:** Consume el contrato compartido; produce implementaciones del
gateway seleccionadas por `classforge.assistant.text.provider`.

- [x] Escribir pruebas con servidor HTTP local sobre payload conversacional,
  contexto, historial, respuesta vacía y errores; comprobar RED.
- [x] Implementar llama.cpp y Bedrock con temperatura 0.2, timeout configurable,
  prompt contextual limitado, texto normal sin herramientas obligatorias.
- [x] Verificar pruebas con filtro `*ProjectAssistantGatewayTests`.

### Task 3: Chat, reconciliación y revisión frontend

**Files:** Crear `frontend/src/app/features/project-assistant/*`; modificar
`projects/assistant/project-assistant-panel.component.*`, colaboración y routes.
**Interfaces:** Consume contrato HTTP/STOMP; produce chat compartido, revisión,
propuesta EDIT y ruta `/projects/:id/assistant`.

- [x] Añadir pruebas Node para merge/orden/aislamiento y paginación; comprobar RED.
- [x] Implementar API/store/panel con modos, historial paginado, estado de envío,
  recuperación y reconexión; integrar plan en preview/Apply existentes.
- [x] Implementar ruta responsive con resumen del proyecto y presencia existente;
  preservar voz/imagen en workspace.
- [x] Ejecutar pruebas frontend y `npm run build` con Node compatible.

### Task 4: Realtime y aceptación

**Files:** Modificar interceptor STOMP, crear pruebas de dos clientes y seguridad;
documentar comandos y demo en `docs/parcial2/README.md`.

- [x] Escribir pruebas de SUBSCRIBE assistant autorizado/ajeno y SEND denegado;
  comprobar RED y ampliar interceptor preservando la política existente.
- [x] Probar dos clientes reales recibiendo los mismos IDs, y concurrencia de
  POST idénticos generando una sola respuesta.
- [x] Ejecutar suite backend completa, caracterización frontend y build.
- [x] Revisar diff con contexto independiente; corregir fallos relevantes,
  registrar evidencia y límites de la demo real LLM.
- [x] Commit y `git push -u origin tiago`; verificar referencia remota.

## Registro de ejecución

- 2026-11-07: diseño aprobado, rama `tiago` creada usando user.name=Tiago.
- Decisión: reutilizar el clon dedicado y la rama autorizada; no crear otro
  worktree. El usuario autorizó implementar y subir, sin más aprobaciones.
- Los subsistemas frontend y adaptadores LLM tienen límites de archivos separados
  y contrato explícito, por lo que se pueden desarrollar en paralelo.
- Task 1: completo. Pruebas de endpoint inicialmente RED (404); 14 casos de
  integración GREEN incluyendo primeros envíos concurrentes y revisión capturada.
- Task 2: completo. 12 pruebas de gateways RED→GREEN; ningún proveedor real usado.
- Task 3: completo. 36 pruebas frontend GREEN y build de producción exitoso.
- Task 4: aceptación y publicación completas. Dos clientes WebSocket reales
  comparten mensajes; seguridad STOMP pasa. Suite backend: 395 aprobadas,
  10 opt-in omitidas, 0 errores/fallos. Browser móvil/escritorio pasa con proveedor
  simulado, incluyendo recuperación de dos eventos STOMP suprimidos.
- Revisión final independiente: dos hallazgos corregidos con pruebas RED→GREEN:
  recuperación periódica de cinco segundos mientras conectado e identidad de
  proyecto/generación en callbacks HTTP pendientes del workspace.
- Sin cambios en ProjectActivity, ProjectSession, ProjectNotification ni auditoría.
- Límite de evidencia: llama.cpp real y AWS no se ejecutaron; se documenta la
  configuración y demo para probar el modelo elegido.
- Publicación: commit de implementación 6981174 subido a origin/tiago; referencia remota verificada.
