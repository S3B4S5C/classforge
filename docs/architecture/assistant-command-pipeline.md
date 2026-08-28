# ClassForge — Assistant Command Pipeline

CU08-001 implementa texto → AssistantSemanticPlan → BATCH UmlCommand.

La IA nunca modifica ProjectDocument directamente.

```text
chat
→ POST /assistant/plan
→ llama.cpp JSON Schema
→ AssistantSemanticPlan
→ resolver Java por nombres
→ BATCH
→ preview
→ Aplicar
→ Command Bus / STOMP
→ ProjectCommandExecutor
→ ProjectDocumentValidator
```

## BATCH

Una intención con varios cambios viaja como una sola operación.

Consecuencias:

- un solo lock;
- una sola revisión;
- una sola persistencia;
- un solo broadcast;
- un solo Undo/Redo;
- sin resultados parciales.

Los BATCH anidados se rechazan.

## LLM

Endpoint local por defecto:

```text
http://127.0.0.1:8092/v1/chat/completions
```

Propiedades:

```text
classforge.assistant.llama-url
classforge.assistant.llama-model
```

El gateway usa `response_format = json_schema`.

El LLM devuelve nombres y semántica. Java genera UUIDs y valida.

## UI

El chat está debajo del Inspector.

CU08-001 permite escribir, previsualizar, descartar y aplicar.

El botón Hablar ya forma parte de la UI; whisper.cpp se conecta en CU08-002.

El endpoint de planificación no modifica revisión ni persistencia.

<!-- CU08-001-FIX-002-PLAN-COMPLETENESS -->
## Hardening CU08-001 — completitud del plan

Durante la prueba con un modelo local pequeño se observó que la frase:

```text
Crea una clase Veterinario con id UUID y nombre String
```

podía ser resumida correctamente pero producir solamente `CREATE_CLASS`.

Se incorporó `AssistantPlanCompletenessGuard`.

Para instrucciones de creación que contienen atributos con tipos UML explícitos después de `con/with`, el backend compara la cantidad de tipos pedidos con las acciones `ADD_ATTRIBUTE`.

Si faltan acciones:

1. el primer plan no se entrega a Angular;
2. llama.cpp recibe una instrucción de reparación;
3. se genera nuevamente el plan completo;
4. el plan reparado vuelve a validarse;
5. si continúa incompleto se rechaza sin modificar el modelo.

La regla crítica permanece:

```text
LLM propone
Java comprueba completitud
Java resuelve
Command Bus aplica
```

El guard no crea atributos por su cuenta y no modifica `ProjectDocument`.

<!-- CU08-001-FIX-003-RICH-INTENT -->
## CU08-001 fix-003 — AssistantIntent rico

El completeness guard sintáctico introducido temporalmente en fix-002 se retira.

La razón es arquitectónica: lenguaje natural no puede depender de palabras concretas como `con`, `with`, `ponle` o `que tenga`.

El contrato del LLM cambia de comandos casi internos a intenciones semánticas.

Ejemplo:

```text
"Crea Veterinario y ponle id UUID y nombre"
```

se interpreta como:

```text
CREATE_CLASS Veterinario
attributes:
  id UUID      EXPLICIT
  nombre STRING DEFAULT
```

Java expande posteriormente esa intención:

```text
BATCH
  CREATE_CLASS Veterinario
  ADD_ATTRIBUTE id UUID
  ADD_ATTRIBUTE nombre STRING
```

### Responsabilidades

```text
LLM
  comprende lenguaje y produce intención

AssistantPlanNormalizer
  aplica defaults del perfil ClassForge

UmlAssistantCommandResolver
  resuelve nombres, UUIDs y expande a comandos

Command Bus / ProjectCommandExecutor
  modifica el modelo validado
```

### Contexto entregado al LLM

El prompt incluye:

- clases existentes;
- atributos con tipo, visibilidad, nullable e identifier;
- relaciones existentes;
- reglas del perfil ClassForge;
- tipos admitidos;
- convenciones de dirección de relaciones;
- ejemplos few-shot con paráfrasis naturales.

No se envían layout, JointJS, historial, presencia ni UUIDs como responsabilidad del LLM.

### Defaults

Atributo sin tipo suficiente:

```text
STRING
typeSource = DEFAULT
```

Si el modelo infiere un tipo por semántica:

```text
typeSource = INFERRED
```

Si el usuario declara el tipo:

```text
typeSource = EXPLICIT
```

Un atributo `id` sigue la convención ClassForge:

```text
identifier = true
nullable = false
```

### MCP

CU08-001 no utiliza MCP.

