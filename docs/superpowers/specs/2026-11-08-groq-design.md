# Proveedor único Groq

Solicitud del usuario: integrar ambas entregas y cambiar toda la IA a Groq para
despliegue con plan gratuito, conservando las funciones actuales.

Base integrada: `e293845`, que ya contiene Persona A, Persona B y MongoDB.
Las ramas locales main y tiago se actualizaron por fast-forward, sin conflictos.

## Alcance y enfoque

Groq será el proveedor configurado en desarrollo y AWS para texto conversacional,
tool calling UML, visión y voz. Los asistentes de datos de los proyectos
generados también usarán Groq. No se inicia llama.cpp, whisper.cpp ni Bedrock,
no se hace fallback a proveedores de pago y no se cambia preview/BATCH/Apply.
Los adaptadores históricos pueden mantenerse para regresión, sin activarse en
la configuración de ejecución normal. No se modifica la persistencia MongoDB.

Un transporte HTTP Groq compartido administra autenticación, timeouts, JSON y
errores saneados. Los gateways traducen el contrato existente a OpenAI-compatible
chat completions y audio/transcriptions. Visión reutiliza el pipeline validado
de extracción/grounding/OpenCV, con transporte Groq y validación JSON posterior.

Modelos iniciales configurables, comprobados en la documentación oficial:

- Texto/chat/tools: `openai/gpt-oss-120b`.
- Imagen: `qwen/qwen3.8-27b` (preview, con texto/imagen, tools y JSON).
- Transcripción: `whisper-large-v3-turbo`.

La API key solo existe en backend/variables de despliegue o `.env` ignorado.
Nunca se envía al frontend, ZIP generado, health, logs o mensajes de error.
Falta de clave no impide iniciar la aplicación; health informa NOT_CONFIGURED y
el intento de uso devuelve error claro. Modelos/ruta API se configuran por entorno.

El plan gratuito tiene cuotas por organización, no es capacidad ilimitada ni
una garantía de producción. 429 conserva Retry-After y permite reintento manual
sin multiplicar llamadas automáticamente. El presupuesto de contexto se limita
para evitar enviar conversaciones ilimitadas. Una revisión/edición nunca se
presenta como aplicada por el proveedor. Las pruebas automáticas usan mocks HTTP.

## Contrato de transporte

Paquete `com.classforge.assistant.groq`:
`GroqClient(JsonMapper mapper, String baseUrl, String apiKey, long timeoutMillis)`.
Métodos `JsonNode chat(Map<String,Object> payload)`, `JsonNode models()`,
`JsonNode transcribe(byte[] audio, String contentType, String filename,
String model, String language)`, `boolean isConfigured()`.
baseUrl predeterminado `https://api.groq.com/openai/v1` (sin barra final).

`GroqApiException` extiende AssistantPlanningException, con constructores
`(int statusCode, long retryAfterSeconds, String message)` y
`(int statusCode, long retryAfterSeconds, String message, Throwable cause)`;
accesores `statusCode()` y `retryAfterSeconds()`. Sin cuerpo bruto del proveedor.

Propiedades: `classforge.assistant.groq.{api-key,base-url,text-model,vision-model,
speech-model,request-timeout-millis}`. Variables `GROQ_API_KEY`,
`GROQ_BASE_URL`, `GROQ_TEXT_MODEL`, `GROQ_VISION_MODEL`, `GROQ_SPEECH_MODEL`.
Configuración principal fija providers text/vision/voice a groq; los tests
legados usan proveedores explícitos en su aplicación de pruebas.

## Validación

Pruebas de autenticación/transporte, errores 401/429/5xx y timeout, tools con
grounding real, JSON/visión/base64, multipart voz, health sin clave, wiring de
Groq en todos los perfiles y generación de apps con Groq sin secretos incrustados.
Regresión completa backend MongoDB, tests frontend y build de producción.
Una prueba real requiere GROQ_API_KEY. Se configuró en el archivo local ignorado
por Git y se verificaron el acceso a los tres modelos y las llamadas reales de
chat, revisión, resumen, EDIT y voz; la evidencia final se registra por separado.

## Fuentes oficiales consultadas (2026-11-08)

- https://console.groq.com/docs/models
- https://console.groq.com/docs/vision
- https://console.groq.com/docs/model/qwen/qwen3.8-27b
- https://console.groq.com/docs/tool-use/overview
- https://console.groq.com/docs/structured-outputs
- https://console.groq.com/docs/speech-to-text
- https://console.groq.com/docs/rate-limits
