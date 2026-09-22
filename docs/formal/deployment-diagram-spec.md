# Especificación para los diagramas de despliegue de ClassForge

Fecha de corte: **19-09-2026**.

Este documento contiene la información que debe trasladarse a Enterprise Architect/diagrama UML de despliegue. No sustituye al diagrama gráfico: fija nodos, artefactos, protocolos, puertos y responsabilidades para evitar inconsistencias durante la documentación formal.

## 1. Mensaje arquitectónico que debe comunicar el diagrama

ClassForge tiene **un solo producto y una sola arquitectura de dominio** desplegados con tres capacidades físicas distintas:

```text
                         MISMO CLASSFORGE
                               │
             ┌─────────────────┼─────────────────┐
             ▼                 ▼                 ▼
          AWS Max           Home Full         Laptop Edge
          Bedrock          IA local full      IA local reducida
```

No representar tres aplicaciones diferentes. Representar tres **deployment configurations** de los mismos artefactos y puertos/adaptadores.

## 2. Diagrama DPL-01 — AWS Max

### 2.1 Nodo cliente

**Elemento UML:** `Device`

```text
<<device>>
Laptop / Browser
```

Contiene:

```text
<<executionEnvironment>> Web Browser
```

Conexiones salientes:

| Destino | Protocolo | Puerto | Uso |
|---|---|---:|---|
| Amazon CloudFront | HTTPS | 443 | SPA + REST |
| Amazon CloudFront | WSS | 443 | STOMP/WebSocket collaboration |

No dibujar una conexión directa Browser -> EC2/Spring/Bedrock.

### 2.2 Amazon CloudFront

**Elemento UML:** `Node` con stereotype `<<AWS managed service>>`.

```text
<<AWS managed service>>
Amazon CloudFront
```

Responsabilidades anotables:

```text
TLS público
CDN/static caching
REST reverse path
WebSocket pass-through
single public origin for browser
```

Hostname:

```text
dXXXXXXXX.cloudfront.net
```

Behaviors:

| Path | Cache | Métodos | Origin policy |
|---|---|---|---|
| `/*` | CachingOptimized | GET/HEAD | static |
| `/api/*` | CachingDisabled | all REST | AllViewer |
| `/ws*` | CachingDisabled | GET/HEAD | AllViewer/WebSocket |

Conexión:

```text
CloudFront --HTTP :80--> Nginx / EC2 origin
```

### 2.3 Nodo EC2

**Elemento UML:** `Device` o `Node` con stereotype `<<AWS EC2>>`.

```text
<<AWS EC2>>
ClassForge Server
m7i-flex.large
Ubuntu 24.04 LTS x86-64
us-east-1
2 vCPU · 8 GiB RAM
```

Adjuntar/relacionar:

```text
<<volume>> EBS gp3 · 30 GiB
<<IAM Role>> ClassForgeDemoRole
```

No dibujar llaves AWS dentro del nodo.

### 2.4 Nginx

Dentro de EC2:

```text
<<executionEnvironment>>
Nginx :80
```

Contiene el artefacto:

```text
<<artifact>>
ClassForge Angular SPA
/var/www/classforge
```

Rutas:

```text
/        -> Angular static
/api/*   -> Spring 127.0.0.1:8082
/ws*     -> Spring 127.0.0.1:8082
```

La relación Nginx -> Angular es deployment/serving, no una llamada de red separada.

### 2.5 Spring Boot

Dentro de EC2:

```text
<<executionEnvironment>>
Java 21 / Spring Boot
127.0.0.1:8082
profile=aws-demo
```

Artefacto:

```text
<<artifact>>
classforge.jar
```

Responsabilidades que pueden anotarse:

```text
REST API
JWT/Auth
ProjectDocument
Assistant orchestration
Vision pipeline + OpenCV
STOMP broker
XMI
application generation
```

No exponer `8082` fuera del nodo EC2.

### 2.6 Persistencia

Dentro/asociado a EC2:

```text
<<database>>
H2
/opt/classforge/data/classforge-aws.mv.db
```

Desplegado sobre:

```text
<<volume>>
EBS gp3
```

Conexión:

```text
Spring Boot --JDBC/file--> H2/EBS
```

No incluir RDS, Aurora o DynamoDB: no forman parte del baseline de examen.

### 2.7 Whisper

Dentro de EC2:

```text
<<executionEnvironment>>
whisper.cpp
127.0.0.1:8093
```

Artefactos:

```text
whisper-server
ggml-base.bin
```

Conexión:

```text
Spring/SpeechToTextGateway --HTTP localhost:8093--> whisper.cpp
```