Spring ya posee el modelo canónico, la validación y el Command Bus, por lo que introducir un servidor MCP en esta etapa agregaría una capa sin resolver una necesidad arquitectónica.

llama.cpp se mantiene como runtime local OpenAI-compatible por HTTP.

<!-- CU08-001-FIX-004-COMPACT-LLAMA -->
## CU08-001 fix-004 — salida compacta y presupuesto temporal local

La prueba con Gemma 3 4B Q4_K_M en CPU mostró una solicitud real con aproximadamente 996 tokens de prompt y generación cercana a 7.5 tokens/s.

El timeout previo de 45 segundos cancelaba una inferencia todavía activa en llama.cpp.

También se detectó que el JSON Schema estricto obligaba a producir todas las propiedades de cada intención, aun cuando la mayoría eran `null`.

### Ajustes

```text
request timeout: 45 s → 90 s
max completion:   384 tokens
stream:           false
```

El schema deja de exigir padding con `null`.

Una creación puede producir de forma compacta:

```json
{
  "summary": "Crear Veterinario",
  "actions": [
    {
      "type": "CREATE_CLASS",
      "className": "Veterinario",
      "attributes": [
        {
          "name": "id",
          "dataType": "UUID",
          "identifier": true,
          "typeSource": "EXPLICIT"
        },
        {
          "name": "nombre",
          "dataType": "STRING",
          "typeSource": "DEFAULT"
        }
      ]
    }
  ]
}
```

en lugar de serializar propiedades no aplicables como `newName`, `relationshipType`, multiplicidades y otros campos con valor `null`.

Si Java agota el presupuesto temporal, el usuario recibe un error específico de timeout.

Si llama.cpp termina por límite de tokens (`finish_reason=length`), ClassForge lo reporta como truncamiento y no intenta aplicar un JSON incompleto.

Ninguno de estos errores modifica `ProjectDocument`.

### Runtime de texto recomendado

Para CU08 texto no se necesita el proyector multimodal.

El runtime puede iniciarse con `--no-mmproj` para evitar cargar el projector de visión cuando se usa `-hf`.

La visión se habilitará explícitamente cuando se implemente el caso de uso correspondiente.

<!-- CU08-001-FIX-005-GROUNDING-SAFETY -->
## CU08-001 fix-005 — grounding y safety del plan

Una prueba real mostró que una solicitud de relación podía producir un preview inválido por acciones adicionales no solicitadas por el usuario.

El modelo persistido era válido; las violaciones aparecían únicamente después de ejecutar temporalmente el plan generado por IA.

Se agrega `AssistantPlanGroundingFilter` antes del normalizador y del resolver.

### Regla de grounding

Toda entidad que el LLM pretenda crear, modificar o eliminar debe estar respaldada por el texto del usuario.

Ejemplo:

```text
Usuario:
Conecta Animal con Veterinario
```

Plan propuesto por LLM:

```text
CREATE_RELATIONSHIP Animal -> Veterinario
UPDATE_ATTRIBUTE Animal.edad -> CUSTOM
```

Resultado:

```text
CREATE_RELATIONSHIP  -> grounded, se conserva
UPDATE_ATTRIBUTE     -> "edad" no aparece, se descarta
```

El filtro no interpreta verbos del español mediante una lista de regex. Verifica el anclaje de nombres de clases y atributos y admite variaciones comunes de plural.

### Safety de CUSTOM

```text
CUSTOM + customTypeName
    -> permitido

CUSTOM inferido/default sin customTypeName
    -> STRING / DEFAULT

CUSTOM explicito sin customTypeName
    -> plan rechazado

UPDATE_ATTRIBUTE a CUSTOM sin customTypeName
    -> plan rechazado
```

El LLM no puede introducir un atributo UML estructuralmente inválido por inferencia.

### Orden actualizado

```text
texto
-> llama.cpp
-> AssistantSemanticPlan
-> grounding filter
-> plan normalizer / safety
-> UmlAssistantCommandResolver
-> BATCH
-> preview
-> ProjectDocumentValidator
-> confirmacion
```

El frontend conserva y muestra `code`, `message` y `field` cuando `ProjectDocumentValidator` rechaza el preview, en vez de ocultar las violaciones tras el mensaje genérico.

No se incorpora MCP.

<!-- CU08-001-FIX-006-CONTEXT-GROUNDING -->
## CU08-001 fix-006 — grounding contextual contra el modelo actual

Una prueba de relaciones mostró que el LLM podía emitir `CREATE_CLASS` para clases que ya existían, porque sus nombres sí estaban mencionados por el usuario.

Ejemplo real:

```text
Crea una asociación uno a uno entre Veterinario y Animal,
y una composición desde Animal hacia Mascota.
```

El modelo podía proponer:

```text
CREATE_CLASS Animal
CREATE_CLASS Mascota
CREATE_RELATIONSHIP Veterinario -> Animal
CREATE_RELATIONSHIP Animal -> Mascota
```

El grounding textual de fix-005 no podía distinguir que `Animal` y `Mascota` eran referencias a clases existentes.

### Solución

`AssistantPlanGroundingFilter` recibe ahora también el `ProjectDocument`.

Inicializa un conjunto de clases ya conocidas a partir del modelo canónico y procesa las acciones en orden.

```text
CREATE_CLASS nombre ya existente
    -> se descarta

CREATE_CLASS nombre nuevo y mencionado
    -> se conserva y pasa a ser conocido

CREATE_RELATIONSHIP entre clases mencionadas
    -> se conserva
```

Esto permite también una instrucción legítima de varias etapas:

```text
Crea Cita y relaciona Cita con Animal
```

porque `Cita` se incorpora al conjunto de nombres conocidos después de aceptar su `CREATE_CLASS`.

El prompt recuerda explícitamente al modelo que las clases presentes en `CONTEXTO CLASSFORGE` ya existen y no deben recrearse.

El filtro sigue siendo una capa de defensa; Java no confía en que el LLM respete el prompt.

<!-- CU08-001-OPT-001-FOCUSED-PROMPT -->
## CU08-001 opt-001 — contexto focal y presupuesto compacto

La primera versión funcional del Assistant enviaba al runtime local el detalle completo de todas las clases, atributos, relaciones y reglas del perfil en cada petición.

En un modelo local 4B esto aumenta tanto el tiempo de prompt como la generación.

La optimización mantiene las reglas de seguridad y cambia únicamente la información que Spring envía al LLM.

### Selección de contexto

Spring construye ahora:

```text
classCount
existingClassNames
focusedClasses
relationships relevantes
```

`focusedClasses` contiene clases cuyo nombre aparece en la petición y sus vecinos directos mediante relaciones existentes, con un máximo de 8 clases detalladas.

El catálogo de nombres se limita a 32 entradas y se utiliza principalmente para evitar recrear clases existentes.

Si la petición crea una clase nueva y no menciona clases existentes, Spring no envía los atributos de todo el modelo.

Para una petición sobre clases existentes, el contexto contiene solo esas clases y como máximo sus vecinos directos.

### Prompt estable

Las reglas ClassForge permanecen en el system prompt, que se mantiene estable entre peticiones.

Se eliminó la duplicación de reglas dentro del JSON de contexto y se redujo el número de ejemplos few-shot.

Esto favorece también la reutilización de prefijos del runtime sin acoplar ClassForge a una opción concreta de llama.cpp.

### Presupuesto de salida

```text
max completion tokens: 384 -> 256
request timeout:        90 s sin cambios
```

El schema continúa usando campos opcionales y salida compacta.

Las configuraciones de llama.cpp (`threads`, GPU layers, parallel, speculative decoding) no forman parte de esta optimización y se medirán sobre el hardware definitivo de presentación.

<!-- CU08-001-FIX-007-ASSOCIATION-REVERSE-LOOKUP -->
## CU08-001 fix-007 — resolución bidireccional segura de asociaciones

Una asociación creada por lenguaje natural puede quedar almacenada como `A -> B` aunque una petición posterior mencione `B -> A`.

Para `ASSOCIATION`, esa orientación de almacenamiento no debe impedir localizar la relación.

La búsqueda para UPDATE/DELETE sigue ahora este orden:

```text
1. buscar source -> target
2. si no existe, buscar target -> source
3. si la coincidencia inversa es ASSOCIATION, aceptarla
4. si es AGGREGATION, COMPOSITION o GENERALIZATION, exigir su dirección UML real
5. múltiples coincidencias -> ambigüedad
```

Al actualizar una asociación localizada en sentido inverso, se conserva su `relationshipId`, pero el nuevo `UmlRelationship` usa `sourceClassName` y `targetClassName` de la intención solicitada. Esto permite convertir una asociación incidentalmente almacenada como `Animal -> Veterinario` en una composición explícita `Veterinario -> Animal`.

No se hace búsqueda inversa silenciosa para relaciones UML donde la dirección ya tiene significado semántico.

<!-- CU08-001-FIX-008-ASSISTANT-WORKBENCH-POSITION -->
## CU08-001 fix-008 — ubicación del Asistente en el workbench

El Asistente UML se considera una herramienta operativa del diagramador.

En escritorio queda a la derecha del canvas e inmediatamente debajo del Inspector dentro de `diagram-sidecar`. Las tarjetas de Estado del documento y arquitectura permanecen en el bloque informativo posterior.

