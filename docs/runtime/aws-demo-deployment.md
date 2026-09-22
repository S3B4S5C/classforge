# Despliegue AWS para la demostración final (`aws-demo`)

Fecha de baseline: **19-09-2026**.

Este runbook despliega **la misma aplicación ClassForge** usada localmente, pero cambia los adaptadores de inferencia de texto/visión por Amazon Bedrock. El dominio UML, comandos, validadores, OpenCV, generación de aplicaciones, XMI, colaboración y frontend no cambian.

## 1. Topología cerrada

```text
Browser
  │ HTTPS / WSS :443
  ▼
Amazon CloudFront
  │ HTTP :80 (origin)
  ▼
EC2 m7i-flex.large · Ubuntu 24.04 · us-east-1
  ├─ Nginx :80
  │   ├─ /        -> Angular estático
  │   ├─ /api/*   -> Spring 127.0.0.1:8082
  │   └─ /ws*     -> Spring 127.0.0.1:8082 (WebSocket/STOMP)
  ├─ Spring Boot / Java 21 / profile aws-demo
  ├─ H2 file sobre EBS gp3
  └─ whisper.cpp 127.0.0.1:8093

Spring ── HTTPS/AWS SDK ──> Amazon Bedrock Converse
                            ├─ texto + tool use
                            └─ visión + tool use estructurado
```

El frontend conserva rutas relativas `/api` y `/ws`. **No se introducen CORS ni URLs absolutas de backend**: CloudFront/Nginx mantienen same-origin.

## 2. Providers por perfil

| Perfil | Texto | Voz | Visión |
|---|---|---|---|
| default / `demo` | llama.cpp `:8092` | whisper.cpp `:8093` | llama.cpp multimodal `:8094` |
| `aws-demo` | Amazon Bedrock | whisper.cpp `:8093` local a EC2 | Amazon Bedrock |

El provider AWS se activa únicamente con `aws-demo`. Los gateways llama.cpp siguen siendo el default. El SDK de AWS queda en el classpath del backend, pero cuando Bedrock está desactivado no se crea el cliente Bedrock ni se requieren credenciales AWS.

## 3. Modelo Bedrock

Los IDs se configuran exclusivamente por variables de entorno:

```text
CLASSFORGE_BEDROCK_TEXT_MODEL
CLASSFORGE_BEDROCK_VISION_MODEL
```

El default del perfil es:

```text
us.amazon.nova-2-lite-v1:0
```

Motivo: al 19-09-2026 Nova 2 Lite figura como modelo activo y soporta Converse/multimodalidad/tool use. **Nova Premier llegó a EOL el 14-09-2026**, por lo que no queda hardcodeado como modelo de examen. Si AWS ofrece otro modelo Bedrock más capaz y compatible con Converse + imágenes + tool use, se cambia el ID por variable de entorno, sin recompilar ClassForge.

Referencias operativas:

- Amazon Nova model lifecycle: https://docs.aws.amazon.com/bedrock/latest/userguide/model-lifecycle-legacy.html
- Converse API: https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference-call.html
- Tool use: https://docs.aws.amazon.com/bedrock/latest/userguide/tool-use.html

## 4. Cambios implementados para AWS

### Texto

```text
AssistantToolCallingGateway
  ├─ LlamaNativeToolCallingGateway   [provider=llama-cpp]
  └─ BedrockToolCallingGateway       [provider=bedrock]
```

`BedrockToolCallingGateway` publica el catálogo dinámico de tools de ClassForge como `ToolSpecification` de Bedrock y exige tool use. Los argumentos regresan al mismo `AssistantToolInvocation` que consume el planner existente.

### Visión

```text
VisionModelGateway
  ├─ LlamaCppVisionModelGateway
  └─ BedrockVisionModelGateway

VisionHybridModelGateway
  ├─ LlamaCppVisionHybridModelGateway
  ├─ BedrockVisionHybridModelGateway
  └─ UnconfiguredVisionHybridModelGateway
```

Bedrock recibe los mismos prompts y contratos de ClassForge. Cada etapa visual se modela como una tool forzada cuyo `input` debe satisfacer el contrato esperado. Para compatibilidad con Amazon Nova, el transporte Bedrock elimina únicamente metadata/strictness no admitida en la raíz del JSON Schema (`title`, `description`, `$schema`, `additionalProperties`) y envía `type/properties/required`; los schemas originales y la validación DTO/grounding de ClassForge permanecen intactos. Después siguen ejecutándose los validators/grounding/OpenCV existentes.

### Credenciales AWS

