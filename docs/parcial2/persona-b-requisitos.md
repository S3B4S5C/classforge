# ClassForge — Segundo Parcial
# Persona B: Asistente IA colaborativo, revisión y experiencia móvil

## 1. Objetivo de esta rama de trabajo

Esta parte del segundo parcial transforma el asistente actual de ClassForge en un **participante compartido del proyecto**.

La persona responsable de este documento puede trabajar en paralelo con la Persona A sin esperar a que existan:

- `ProjectActivity`;
- sesiones persistentes;
- notificaciones;
- historial de auditoría nuevo.

Esta rama utilizará únicamente:

```text
ProjectDocument actual
+
membresías existentes
+
historial propio del chat IA
+
infraestructura STOMP existente
```

La integración con el historial desarrollado por Persona A puede añadirse al final como una mejora, pero no es necesaria para terminar esta rama.

---

# 2. Requisitos asignados

## RF24 — Asistente IA compartido por proyecto

La conversación con la IA debe pertenecer al proyecto.

Todos los integrantes autorizados deben poder:

- ver los mensajes;
- ver respuestas de la IA;
- continuar una conversación iniciada por otro usuario;
- recargar la aplicación sin perder el historial.

---

## RF28 — IA como tutor, revisor y asesor

La IA debe dejar de servir únicamente para traducir texto a comandos UML.

Debe soportar:

```text
ASK
REVIEW
EDIT
```

y opcionalmente:

```text
SUMMARY_CURRENT
```

`SUMMARY_CURRENT` en esta rama resume solamente:

- UML actual;
- conversación IA del proyecto.

No depende del historial de actividad de Persona A.

---

## RF29 — Experiencia móvil informativa del asistente

Debe existir una experiencia responsive/PWA desde la cual un usuario pueda:

- consultar el proyecto;
- ver miembros/presencia;
- leer la conversación IA compartida;
- hacer preguntas;
- solicitar una revisión;
- consultar un resumen del estado UML actual.

No es necesario editar el diagrama completo desde móvil.

---

# 3. Regla de independencia

Esta rama NO debe depender de:

```text
ProjectActivity
ProjectSession
ProjectNotification
ProjectUserVisit
```

El asistente debe funcionar completamente aunque esas tablas y servicios no existan.

Contexto mínimo:

```text
ProjectAssistantContext
- ProjectDocument actual
- miembros autorizados
- mensajes recientes del chat IA
```

---

# 4. Paquetes sugeridos

Backend:

```text
backend/src/main/java/com/classforge/projectassistant/
```

Subpaquetes opcionales:

```text
projectassistant/application/
projectassistant/domain/
projectassistant/persistence/
projectassistant/web/
projectassistant/realtime/
projectassistant/llm/
```

Frontend:

```text
frontend/src/app/features/project-assistant/
```

Esto evita mezclar el nuevo comportamiento con el `assistant/` actual más de lo necesario.

---

# 5. Qué debe conservarse del asistente actual

No reemplazar:

```text
AssistantPlanService
AssistantNativeToolPlanner
UmlAssistantCommandResolver
preview
BATCH
Apply
```

La edición UML sigue siendo:

```text
Usuario
   ↓
EDIT
   ↓
AssistantPlanService
   ↓
plan
   ↓
BATCH
   ↓
preview
   ↓
Apply
```

El segundo parcial añade capacidades alrededor de ese flujo.

---

# 6. Nuevo ProjectAssistantService

Crear una fachada:

```text
ProjectAssistantService
```

Responsabilidades:

```text
- recibir mensajes;
- determinar modo;
- persistir conversación;
- construir contexto;
- llamar al gateway conversacional;
- delegar EDIT al sistema existente;
- publicar respuestas en realtime.
```

Flujo:

```text
mensaje
  ↓
ProjectAssistantService
  ↓
intent/mode
  ├── ASK
  ├── REVIEW
  ├── SUMMARY_CURRENT
  └── EDIT
```

---

# 7. Conversación compartida

## Entidad

```text
ProjectAiMessage
- id
- projectId
- authorUserId
- authorDisplayName
- role
- type
- content
- projectRevision
- createdAt
```

