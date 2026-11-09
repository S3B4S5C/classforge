# Segundo parcial — Persona B

El cierre de trazabilidad, continuidad y notificaciones de Persona A y la migración
NoSQL están en [Persona A — cierre y MongoDB](persona-a-cierre.md).
Las instrucciones de base H2 de la verificación histórica de Persona B fueron reemplazadas por ese runbook.
Desde el 8 de noviembre toda la IA activa usa Groq. La configuración vigente y
las cuotas están en [asistente Groq](../runtime/assistant-groq.md).

Implementación del asistente colaborativo de proyecto (RF24, RF28 y RF29).
Los requisitos recibidos están en [persona-b-requisitos.md](persona-b-requisitos.md)
y el diseño aprobado en
[diseño técnico](../superpowers/specs/2026-11-07-asistente-colaborativo-persona-b-design.md).

## Funcionalidades

- Conversación persistida con MongoDB por proyecto, visible para OWNER y EDITOR,
  con autor, modo, fecha y revisión del UML utilizada.
- ASK: preguntas y asesoría contextual sin generar comandos.
- REVIEW: revisión textual y observaciones estructuradas cuando las entrega
  el modelo; las referencias se validan contra la instantánea UML.
- SUMMARY_CURRENT: resumen del documento actual y conversación reciente.
- EDIT: delegación en AssistantPlanService y preview/Apply existentes.
  Preparar una recomendación nunca aplica automáticamente el cambio.
- Historial paginado y canal STOMP independiente del de operaciones UML.
- Reconciliación al conectar/reconectar y cada cinco segundos mientras hay
  conexión, para recuperar mensajes incluso si SUBSCRIBE todavía no se había
  registrado o se perdió un evento. HTTP y STOMP se combinan por ID.
- Ruta responsive `/projects/:id/assistant` con nombre, revisión, clases,
  relaciones, miembros, presencia y chat. Las propuestas se aplican desde el
  workspace; la vista móvil no necesita montar el lienzo JointJS.
- Se conservan voz, imagen, validación y pipeline de comandos existentes.

El chat funciona sin depender de ProjectActivity, ProjectSession, ProjectNotification ni
ProjectUserVisit. Esas features de Persona A están disponibles de manera independiente.
No se requiere instalar una PWA ni otra aplicación móvil.

## Ejecutar

Requisitos: Docker para MongoDB replica set (o un replica set propio), JDK 21 y Node compatible con Angular
22 (rama 22 desde 22.22.3, o rama 24 desde 24.15.0). La verificación integrada
se hizo con Temurin 21.0.13, Node 24.22.1 y MongoDB 8.0.17.

Backend, desde la raíz:

```powershell
docker compose -f compose.mongo.yml up -d --wait
.\backend\gradlew.bat -p backend bootRun
```

Frontend, en otro terminal:

```powershell
cd frontend
npm ci
npm start
```

Frontend: `http://localhost:4200`. Backend: `http://localhost:8082`.
El proxy Angular existente redirige `/api` y `/ws` al backend.

El proveedor es Groq para texto, herramientas, imágenes y voz. Configurar
GROQ_API_KEY en el backend mediante `.env` ignorado o variable de entorno;
ver [configuración Groq](../runtime/assistant-groq.md).
El backend informa un error recuperable si el proveedor no está disponible:
no sustituye una respuesta real por contenido ficticio.

Los adaptadores históricos de llama.cpp y Bedrock quedan para regresión;
no se seleccionan en los perfiles normales ni se usan como fallback.

Variables nuevas opcionales:

| Variable | Predeterminado | Uso |
| --- | --- | --- |
| CLASSFORGE_ASSISTANT_CONVERSATION_TIMEOUT_MILLIS | 90000 | Tiempo máximo de consulta |
| CLASSFORGE_ASSISTANT_CONVERSATION_MAX_CONTEXT_CHARS | 18000 | Límite de contexto serializado |
| CLASSFORGE_ASSISTANT_CONVERSATION_MAX_TOKENS | 2048 | Tokens máximos de respuesta |

Se envía el documento actual, miembros y los mensajes recientes completos que
entren en el presupuesto, con un máximo de 20. Si el contexto obligatorio excede
el límite se devuelve un error explícito; no se corta el documento JSON ni se
inventa un resumen. La respuesta admite hasta 20000
caracteres. La conversación completa permanece en la base de datos.

## API

Se requiere Bearer JWT y acceso al proyecto en HTTP y STOMP.

```text
GET  /api/projects/{projectId}/assistant/messages?limit=50&before={sequence}
POST /api/projects/{projectId}/assistant/messages
STOMP /topic/projects/{projectId}/assistant
```

`limit` acepta 1–100; `before` es exclusivo y opcional. GET devuelve la página
más reciente en orden cronológico, con `{messages, hasMore}`. Cada mensaje
tiene un `sequence` estable para paginar y un UUID `id` para reconciliar.

Ejemplo de POST:

```json
{
  "content": "Revisa las relaciones del modelo actual",
  "mode": "REVIEW",
  "requestId": "a188c43e-96cf-4728-948c-8c8af87406f5"
}
```

`mode` predeterminado: ASK. `requestId` es obligatorio. La respuesta contiene
`userMessage`, `assistantMessage` y `projectRevision`. El mensaje del asistente
puede contener `review` o `plan`; ambos sobreviven a recargas del historial.