El SDK usa `DefaultCredentialsProvider`. En EC2 la autoridad debe ser el **IAM Role de la instancia**. No se guardan `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY` en archivos del proyecto.

## 5. Recursos AWS recomendados para el examen

```text
Region:       us-east-1
AMI:          Ubuntu Server 24.04 LTS x86-64
EC2:          m7i-flex.large (2 vCPU / 8 GiB)
EBS:          gp3 30 GiB
Entrada:      Amazon CloudFront
Dominio:      *.cloudfront.net (sin dominio propio)
```

Para el examen conviene crear un AWS Budget y revisar `Billing > Credits` diariamente. El Free Account Plan y los precios cambian con el tiempo: validar el estado de la cuenta antes de lanzar recursos.

## 6. IAM

Crear un role, por ejemplo `ClassForgeDemoRole`, con trust para EC2.

Adjuntar:

1. `AmazonSSMManagedInstanceCore` para administración sin SSH permanente.
2. La policy de `deploy/aws/iam/bedrock-policy.json`.

La policy del repo limita acciones a inferencia (`InvokeModel`, `InvokeModelWithResponseStream`) pero usa `Resource: "*"` para que inference profiles/model IDs intercambiables no rompan la demo. Después del examen puede restringirse a ARNs concretos.

## 7. Security Group

Objetivo final:

```text
Inbound
TCP 80  <- CloudFront origin-facing managed prefix list

Outbound
TCP 443 -> AWS/Internet
```

Spring `8082` y Whisper `8093` **no se exponen**.

Para la primera carga de artefactos puede habilitarse temporalmente SSH/22 solo desde la IP pública actual (`/32`) y eliminar la regla inmediatamente después. Para operación normal usar Session Manager.

## 8. Construir el bundle en la PC

Desde la raíz del repo en PowerShell:

```powershell
.\scripts\aws-build-deploy-bundle.ps1
```

El script ejecuta por defecto:

```text
backend tests
bootJar
frontend characterization tests
Angular production build
```

y produce:

```text
classforge-aws-deploy-bundle.zip
```

Para una reconstrucción rápida **solo después de haber corrido los gates**:

```powershell
.\scripts\aws-build-deploy-bundle.ps1 -SkipTests
```

Contenido del bundle:

```text
classforge.jar
frontend/
nginx/classforge.conf
systemd/classforge.service
systemd/classforge-whisper.service
classforge.env.example
install-origin.sh
install-whisper.sh
iam/bedrock-policy.json
```

Whisper/modelo no se empaquetan para no versionar binarios pesados.

## 9. Preparar EC2

Subir el ZIP. En una AMI mínima instalar `unzip` si aún no existe y descomprimirlo:

```bash
sudo apt-get update
sudo apt-get install -y unzip
unzip classforge-aws-deploy-bundle.zip -d classforge-aws
cd classforge-aws
sudo bash ./install-origin.sh
```

El instalador crea:

```text
/opt/classforge/app/classforge.jar
/opt/classforge/data/
/opt/classforge/whisper/
/var/www/classforge/
/etc/classforge/classforge.env
/etc/nginx/sites-available/classforge
/etc/systemd/system/classforge.service
/etc/systemd/system/classforge-whisper.service
```

## 10. Instalar Whisper en EC2

La ruta reproducible incluida en el bundle compila una versión fijada de `whisper.cpp` y descarga `ggml-base.bin`:

```bash
sudo bash ./install-whisper.sh
```

Baseline de este runbook: `whisper.cpp v1.9.4`. Para probar intencionalmente otra versión sin editar el script:

```bash
sudo WHISPER_CPP_REF=<tag-o-commit> bash ./install-whisper.sh
```

El resultado debe quedar exactamente en:

```text
/opt/classforge/whisper/whisper-server
/opt/classforge/whisper/models/ggml-base.bin
```

Si se prefiere reutilizar un binario/modelo ya validados, también pueden copiarse manualmente a esas dos rutas con owner `classforge:classforge`.

`classforge-whisper.service` ejecuta:

```text
whisper-server
-m /opt/classforge/whisper/models/ggml-base.bin
-l es
--host 127.0.0.1
--port 8093
-t 2
-nfa
-nt
```

## 11. Variables de entorno

Editar:

```bash
sudo nano /etc/classforge/classforge.env
sudo chmod 600 /etc/classforge/classforge.env
```

Generar un JWT secret largo, por ejemplo:

```bash
openssl rand -base64 48
```

Variables mínimas:

