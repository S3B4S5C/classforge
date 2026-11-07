# Segundo parcial: asistente colaborativo de ClassForge

Estado: diseño aprobado por el usuario el 2026-11-07. Implementación y evidencia
en `docs/parcial2/README.md`.
Fecha: 2026-11-07.
Base: `main`, commit `de1fb449f760f6076e9d54c54b99bd7f4119c164`.
Requisitos entregados: `docs/parcial2/persona-b-requisitos.md`.

## Intención y alcance

Desarrollar la parte de Persona B sobre el sistema existente del compañero:
convertir la IA en un participante del proyecto, con conversación persistente
compartida, asesoría UML y acceso desde móvil. El mismo proyecto y sus miembros
comparten mensajes aunque recarguen la aplicación o utilicen distintos clientes.

La entrega cubre RF24, RF28 y RF29, incluyendo ASK, REVIEW, EDIT y
SUMMARY_CURRENT. Aunque el resumen aparece como opcional al principio del
documento original, su Definition of Done lo exige; se incluye en esta propuesta.

No requiere ProjectActivity, ProjectSession, ProjectNotification ni
ProjectUserVisit. Tampoco incluye consultas sobre quién modificó el modelo,
auditoría nueva, edición completa de JointJS desde móvil o una aplicación Flutter
adicional. La ruta Angular responsive satisface la entrega; instalar como PWA
queda como mejora posterior, según el apartado 31 del documento original.

## Estado comprobado en el código

- Backend: Java con compilación objetivo 21, Spring Boot 4.0.8, Gradle Wrapper
  9.2.0, Spring Data JPA, H2 de desarrollo y driver PostgreSQL disponible.
- Frontend: Angular 22, Material, JointJS y STOMP. `package.json` exige Node
  >=22.22.3; la instalación detectada es 22.19.0. Java instalado: Temurin 25.0.1.
- `ProjectService.get` comprueba acceso y devuelve documento y revisión.
- `ProjectAccessService` ya define OWNER/EDITOR/NONE y controles de lectura y
  edición; se reutilizan, igual que las membresías y usuarios actuales.
- `AssistantPlanService` ya produce propuestas de comandos sin guardar UML.
- `projects/assistant/project-assistant-panel.component.ts` mantiene mensajes
  locales con signals, llama a `/assistant/plan` y aplica mediante
  `ProjectWorkspaceStore.applyAssistantCommand`.
- `ProjectCollaborationService` ya gestiona conexión, reconexión y presencia.
- `ProjectStompAuthorizationInterceptor` permite únicamente destinos de
  operaciones/presencia. El destino de asistente necesita autorización explícita.
- Ya existen adaptadores de herramientas para llama.cpp y Bedrock, elegidos por
  `classforge.assistant.text.provider`. No equivalen a un gateway conversacional.
- La configuración de pruebas usa H2 en memoria; la base de desarrollo usa
  `backend/data`, que no debe emplearse en pruebas.

La inspección anterior describe la base antes de implementar. La evidencia
posterior está en `docs/parcial2/README.md`; el modelo LLM real sigue sin probarse.

## Enfoque propuesto

El documento entregado determina el enfoque: un módulo de asistente de proyecto
que rodea al pipeline UML existente. Se propone conservarlo.

Reemplazar el planner o crear otra vía de escritura contradice los requisitos
5, 19 y 20. Depender del historial de Persona A contradice la regla de
independencia. Por estas restricciones no se proponen como alternativas válidas.

La decisión de implementación propuesta es añadir el backend en
`com.classforge.projectassistant` y el frontend conversacional en
`features/project-assistant`. La integración reutiliza el componente o extrae
la presentación de preview/Apply existente, evitando tener dos componentes
Angular con el mismo nombre y dos implementaciones del chat.

## Backend y persistencia

`ProjectAssistantService` coordina permisos, captura de contexto, persistencia,
selección de modo, gateway conversacional y publicación realtime.

`ProjectAiMessage` almacena los campos del documento original: identidad,
proyecto, autor, nombre visible, rol, modo, contenido, revisión y fecha. Se
propone añadir un identificador de solicitud para enlazar pregunta y respuesta
y evitar que reintentos de una misma solicitud generen otra respuesta.