No exponer `8093` a Internet.

### 2.8 Amazon Bedrock

Nodo externo:

```text
<<AWS managed service>>
Amazon Bedrock
Converse API
```

Modelo por configuración, no por código:

```text
CLASSFORGE_BEDROCK_TEXT_MODEL
CLASSFORGE_BEDROCK_VISION_MODEL
```

Baseline 19-09-2026:

```text
us.amazon.nova-2-lite-v1:0
```

La documentación puede aclarar que el despliegue AWS permite sustituirlo por un modelo Bedrock más capaz compatible con Converse + tool use + imágenes. Nova Premier no debe presentarse como default vigente: llegó a EOL el 14-09-2026.

Conexiones desde Spring:

```text
AssistantToolCallingGateway
  -> BedrockToolCallingGateway
  -> AWS SDK / Converse
  -> Bedrock

VisionModelGateway
  -> BedrockVisionModelGateway
  -> AWS SDK / Converse + image + tool schema
  -> Bedrock

VisionHybridModelGateway
  -> BedrockVisionHybridModelGateway
  -> AWS SDK / Converse + image + tool schema
  -> Bedrock
```

Comunicación UML del deployment:

```text
Spring Boot --HTTPS :443 / AWS SDK--> Amazon Bedrock
```

### 2.9 IAM

Elemento conceptual asociado a EC2:

```text
<<IAM Role>>
ClassForgeDemoRole
```

Permisos de aplicación:

```text
bedrock:InvokeModel
bedrock:InvokeModelWithResponseStream
```

Administración recomendada:

```text
AmazonSSMManagedInstanceCore
```

Credenciales:

```text
EC2 Instance Metadata / DefaultCredentialsProvider
```

Marcar explícitamente en la explicación: **no existen access keys hardcodeadas**.

### 2.10 Security boundary

Puertos visibles en DPL-01:

| Puerto | Bind/destino | Exposición |
|---:|---|---|
| 443 | CloudFront | público |
| 80 | Nginx EC2 origin | solo CloudFront origin-facing |
| 8082 | Spring | localhost EC2 |
| 8093 | Whisper | localhost EC2 |
| 443 | Bedrock | salida desde EC2 |

No dibujar `8092` ni `8094` en AWS: esos son runtimes llama.cpp de los despliegues locales.

## 3. Secuencia de red DPL-01

### REST

```text
Browser
  -> HTTPS CloudFront :443
  -> HTTP Nginx :80
  -> HTTP Spring :8082
  -> response por la misma cadena
```

### Realtime

```text
Browser
  -> WSS CloudFront :443
  -> WebSocket Nginx :80
  -> WebSocket/STOMP Spring :8082
```

### Assistant textual

```text
Browser
  -> /api/.../assistant/plan
  -> Spring
  -> AssistantToolCallingGateway
  -> BedrockToolCallingGateway
  -> Bedrock Converse :443
  -> AssistantToolInvocation
  -> semantic resolver / preview
```

### Voz

```text
Browser microphone
  -> HTTPS upload Spring
  -> SpeechToTextGateway
  -> whisper.cpp :8093
  -> transcript
  -> Bedrock textual planning
```

### Imagen

```text
Browser image
  -> HTTPS Spring
  -> image normalization
  -> BedrockVisionModelGateway
  -> Bedrock multimodal Converse
  -> VisionUmlProposal
  -> OpenCV/hybrid/grounding
  -> preview
  -> apply -> ProjectDocument
```

## 4. Diagrama DPL-02 — comparación de los tres entornos del examen

El segundo diagrama puede ser una vista comparativa de tres nodos/zonas de deployment.

### AWS Max

```text
Host application: EC2
Frontend: Angular/Nginx/CloudFront
Text: Bedrock
Speech: whisper.cpp EC2
Vision: Bedrock
Persistencia: H2/EBS
Entrada: HTTPS/WSS CloudFront
Objetivo: máxima capacidad disponible / provider gestionado
```

### Home Full

```text
Host application: PC de casa
Frontend: Angular local en PC de casa
Backend: Spring local en PC de casa
Text: llama.cpp/Qwen local validado
Speech: whisper.cpp local
Vision: llama.cpp/Qwen3-VL local validado
Entrada: IPv6 desde laptop universidad
Objetivo: baseline con el hardware/modelos usados en calibración y pruebas
```

La representación formal debe indicar que la conexión remota cruza Internet mediante IPv6 y que los runtimes IA permanecen privados en la PC de casa.

### Laptop Edge