```text
AWS_REGION=us-east-1
CLASSFORGE_BEDROCK_TEXT_MODEL=us.amazon.nova-2-lite-v1:0
CLASSFORGE_BEDROCK_VISION_MODEL=us.amazon.nova-2-lite-v1:0
CLASSFORGE_JWT_SECRET=<secreto aleatorio>
CLASSFORGE_WEBSOCKET_ALLOWED_ORIGINS=https://<distribution>.cloudfront.net
```

No incluir credenciales AWS estáticas.

## 12. Arrancar servicios

```bash
sudo systemctl daemon-reload
sudo systemctl restart classforge-whisper
sudo systemctl restart classforge
sudo systemctl restart nginx

sudo systemctl status classforge-whisper classforge nginx --no-pager
```

Logs:

```bash
sudo journalctl -u classforge -f
sudo journalctl -u classforge-whisper -f
```

El backend debe indicar profile `aws-demo` y escuchar solo en `127.0.0.1:8082`.

## 13. Smoke local del origin

Desde EC2:

```bash
curl -I http://127.0.0.1/
curl -I http://127.0.0.1/api/projects
curl http://127.0.0.1:8093/
```

Una respuesta `401/403` de una ruta REST protegida es válida como prueba de proxy: confirma que Nginx llegó a Spring.

## 14. CloudFront

Crear una distribución con **un custom origin**:

```text
Origin domain: EC2 Public IPv4 DNS
Origin protocol: HTTP only
Origin port: 80
```

No detener/recrear la instancia durante los cuatro días de preparación si se usa su Public IPv4 DNS como origin.

### Default behavior `/*`

```text
Viewer protocol policy: Redirect HTTP to HTTPS
Allowed methods: GET, HEAD
Cache policy: CachingOptimized
Compress: Yes
```

### Behavior `/api/*`

```text
Viewer protocol: HTTPS
Allowed methods: GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE
Cache policy: CachingDisabled
Origin request policy: AllViewer
```

`AllViewer` permite que `Authorization` y los demás headers de la petición lleguen a Spring. No cachear respuestas REST autenticadas.

### Behavior `/ws*`

```text
Viewer protocol: HTTPS
Allowed methods: GET, HEAD
Cache policy: CachingDisabled
Origin request policy: AllViewer
```

CloudFront soporta WebSocket. `AllViewer` deja pasar los headers `Upgrade`, `Connection` y `Sec-WebSocket-*` requeridos por el handshake.

Después de conocer el dominio final, reemplazar el wildcard de `/etc/classforge/classforge.env`:

```text
CLASSFORGE_WEBSOCKET_ALLOWED_ORIGINS=https://dXXXXXXXX.cloudfront.net
```

y reiniciar Spring.

## 15. Validación externa

Abrir:

```text
https://dXXXXXXXX.cloudfront.net
```

Validar en este orden:

1. Angular carga por HTTPS.
2. Login/demo bootstrap funciona.
3. Crear/abrir proyecto.
4. DevTools Network: REST va al mismo host `/api/...`.
5. DevTools Network: `/ws` responde `101 Switching Protocols`.
6. Assistant textual ejecuta una mutación pequeña y devuelve preview.
7. Voz transcribe mediante Whisper de EC2.
8. Imagen simple y luego fixture denso pasan por Bedrock Vision.
9. Generación Spring/Angular/Flutter sigue funcionando.
10. Reiniciar `classforge` y comprobar que CU-27 se vuelve a sembrar de forma reproducible.

## 16. Health del Assistant en Bedrock

El health de ClassForge reporta texto/visión Bedrock como `CONFIGURED`, no como un ping remoto. Esto es deliberado: Bedrock no tiene un `/health` equivalente a llama.cpp y la autoridad real es IAM + model access de la llamada `Converse`.

Por ello el smoke obligatorio incluye **una inferencia textual y una visual reales**.

Whisper sí conserva su probe local HTTP.

## 17. Rollback

Cambiar a ejecución local no requiere revertir código. Detener el perfil AWS y arrancar el default/demo:

```powershell
cd backend
.\gradlew.bat bootRun --no-daemon --args="--spring.profiles.active=demo"
```

Los providers locales vuelven a ser:

```text
text.provider=llama-cpp
vision.provider=llama-cpp
```

## 18. Cierre después del examen

1. Descargar cualquier evidencia/log necesario.
2. Terminar EC2.
3. Eliminar EBS si no se elimina con la instancia.
4. Deshabilitar/eliminar distribución CloudFront si ya no se usa.
5. Eliminar role/policies específicos del demo si no se reutilizarán.
6. Verificar Billing/Cost Explorer/Credits.

La infraestructura AWS es un perfil de despliegue; **no es una nueva versión funcional de ClassForge**.