El autor humano se obtiene de la identidad autenticada. El cliente nunca elige
el usuario autor, el rol ASSISTANT o la revisión. El historial se consulta por
proyecto, con orden estable y paginación. Para alimentar al modelo se usan los
últimos 20 mensajes, con límites configurables de tamaño de entrada y contexto.
La paginación del historial completo es independiente de ese límite de contexto.

Para REVIEW se persiste `AssistantReview` con resumen y revisión. Se propone
incluir findings con severidad, categoría, mensaje y referencia opcional a un
elemento; la UI debe admitir una revisión textual si no hay findings válidos.

No se mantiene una transacción de base de datos ni un bloqueo del proyecto
abierto durante la llamada LLM. La pregunta se confirma antes de emitir su
evento; respuesta y revisión se confirman antes de publicar sus eventos. Un
fallo del proveedor conserva la pregunta y produce un error recuperable, sin
inventar una respuesta del asistente ni un cambio aplicado.

## Contexto y modos

`ProjectAssistantContextService` captura un `ProjectDocument`, su revisión,
nombre de proyecto, miembros autorizados y mensajes recientes. El documento y
la revisión deben pertenecer a la misma instantánea. No incluye sesiones o
actividad histórica.

La UI envía el modo explícitamente; ASK es el predeterminado:

| Modo | Resultado | Escritura UML |
| --- | --- | --- |
| ASK | Explicación contextual y continuidad de conversación | Ninguna |
| REVIEW | Revisión y observaciones sobre el modelo capturado | Ninguna |
| SUMMARY_CURRENT | Estado UML actual y conversación reciente | Ninguna |
| EDIT | `AssistantPlanService` y preview existentes | Solo tras Apply existente |

Cada respuesta informa la revisión realmente utilizada, aunque el proyecto
avance durante la generación. La UI compara esa revisión con la actual y
permite solicitar otra revisión. Para EDIT la revisión autoritativa es
`AssistantPlanResponse.baseRevision`; no se debe etiquetar el plan con una
revisión diferente tomada por la fachada antes de llamar al planner.

Aplicar una recomendación de REVIEW prepara una solicitud EDIT y abre preview.
No ejecuta la sugerencia directamente. Los modos de consulta no llaman al bus
de comandos ni guardan ProjectDocument.

## Gateway conversacional

Interfaz independiente `ProjectAssistantConversationGateway` con adaptadores
llama.cpp y Bedrock. Se propone reutilizar la selección de proveedor y las
variables de configuración actuales, manteniendo llama.cpp como predeterminado.
Bedrock se usa solo si ya está configurado; no se provisionan servicios AWS.

ASK/REVIEW/SUMMARY_CURRENT usan conversación normal, sin `tool_choice=required`.
El system prompt identifica al asistente colaborativo, limita las afirmaciones
al contexto enviado y distingue hechos y recomendaciones. El contenido de
usuarios, nombres UML y mensajes previos es contexto no confiable, no una nueva
fuente de instrucciones del sistema.

En REVIEW se solicitan observaciones sobre clases aisladas, nombres,
relaciones, multiplicidades, duplicados, posibles identificadores faltantes y
problemas de generación. Las referencias estructuradas se validan contra el
documento; no se presentan referencias inexistentes como elementos reales.

## API y colaboración

- `GET /api/projects/{projectId}/assistant/messages`: historial autorizado,
  paginado y cronológico.
- `POST /api/projects/{projectId}/assistant/messages`: contenido, modo e
  identificador de solicitud; devuelve pregunta, respuesta y revisión, además
  de review o plan cuando corresponda.
- `/topic/projects/{projectId}/assistant`: eventos de mensaje, respuesta y
  revisión completada publicados por el backend.

OWNER/EDITOR pueden consultar y publicar; quien no tiene acceso no puede leer
por HTTP ni suscribirse por STOMP. Se amplía el interceptor actual con el
destino assistant, conservando el bloqueo de SEND directo al broker.

Solo el backend invoca al modelo. Una solicitud identificada genera como
máximo una respuesta persistida; dos mensajes distintos de dos miembros siguen
siendo dos solicitudes legítimas. La política de idempotencia y sus pruebas
deben cubrir reintentos concurrentes, no solo deshabilitar un botón.