```text
Host application: ThinkPad del examen
Frontend: localhost
Backend: localhost
Text: llama.cpp/modelo reducido configurado
Speech: whisper.cpp local
Vision: llama.cpp/modelo reducido configurado
Entrada: localhost
Objetivo: demostrar degradación/capacidad bajo límite físico
```

No documentar aquí parámetros concretos de modelos reducidos: pertenecen al runbook de laptop, no al diseño del deployment.

## 5. Relación arquitectónica entre los tres entornos

El diagrama comparativo debe destacar los **ports** estables:

```text
AssistantToolCallingGateway
VisionModelGateway
VisionHybridModelGateway
SpeechToTextGateway
```

Implementaciones:

```text
AWS:
  BedrockToolCallingGateway
  BedrockVisionModelGateway
  BedrockVisionHybridModelGateway
  WhisperCppSpeechToTextGateway

Casa/Laptop:
  LlamaNativeToolCallingGateway
  LlamaCppVisionModelGateway
  LlamaCppVisionHybridModelGateway
  WhisperCppSpeechToTextGateway
```

Esto permite defender que la variación de infraestructura no cambia el dominio ni los CU.

## 6. Artefactos que deben aparecer en el diagrama formal

DPL-01 debería incluir como mínimo:

```text
<<artifact>> Angular SPA
<<artifact>> classforge.jar
<<artifact>> classforge-aws.mv.db
<<artifact>> whisper-server
<<artifact>> ggml-base.bin
```

No es necesario representar `.env`, Nginx config o unit files como artefactos del diagrama principal; pueden aparecer en una nota o anexo de deployment.

## 7. Estereotipos sugeridos para Enterprise Architect

```text
<<device>>                laptop/EC2 físico-virtual
<<executionEnvironment>>  Browser, Nginx, JVM/Spring, whisper.cpp
<<artifact>>              Angular SPA, JAR, model/binario
<<database>>              H2
<<volume>>                EBS gp3
<<AWS managed service>>   CloudFront, Bedrock
<<IAM Role>>              ClassForgeDemoRole
```

Si EA no permite stereotypes personalizados de forma cómoda, usar `Node` + notas; mantener los nombres y protocolos de este documento como autoridad.

## 8. Vista textual definitiva DPL-01

```text
┌──────────────────────────┐
│ <<device>>               │
│ Laptop / Browser         │
└────────────┬─────────────┘
             │ HTTPS / WSS :443
             ▼
┌──────────────────────────┐
│ <<AWS managed service>>  │
│ Amazon CloudFront        │
│ TLS + CDN + WebSocket    │
└────────────┬─────────────┘
             │ HTTP :80
             ▼
┌───────────────────────────────────────────────────────┐
│ <<AWS EC2>> ClassForge Server                        │
│ m7i-flex.large · Ubuntu 24.04 · 2 vCPU · 8 GiB      │
│                                                       │
│  ┌──────────────────────┐                            │
│  │ Nginx :80            │                            │
│  │ /     -> Angular     │                            │
│  │ /api  -> :8082       │                            │
│  │ /ws   -> :8082       │                            │
│  └──────────┬───────────┘                            │
│             │                                         │
│  ┌──────────▼───────────┐       ┌──────────────────┐ │
│  │ Spring Boot :8082    │──────>│ H2 / EBS gp3    │ │
│  │ Java 21              │       └──────────────────┘ │
│  │ profile aws-demo     │                            │
│  └──────┬────────┬──────┘                            │
│         │        │                                    │
│         │        ▼                                    │
│         │  ┌──────────────────┐                      │
│         │  │ whisper.cpp      │                      │
│         │  │ :8093            │                      │
│         │  │ ggml-base.bin    │                      │
│         │  └──────────────────┘                      │
└─────────┼─────────────────────────────────────────────┘
          │ HTTPS :443 / AWS SDK
          ▼
┌──────────────────────────┐
│ <<AWS managed service>>  │
│ Amazon Bedrock           │
│ Converse API             │
│ Text/tool use + Vision   │
└──────────────────────────┘
```

## 9. Restricciones para no falsear el diagrama

No añadir al baseline del examen:

```text
Firebase
API Gateway
RDS/Aurora
DynamoDB
Lambda
ECS/EKS
S3 como hosting del frontend
Kafka/RabbitMQ
ElastiCache
VPN
llama.cpp en EC2
VLM local en EC2
```

Pueden ser alternativas futuras, pero **no son el deployment decidido**.

## 10. Fuente operacional

Para configurar el despliegue representado aquí usar `docs/runtime/aws-demo-deployment.md` y los artefactos de `deploy/aws/`.