En tablet y móvil el sidecar deja de ser sticky y vuelve al flujo responsivo.

No cambia ninguna API, Command Bus, ProjectDocument ni comportamiento del planner.

<!-- CU08-002-LOCAL-VOICE-WHISPER -->
## CU08-002 — voz local con whisper.cpp

CU08-002 activa la entrada de voz real del Asistente UML.

### Pipeline

```text
Microfono navegador
-> BrowserWavRecorderService
-> WAV PCM mono 16 kHz
-> POST /api/projects/{id}/assistant/voice
-> SpeechToTextGateway
-> whisper-server /inference
-> transcript
-> AssistantPlanService source=VOICE
-> LlamaLanguageModelGateway
-> grounding contextual
-> normalizacion/safety
-> UmlAssistantCommandResolver
-> BATCH
-> preview
-> ProjectDocumentValidator
-> confirmacion Aplicar
```

Whisper solo produce texto. No conoce `ProjectDocument`, no genera UUIDs y no ejecuta comandos.

Gemma continua produciendo `AssistantSemanticPlan`, nunca modifica directamente el modelo canonico.

La mutacion sigue ocurriendo exclusivamente cuando el usuario pulsa `Aplicar`, por el mismo Command Bus/BATCH de CU08-001.

### Audio

El navegador genera WAV PCM directamente, por lo que el flujo normal no necesita ffmpeg ni `--convert` en whisper-server.

Perfil de captura:

```text
mono
16 kHz
PCM 16 bit
maximo 20 segundos por grabacion
maximo backend 4 MB
```

La captura usa Web Audio API y solicita cancelacion de eco, reduccion de ruido y control automatico de ganancia cuando el navegador lo soporta.

### Runtime

Defaults Spring:

```text
classforge.assistant.whisper-url=http://127.0.0.1:8093
classforge.assistant.whisper-language=es
```

Nota histórica de CU08-002: en ese corte CU08-003 aún estaba pendiente. El cierre se encuentra documentado inmediatamente después en la sección CU08-003.

<!-- CU08-002-FIX-001-ASSISTANT-DIAGNOSTICS -->
## CU08-002 fix-001 — diagnostico estructurado del planner

Los errores del Assistant conservan ahora el contexto seguro del pipeline:

```text
stage
source
transcript
attemptedPlan
```

Etapas: STT, LLM, GROUNDING, NORMALIZATION, RESOLUTION y PREVIEW.

Si Gemma ya produjo un `AssistantSemanticPlan` y una defensa posterior lo rechaza, la respuesta HTTP incluye el plan original bajo `attemptedPlan`. El frontend muestra un resumen de las acciones intentadas.

No se exponen system prompts, chain-of-thought ni razonamiento interno del modelo.

<!-- CU08-003-RUNTIME-COLLAB-HARDENING-CLOSE -->
## CU08-003 — health, colaboración y cierre del Asistente UML

CU08-003 cierra el caso de uso CU08 sobre los incrementos anteriores.

### Runtime health

Spring consulta los endpoints oficiales `/health` de los dos runtimes locales y publica:

```text
GET /api/projects/{projectId}/assistant/health
```

La respuesta diferencia:

```text
readyForText  = llama listo
readyForVoice = llama listo AND whisper listo
```

El panel muestra ambos estados y permite refrescarlos manualmente. No se hace polling continuo para no competir con una inferencia activa.

### Hardening colaborativo

Antes de pedir o aplicar un plan, el frontend rechaza temporalmente la acción cuando:

```text
hay ProjectOperation pendientes
collaboration = connecting
collaboration = syncing
collaboration = resyncing
collaboration = conflict
hay draft local dirty fuera de realtime
```

El `baseRevision` retornado por el preview se comprueba:

1. al recibir la respuesta del planner;
2. dentro de `ProjectWorkspaceStore.applyAssistantCommand`;
3. inmediatamente antes del Command Bus.

Por tanto una operación remota que cambie el proyecto durante la inferencia invalida el preview en lugar de intentar aplicarlo sobre otra revisión.

El Assistant continúa despachando un único `BATCH`. El servidor ejecuta ese BATCH atómicamente y el proyecto incrementa una sola revisión.

### Cierre CU08

```text
CU08-001
texto -> rich semantic intent -> deterministic resolver -> BATCH -> preview

CU08-002
microfono -> WAV 16 kHz -> whisper.cpp -> transcript -> mismo planner

CU08-003
runtime health + diagnostics + stale-plan guard + collaboration hardening + tests
```

La IA nunca modifica `ProjectDocument` directamente.

No se introduce MCP, CRDT, Yjs, Kafka, Redis ni un segundo canal de mutación.