`role`:

```text
USER
ASSISTANT
SYSTEM
```

`type`:

```text
ASK
REVIEW
EDIT
SUMMARY_CURRENT
```

Para respuestas del asistente puede conservarse el tipo de la solicitud que las originó.

---

# 8. Repositorio

```text
ProjectAiMessageRepository
```

Consultas mínimas:

```text
findByProjectIdOrderByCreatedAt(...)
findRecentByProjectId(...)
```

El historial pertenece al proyecto, no al usuario.

---

# 9. API de conversación

Endpoints sugeridos:

```text
GET  /api/projects/{projectId}/assistant/messages
POST /api/projects/{projectId}/assistant/messages
```

Request:

```json
{
  "content": "Revisa las relaciones de Pedido",
  "mode": "REVIEW"
}
```

Response:

```json
{
  "userMessage": {},
  "assistantMessage": {},
  "projectRevision": 42
}
```

---

# 10. Realtime

Utilizar un destino propio.

Ejemplo:

```text
/topic/projects/{projectId}/assistant
```

Eventos:

```text
ASSISTANT_MESSAGE_CREATED
ASSISTANT_RESPONSE_CREATED
ASSISTANT_REVIEW_COMPLETED
```

Esto es independiente del canal de operaciones UML.

Dos clientes conectados deben recibir la misma conversación.

---

# 11. Contexto del asistente

Crear:

```text
ProjectAssistantContextService
```

Primera versión:

```text
ProjectAssistantContext
 ├── ProjectDocument actual
 ├── projectId
 ├── projectName
 ├── currentRevision
 ├── miembros
 └── últimos mensajes IA
```

No incluir todavía:

```text
ProjectActivity
ProjectSession
```

Eso permite trabajar completamente en paralelo.

---

# 12. Control de tamaño de contexto

No enviar siempre todo el historial.

Política inicial:

```text
ProjectDocument actual
+
últimos N mensajes
```

Ejemplo:

```text
N = 20
```

Si el chat crece:

```text
resumen conversacional
+
últimos mensajes
```

Este resumen pertenece al chat IA y no depende de las sesiones de Persona A.

---

# 13. Gateway conversacional

El gateway actual está especializado en tool calling.

Crear una interfaz separada:

```text
ProjectAssistantConversationGateway
```

Método conceptual:

```java
AssistantConversationResponse respond(
    ProjectAssistantContext context,
    String userMessage,
    AssistantMode mode
);
```

Implementaciones:

```text
LlamaCppProjectAssistantGateway
BedrockProjectAssistantGateway
```

---

# 14. Llama.cpp conversacional

Para ASK/REVIEW no usar:

```text
tool_choice = required
```

Usar chat completion normal.

Request conceptual:

```json
{
  "model": "local-model",
  "temperature": 0.2,
  "messages": [
    {
      "role": "system",
      "content": "..."
    },
    {
      "role": "user",
      "content": "..."
    }
  ]
}
```

La respuesta es texto, no tool calls.

---

# 15. System prompt

El prompt debe definir claramente que la IA:

- conoce el proyecto actual;
- es un asesor colaborativo;
- puede explicar y revisar;
- no debe afirmar que realizó cambios si no fueron aplicados;
- no debe modificar el UML automáticamente;
- debe distinguir observaciones de hechos;
- debe basarse en el `ProjectDocument` enviado.

Ejemplo conceptual:

```text
Eres el asistente colaborativo de ClassForge.

Trabajas con varios integrantes sobre el mismo proyecto UML.

Puedes:
- responder preguntas;
- revisar el modelo;
- señalar inconsistencias;
- sugerir mejoras;
- explicar decisiones.

No puedes afirmar que un cambio fue persistido salvo que ClassForge
lo haya aplicado mediante su pipeline normal.

Cuando revises UML, utiliza únicamente el ProjectDocument proporcionado.
```

---

# 16. Modo ASK

Ejemplos:

```text
¿Qué diferencia hay entre composición y agregación?

¿Esta relación debería ser muchos a muchos?

¿Qué representa esta generalización?

¿Por qué Pedido tiene esta relación con Cliente?
```

