# Preparación de despliegue Azure

## Objetivo y alcance

Preparar ClassForge integrado para Azure, con Groq, MongoDB y colaboración
WebSocket. Entregar infraestructura declarativa, JAR desplegable, scripts,
workflow manual y runbook. No crear recursos ni publicar en Azure en esta tarea.

## Alternativas y decisión de implementación

- App Service Linux Java 21, Angular dentro del JAR y MongoDB Atlas: un origen,
  un artefacto y sin registro de contenedores. Opción recomendada y asumida para
  la preparación mientras no se indique un recurso existente.
- Container Apps: necesita imagen/registro y reglas de réplicas; no aporta una
  ventaja necesaria para esta entrega.
- VM con nginx/Java: reutiliza el enfoque AWS, pero requiere mantener SO y TLS.

La elección es una propuesta técnica del implementador; no implica que el usuario
haya contratado App Service ni aceptado costos. Basic B1 es el valor inicial de
la plantilla y tiene costo. No se ejecutan acciones contra una suscripción.

## Arquitectura

Un App Service Java SE 21, una instancia, HTTPS obligatorio y Always On. El JAR
contiene Angular; las rutas conocidas de la SPA reenvían a index.html. API y
STOMP conservan JWT y controles de membresía. Los archivos/rutas públicos se
habilitan solo cuando `classforge.web.spa.enabled=true` (perfil azure).
El broker en memoria y presencia requieren una instancia; no configurar autoscale.

MongoDB Atlas usa una base explícita, usuario con readWrite e índices/transacciones
Mongo reales. No se sustituye por Cosmos DB sin una evaluación específica.
Registrar las IP de salida de App Service en Atlas antes de iniciar la app.
Groq permanece como único proveedor IA.

## Configuración y secretos

Perfil azure sin demo ni datos de examen. Puerto SERVER_PORT de App Service,
forwarded headers, origen WSS exacto. JWT, URI Mongo y clave Groq obligatorios en
configuración de despliegue; parámetros Bicep secure, sin outputs secretos.
Archivo local de parámetros ignorado; credenciales fuera del JAR/ZIP/Git.
Se puede reemplazar un valor de App Settings por una referencia Key Vault.
No cargar el .env del usuario en artefactos ni imprimir valores.

## Entregables y verificación

- Build reproducible Java21/Node24, frontend incluido en JAR y SHA256.
- Plantilla Bicep con App Service plan/site y App Settings; script de provisión
  opcional y otro de publicación JAR, ambos requieren ejecución deliberada.
- Workflow manual: build/tests/artefacto; publicación opcional con OIDC y environment.
- Tests de rutas públicas, API protegida, fallback y ausencia de fallback en modo
  normal; prueba HTTP del JAR final con Mongo aislado.
- Compilar Bicep sin crear recursos; parsear PowerShell y workflow; comprobar
  paquete sin credenciales y referencia remota de tiago tras publicar código.

## Límites

No se dispone de Azure CLI ni suscripción autenticada en esta sesión. Atlas y
sus credenciales no están configurados para la nube. La comprobación local no
equivale a una prueba Azure/Atlas ni valida capacidad bajo carga. Groq y Atlas
gratuitos tienen cuotas; B1 no es gratuito. La demo es un procedimiento separado
porque reinicia su fixture al arrancar.