Un reintento debe conservar requestId, contenido, modo y autor. Si la respuesta
ya existe, se devuelve la misma; una petición en proceso devuelve HTTP 409.
Una petición diferente que intenta reutilizar ese ID también devuelve 409.
Un fallo del proveedor devuelve 503, o 429 con Retry-After si se agota una cuota,
y conserva la pregunta para reintentar manualmente con el mismo ID.
Una solicitud abandonada se puede recuperar a los
diez minutos; un token evita que un intento anterior publique otra respuesta.
La restricción única en base de datos protege los primeros envíos concurrentes.

Las llamadas LLM se hacen fuera de transacciones y bloqueos de base de datos.
Los eventos se publican después de confirmar cada escritura. Los clientes no
pueden publicar directamente en el broker ni generar mensajes ASSISTANT.

Cada respuesta conserva la revisión de su instantánea; EDIT usa específicamente
`AssistantPlanResponse.baseRevision`. REVIEW antiguo muestra un aviso y permite
preparar otra revisión. Las consultas nunca guardan el ProjectDocument.

## Verificación ejecutada

La integración MongoDB + Groq del 2026-11-08 terminó con 507 pruebas backend
aprobadas y 10 opt-in omitidas, 46 pruebas frontend aprobadas, builds de backend
y Angular correctos y aceptación explícita de los proyectos exportados Simple/Auth.
Se verificaron ASK, REVIEW, SUMMARY_CURRENT, EDIT, voz e imagen con Groq real.
La evidencia y sus límites están en
[integración Groq](../evidence/parcial2/groq-integration.md).

### Verificación histórica de Persona B

2026-11-07, sobre la rama `tiago`:

- Suite backend completa: 405 casos encontrados, **395 aprobados, 10 omitidos,
  cero fallos y cero errores**. Los omitidos son acceptance/benchmarks opt-in
  heredados para modelos reales y aplicaciones generadas.
- Nuevos casos backend: 12 gateways, 14 integración HTTP/persistencia/concurrencia,
  3 seguridad STOMP y 2 pruebas WebSocket con clientes reales.
- Frontend: **36 pruebas aprobadas**, incluyendo reconciliación, reintentos,
  historial paginado, reloj virtual y operaciones tardías A→B.
- Build Angular de producción y empaquetado bootJar exitosos.
- Prueba en navegador Edge headless con viewport móvil 390×844 y escritorio
  1440×1000: ASK, REVIEW con tarjeta, SUMMARY_CURRENT, recarga de historial,
  dos usuarios, presencia y documento/revisión UML sin cambios. Sin errores
  JavaScript ni desbordamiento horizontal móvil.
- Se suprimieron deliberadamente los eventos STOMP de una pregunta y respuesta;
  la reconciliación periódica recuperó ambos mensajes en el navegador.
- Revisión independiente de código; se corrigieron el intervalo de registro de
  SUBSCRIBE y las respuestas HTTP tardías de rename/save/validate/resync al
  cambiar de proyecto.

Comandos reproducibles:

```powershell
.\backend\gradlew.bat -p backend test
.\backend\gradlew.bat -p backend bootJar
cd frontend
npm run test:characterization
npm run build
```

Los gateways se probaron con servidor HTTP local y doble del SDK de Bedrock.
La prueba de navegador usó una base H2 en memoria, usuarios demo y un proveedor
simulado local aislado. **No se comprobó la calidad de un modelo real ni se
invocó AWS**: no había un runtime llama.cpp activo en el equipo durante esta
verificación histórica. La comprobación posterior con Groq real está documentada arriba.

## Demo del examen

El perfil demo crea Veterinaria y dos miembros en MongoDB. Por defecto usa
`classforge_demo`; el perfil normal usa `classforge`. Al arrancar se reconstruyen
los documentos del fixture, incluidos su chat y actividad, sin vaciar toda la base.
Si se configura CLASSFORGE_MONGODB_URI, el perfil utiliza la base indicada en esa
URI: usar una base dedicada para la demo. El perfil normal conserva los proyectos
habituales. El reset completo de la base demo local está en `scripts/demo-reset.ps1`.

```powershell
docker compose -f compose.mongo.yml up -d --wait
.\backend\gradlew.bat -p backend bootRun --args="--spring.profiles.active=demo"
```

Configurar GROQ_API_KEY como se indica en la sección Ejecutar. Con backend y
frontend iniciados, comprobar la demo y disponibilidad de modelos:

```powershell
.\scripts\demo-smoke.ps1 -RequireAi
```

Usuarios demo: `demo@classforge.local` y `colaborador@classforge.local`;
contraseña local de ambos: `classforge-demo` (fixture del repositorio original).
Proyecto: `27000000-0000-0000-0000-000000000100`.

1. Comprobar Groq con el smoke y abrir dos navegadores con esos usuarios.
2. Entrar al mismo proyecto y preguntar con ASK. Ambos reciben la conversación.
3. Continuar desde el colaborador y solicitar REVIEW; comprobar que el UML no cambia.
4. Preparar una sugerencia con EDIT, revisar preview y confirmar Apply desde
   el workspace. Mostrar que un plan obsoleto exige volver a planificar.
5. Abrir `/projects/27000000-0000-0000-0000-000000000100/assistant` en móvil;
   consultar miembros/presencia, preguntar, revisar y resumir.
6. Recargar y comprobar que se recuperan mensajes, revisiones y propuestas.