La IA responde utilizando:

```text
conocimiento general
+
ProjectDocument actual
+
chat reciente
```

No genera comandos.

---

# 17. Modo REVIEW

Ejemplo:

```text
Revisa el modelo actual.
```

La IA recibe una representación estructurada del UML.

Debe buscar, como mínimo:

- clases posiblemente aisladas;
- nombres inconsistentes;
- relaciones sospechosas;
- multiplicidades llamativas;
- atributos duplicados;
- posibles identificadores faltantes;
- problemas básicos para generación.

Respuesta sugerida:

```text
Revisión del modelo

1. DetallePedido no tiene un atributo que parezca identificador.

2. Cliente y Pedido están relacionados 1:1.
   Por el nombre de las entidades podría ser más probable 1:N.
   Revisa si esa cardinalidad representa el dominio.

3. Factura está aislada del resto del modelo.
```

La IA debe presentar esto como recomendaciones, no como verdad absoluta.

---

# 18. Resultado estructurado de REVIEW

Aunque la salida principal sea texto, conviene modelar observaciones.

```text
AssistantReview
- id
- projectId
- projectRevision
- requestedBy
- summary
- createdAt
```

Opcional:

```text
AssistantReviewFinding
- id
- reviewId
- severity
- category
- message
- elementRef
```

`severity`:

```text
INFO
WARNING
ERROR
```

Esto facilita mostrar tarjetas en UI.

---

# 19. Aplicar una sugerencia

Una revisión NO modifica el UML.

Ejemplo:

```text
IA:
"Podrías cambiar Cliente-Pedido de 1:1 a 1:N."

Usuario:
[Aplicar sugerencia]
```

Solo entonces:

```text
sugerencia
   ↓
convertir a instrucción EDIT
   ↓
AssistantPlanService existente
   ↓
BATCH
   ↓
preview
   ↓
Apply
```

No crear un segundo mecanismo de escritura.

---

# 20. Modo EDIT

Debe reutilizar completamente el primer parcial.

Ejemplo:

```text
Crea una clase Factura con id Long.
```

Flujo:

```text
ProjectAssistantService
      ↓ detecta EDIT
AssistantPlanService
      ↓
AssistantPlanResponse
      ↓
preview existente
      ↓
usuario confirma
      ↓
Apply existente
```

El nuevo chat puede persistir:

```text
Sebastián:
"Crea una clase Factura con id Long"

ClassForge AI:
"Preparé una propuesta para crear Factura con id Long."
```

Pero la persistencia UML sigue ocurriendo solo después de Apply.

---

# 21. Modo SUMMARY_CURRENT

Esta función debe ser independiente del historial de Persona A.

Resume:

```text
ProjectDocument actual
+
conversación IA reciente
```

Ejemplo:

```text
Estado actual del proyecto

- 8 clases.
- 11 relaciones.
- Cliente se relaciona con Pedido y Dirección.
- Pedido contiene DetallePedido.
- La última conversación se centró en revisar Factura.
```

Cuando ambas ramas se integren, podrá añadirse actividad histórica.

No es necesario para esta entrega.

---

# 22. Detección de modo

Opción simple y robusta:

La UI envía explícitamente:

```text
ASK
REVIEW
EDIT
SUMMARY_CURRENT
```

No hacer que un LLM tenga que adivinar todo inicialmente.

Ejemplo:

```text
[Preguntar]
[Revisar proyecto]
[Editar con IA]
[Resumir estado]
```

El chat libre puede utilizar `ASK` por defecto.

---

# 23. Permisos

Reglas:

```text
OWNER / EDITOR:
- ASK
- REVIEW
- SUMMARY_CURRENT
- EDIT

usuario sin acceso:
- nada
```

Si posteriormente aparecen permisos READ_ONLY, podrían permitirse:

```text
ASK
REVIEW
SUMMARY_CURRENT
```

sin EDIT.

---

# 24. Concurrencia y revisión

Cada mensaje debe almacenar:

```text
projectRevision
```

Si una revisión se generó sobre:

