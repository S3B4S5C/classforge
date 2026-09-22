# Mapa de código para lectura y defensa

**Corte:** 14 de septiembre de 2026.  
**Regla:** este mapa explica responsabilidades; no introduce funcionalidades nuevas.

## 1. Recorrido mental recomendado

```text
Angular UI
  -> UmlCommand / Assistant plan / XMI import
  -> backend autoritativo
  -> ProjectDocument
  -> validación + revisión + persistencia
  -> colaboración/broadcast

ProjectDocument
  -> modelo relacional
  -> Spring/API/OpenAPI/Postman/Domain Manifest
  -> Angular + Flutter + Assistant generados
```

La regla transversal más importante es que **`ProjectDocument` es la fuente de verdad**. JointJS, IA, XMI y los generadores son entradas, proyecciones o transformaciones alrededor de ese modelo.

## 2. Backend por responsabilidad

| Paquete | Responsabilidad | Entrada principal | Salida/efecto |
|---|---|---|---|
| `com.classforge.auth` | identidad local, BCrypt y JWT | credenciales | identidad autenticada |
| `com.classforge.project` | proyecto, documento, persistencia y permisos | REST / servicios | `Project` + `ProjectDocument` |
| `com.classforge.project.validation` | invariantes del UML canónico | `ProjectDocument` | diagnósticos |
| `com.classforge.collaboration` | operaciones STOMP autoritativas y presencia | `UmlCommand` + `baseRevision` | revisión persistida + broadcast |
| `com.classforge.assistant` | texto/voz -> plan semántico UML | texto / WAV | preview `BATCH` |
| `com.classforge.assistant.tools` | grounding y resolución de herramientas | native tool calls | acciones semánticas |
| `com.classforge.assistant.vision` | imagen -> propuesta UML | imagen | propuesta/preview; nunca persistencia directa |
| `com.classforge.integration.xmi` | interoperabilidad Enterprise Architect | XMI / `ProjectDocument` | import preview / XMI determinista |
| `com.classforge.generation.relational` | UML -> IR relacional | `ProjectDocument` | modelo relacional efímero |
| `com.classforge.generation.spring` | aplicación generada completa | IR + opciones | ZIP reproducible |

## 3. Pipeline de generación

```text
SpringBootGenerationService
  -> preparación/fallback explícito de PK
  -> SpringGenerationPlanner
  -> SpringApiGenerationPlanner
  -> DomainManifestPlanner
  -> SpringProjectRenderer
       -> backend Spring/JPA
       -> OpenAPI/Postman
       -> Domain Manifest
       -> AngularFrontendRenderer (fachada)
       -> FlutterMobileRenderer (fachada)
       -> GeneratedAssistantRenderer (fachada)
  -> GeneratedProjectValidator (fachada)
  -> DeterministicZipWriter
```

Las clases `*Renderer` y `GeneratedProjectValidator` son fachadas deliberadas. Las responsabilidades de detalle viven en colaboradores del mismo paquete para que el recorrido del pipeline sea legible sin alterar el contrato externo.

## 4. Frontend principal

| Ruta | Responsabilidad |
|---|---|
| `auth/` | sesión de ClassForge |
| `projects/model/` | tipos del proyecto/documento |
| `projects/commands/` | Command Bus, inversas y ejecución UML |
| `projects/state/` | estado del workspace y coordinación |
| `projects/collaboration/` | STOMP, presencia y protocolo |
| `projects/diagram/` | geometría/proyección JointJS |
| `projects/assistant/` | UI y API del Assistant |
| `projects/xmi/` | import/export XMI |
| `projects/generation/` | diálogo/API de exportación Spring |

### Workspace

`ProjectWorkspaceStore` permanece como fachada de estado consumida por la página. La lógica pura extraída (`WorkspaceDocumentSession`, `WorkspaceCollaborationState`) encapsula historial/documento y seguimiento de revisiones pendientes para que las reglas puedan leerse y probarse sin Angular.

### Comandos UML

```text
UmlCommandBus
  -> UmlCommandInverter
  -> UmlCommandExecutor (dispatcher)
       -> class execution
       -> attribute execution
       -> relationship execution
       -> layout execution
```

El dispatcher conserva la API pública; las operaciones puras están separadas por intención.

## 5. Cómo estudiar un flujo

### Guardar/editar
1. `project-workspace.page.ts`.
2. `ProjectWorkspaceStore`.
3. `UmlCommandBus` y `UmlCommandExecutor`.
4. API/collaboration service.
5. backend `ProjectCommandExecutor`.
6. `ProjectDocumentValidator`.

### Assistant
1. frontend assistant dialog/service.
2. backend assistant controller/service.
3. `UmlToolCallResolver` / semantic resolver.
4. preview `BATCH`.
5. colaboración normal para Apply.

### Imagen
Leer `docs/architecture/vision-input-pipeline.md` antes del código. Los algoritmos OpenCV/VLM se mantienen deliberadamente sin refactor funcional por ser calibrados y de alto riesgo.

### Generación
1. `SpringBootGenerationService`.
2. planning/model.
3. renderers por plataforma.
4. `GeneratedProjectValidator`.
5. deterministic ZIP.

## 6. Functional freeze

En el refactor de legibilidad se permiten únicamente extracción, movimiento, nombres internos, comentarios y tests de caracterización. Permanecen congelados endpoints, JSON, operationIds, Domain Manifest, XMI, comandos UML, validaciones, generated output, UX, esquema persistente, heurísticas Vision, prompts y parámetros IA.

## 7. Adaptadores de despliegue AWS

El perfil `aws-demo` no cambia el dominio. Sustituye únicamente adapters de infraestructura:

```text
AssistantToolCallingGateway
  ├─ LlamaNativeToolCallingGateway      # default/demo local
  └─ BedrockToolCallingGateway          # aws-demo

VisionModelGateway
  ├─ LlamaCppVisionModelGateway         # default/demo local
  └─ BedrockVisionModelGateway          # aws-demo

VisionHybridModelGateway
  ├─ LlamaCppVisionHybridModelGateway   # default/demo local
  └─ BedrockVisionHybridModelGateway    # aws-demo
```

Soporte común Bedrock: `com.classforge.assistant.bedrock` (`BedrockRuntimeConfiguration`, `BedrockConverseSupport`, `BedrockDocumentCodec`). El provider se selecciona por configuración, no mediante bifurcaciones dentro del dominio.

Runbook: `docs/runtime/aws-demo-deployment.md`. Autoridad para el diagrama DPL-01: `docs/formal/deployment-diagram-spec.md`.