El cliente carga historial, suscribe realtime y vuelve a reconciliar historial
tras conectar y reconectar. La segunda recuperación cubre el intervalo entre
la primera carga y la suscripción. Los mensajes recibidos por HTTP y STOMP se
combinan por ID para evitar pérdidas y duplicados.

## Interfaz y móvil

Un único estado conversacional por proyecto administra historial, envío,
reconciliación, errores y reconexión. Los componentes muestran autor, contenido,
modo, fecha y revisión, con controles explícitos Preguntar, Revisar, Editar y
Resumir. Se preservan las funciones de voz e imagen ya existentes.

El workspace integra el chat y sus revisiones con preview/Apply existentes.
La ruta autenticada `/projects/:id/assistant` ofrece una vista responsive con
nombre, revisión, número de clases/relaciones, miembros, presencia y chat.
Reutiliza los servicios actuales de presencia y colaboración sin exigir
montar el lienzo JointJS. En móvil el alcance obligatorio son consultas,
revisión y resumen; la edición completa del diagrama no forma parte del alcance.

La presencia debe entrar/salir y renovar su estado mediante el mismo servicio
existente. Cambiar entre proyectos o destruir la vista libera suscripciones y
evita mostrar mensajes o presencia del proyecto anterior.

## Secuencia propuesta de desarrollo

1. Preparar una rama de trabajo y entorno compatible; verificar la base con
   pruebas existentes y build frontend antes de atribuir fallos a esta entrega.
2. Implementar mensajes, autorización HTTP, historial y contexto, con pruebas
   de persistencia, aislamiento, orden y límites.
3. Implementar gateways y ASK/REVIEW/SUMMARY_CURRENT; verificar que no mutan UML.
4. Integrar EDIT con planner y preview existentes; verificar revisión y Apply.
5. Integrar eventos STOMP, autorización, idempotencia y reconciliación.
6. Integrar chat/revisiones en workspace y ruta móvil reutilizando presencia.
7. Ejecutar pruebas de regresión, build y demo con dos clientes y viewport móvil.

Esta secuencia es una propuesta de diseño, no una lista de tareas ejecutadas.
El plan de implementación detallado se prepara después de revisar este diseño.

## Aceptación y evidencia requerida

- Historial persiste tras recargar y coincide para dos miembros del proyecto.
- Otro proyecto no comparte mensajes; usuarios ajenos fallan en GET, POST y
  SUBSCRIBE. Los clientes no pueden publicar respuestas falsas en el topic.
- Dos clientes reciben pregunta y respuesta idénticas por ID. Una repetición
  de la misma solicitud no vuelve a generar respuesta.
- Reconectar o cargar historial mientras llegan mensajes no pierde ni duplica
  mensajes. Cambiar de proyecto no deja estado anterior visible.
- ASK/REVIEW/SUMMARY_CURRENT conservan documento y revisión UML; el modelo
  recibe la instantánea actual y contexto reciente limitado.
- REVIEW muestra recomendaciones y revisión utilizada, con aviso de revisión
  antigua cuando corresponda. Sugerencias requieren EDIT y preview.
- EDIT delega en AssistantPlanService, no guarda antes de Apply y rechaza un
  plan obsoleto mediante el mecanismo existente.
- Fallo o timeout del LLM conserva la pregunta y permite recuperación sin
  mostrar una respuesta inexistente como exitosa.
- La ruta móvil permite leer, preguntar, revisar y resumir, con miembros y
  presencia. La demo se ejecuta sin módulos nuevos de Persona A.
- Las pruebas automáticas usan dobles de gateways y H2 de pruebas. Una prueba
  manual adicional con el proveedor real se reporta por separado.

## Decisiones propuestas y pendientes

Se proponen mantener el stack actual, llama.cpp predeterminado con soporte
Bedrock configurable, incluir SUMMARY_CURRENT, priorizar responsive sobre PWA
instalable, contexto de 20 mensajes y añadir idempotencia de solicitudes.
Son decisiones de esta propuesta técnica, no acuerdos nuevos atribuidos al
usuario. No se ha confirmado ningún cambio de requisitos con el docente.

El usuario aprobó este diseño y autorizó implementar y publicar en una rama con
su nombre. La rama elegida es `tiago`, según user.name de Git. Queda por comprobar
el runtime/modelo IA real para la demo; no es necesario para las pruebas con
gateways sustituidos.