```text
revision 42
```

y el proyecto ahora está en:

```text
revision 45
```

la UI puede mostrar:

```text
Esta revisión fue realizada sobre una versión anterior del proyecto.
[Revisar nuevamente]
```

Esto evita recomendaciones obsoletas.

---

# 25. Frontend — panel de asistente

Crear un feature independiente:

```text
ProjectAssistantPanelComponent
```

Subcomponentes:

```text
AssistantMessageListComponent
AssistantMessageInputComponent
AssistantModeSelectorComponent
AssistantReviewCardComponent
AssistantReviewFindingComponent
AssistantContextBadgeComponent
```

---

# 26. UX propuesta

```text
┌─────────────────────────────────────┐
│ ClassForge AI                       │
│ Proyecto: Veterinaria · rev 42      │
├─────────────────────────────────────┤
│ Sebastián                           │
│ Revisa las relaciones de Pedido.    │
│                                     │
│ ClassForge AI                       │
│ Encontré 2 observaciones...         │
│                                     │
│ [Ver revisión]                      │
│                                     │
├─────────────────────────────────────┤
│ Preguntar | Revisar | Editar        │
│ [Escribe un mensaje...]             │
└─────────────────────────────────────┘
```

---

# 27. Colaboración del chat

Con dos navegadores:

```text
Sebastián escribe
        ↓
backend persiste
        ↓
STOMP
        ↓
Sebastián ve mensaje
Marco ve mensaje
        ↓
IA responde
        ↓
backend persiste
        ↓
STOMP
        ↓
ambos ven respuesta
```

La conversación es una sola por proyecto.

---

# 28. Evitar respuestas duplicadas

Solo el backend genera la respuesta de IA.

Los clientes nunca llaman directamente al modelo.

Flujo:

```text
Angular
   ↓
Spring Boot
   ↓
LLM
   ↓
Spring Boot
   ↓
broadcast
```

Así dos clientes no producen dos respuestas para un mismo mensaje.

---

# 29. Persistencia conversacional

Al recargar:

```text
GET /assistant/messages
```

y después:

```text
suscripción STOMP
```

Orden recomendado:

1. recuperar historial;
2. establecer realtime;
3. reconciliar mensajes por ID.

Evitar duplicados utilizando `messageId`.

---

# 30. Vista responsive / móvil

Esta rama debe proporcionar una experiencia móvil centrada en IA y estado del proyecto.

No implementar JointJS móvil completo.

Vista:

```text
CLASSFORGE
Veterinaria

rev 42
2 colaboradores en línea

[Resumen]
8 clases
11 relaciones

[ClassForge AI]

Sebastián:
Revisa el proyecto.

IA:
Encontré 2 observaciones...

[Escribir...]
```

---

# 31. Ruta móvil

Puede ser la misma aplicación Angular responsive.

Ejemplo:

```text
/projects/{id}/assistant
```

No hace falta otra aplicación Flutter.

Opcionalmente puede configurarse como PWA posteriormente.

---

# 32. Información del proyecto en móvil

Mostrar:

- nombre;
- revisión actual;
- cantidad de clases;
- cantidad de relaciones;
- colaboradores presentes;
- últimas conversaciones;
- última revisión IA.

Esto se obtiene de servicios que ya existen o de esta propia rama.

No depender de `ProjectActivity`.

---

# 33. Presencia

Reutilizar la presencia ya implementada en el primer parcial.

En móvil:

```text
En línea

● Sebastián
● Marco
```

No crear otro sistema de presencia.

---

# 34. Pruebas mínimas

## Mensajes

- crear mensaje;
- persistir;
- recuperar;
- aislamiento por proyecto;
- orden cronológico.

## Realtime

- A escribe;
- B recibe;
- IA responde una sola vez;
- A y B reciben la misma respuesta.

## ASK

- responde sin generar BATCH;
- no cambia ProjectDocument.

## REVIEW

- recibe UML actual;
- devuelve observaciones;
- conserva projectRevision;
- no modifica UML.

## EDIT

- reutiliza AssistantPlanService;
- produce preview;
- no persiste antes de Apply.

## Seguridad

- usuario sin acceso no lee chat;
- usuario sin acceso no publica;
- conversaciones de proyectos diferentes no se mezclan.

---

# 35. Demo independiente

Esta demo NO necesita nada de Persona A.

## Escenario

Abrir ClassForge con:

```text
Sebastián
Marco
```

en el mismo proyecto.

### Paso 1 — Chat compartido

Sebastián:

```text
¿Qué opinas del modelo actual?
```

La IA responde.

Marco ve tanto pregunta como respuesta sin recargar.

---

### Paso 2 — Continuar conversación

Marco:

```text
Revisa específicamente las relaciones de Pedido.
```

La IA genera una revisión.

Sebastián también la ve.

---

### Paso 3 — Review

Mostrar:

```text
REVIEW · rev 42

WARNING
Cliente-Pedido tiene multiplicidad 1:1.

INFO
Factura está aislada del resto del modelo.
```

No se modifica el UML.

---

### Paso 4 — Edit

Sebastián:

```text
Cambia la relación Cliente-Pedido a uno a muchos.
```

El sistema utiliza el flujo existente:

```text
AssistantPlanService
→ preview
```

Mostrar la previsualización.

Solo al confirmar:

```text
Apply
```

se modifica el proyecto.

---

### Paso 5 — Móvil

Abrir la misma ruta desde viewport móvil.

Debe poder:

- ver proyecto;
- ver presencia;
- leer chat;
- realizar ASK;
- solicitar REVIEW;
- ver el resumen actual.

---

# 36. Archivos que esta persona debería evitar tocar

Para minimizar conflictos con Persona A:

```text
backend/.../activity/**
backend/.../session/**
backend/.../notification/**
frontend/.../features/activity/**
frontend/.../features/notifications/**
```

No modificar la persistencia de auditoría.

---

# 37. Puntos de integración futuros

Cuando Persona A termine, esta rama podrá añadir opcionalmente herramientas/contexto:

```text
getRecentActivity(projectId)
getActivityByActor(projectId, actorId)
getSinceRevision(projectId, revision)
getCurrentOrLastSession(projectId)
```

Entonces podrán soportarse preguntas como:

```text
¿Qué hizo Marco hoy?
¿Qué me perdí desde ayer?
¿Quién creó Factura?
```

Pero estas preguntas históricas NO forman parte del bloqueo de esta rama.

El asistente debe estar terminado antes de integrar esas APIs.

---

# 38. Cómo integrar sin romper independencia

Añadir una interfaz opcional:

```text
ProjectAssistantHistoricalContextProvider
```

Implementación inicial:

```text
NoOpHistoricalContextProvider
```

Devuelve:

```text
sin contexto histórico adicional
```

Después de merge con Persona A:

```text
ActivityHistoricalContextProvider
```

consume sus servicios.

Así Persona B puede desarrollar desde el día 1 sin esperar.

---

# 39. Definition of Done

Esta rama está terminada cuando:

- existe conversación IA persistente por proyecto;
- dos usuarios comparten la misma conversación;
- funciona realtime;
- ASK funciona;
- REVIEW funciona;
- SUMMARY_CURRENT funciona;
- EDIT reutiliza el pipeline existente;
- REVIEW nunca modifica automáticamente el UML;
- cada respuesta conoce la revisión actual;
- existe UI de chat;
- existe UI de revisión;
- funciona en viewport móvil;
- reutiliza presencia existente;
- hay seguridad por proyecto;
- puede demostrarse sin `ProjectActivity`, sesiones ni notificaciones nuevas.

---

# 40. Dificultad relativa

Carga aproximada por complejidad técnica:

```text
Persistencia de chat          ███
Realtime compartido           ████
Gateway LLM conversacional    ████
Context building              ████
ASK / REVIEW / EDIT           ████
Frontend y responsive         ████
Pruebas y concurrencia        ████
```

Es una rama principalmente de:

```text
IA
+
backend de aplicación
+
realtime
+
integración con pipeline existente
+
UX colaborativa
```

Su dificultad global está pensada para ser comparable con la rama de Persona A.
