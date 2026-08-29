# Estado actual PUDS

**Fecha de corte:** 29 de agosto de 2026.

```text
Fase PUDS: Elaboración
Ciclo 1: CERRADO
Ciclo 2: ABIERTO
CU-31: CERRADO
```

## Objetivo alcanzado del Ciclo 1

Estabilizar la arquitectura ejecutable de ClassForge mediante un incremento que demuestre que el mismo modelo UML canónico puede ser manipulado desde UI manual, colaboración en tiempo real y lenguaje natural/voz sin introducir rutas alternativas de mutación.

## Casos cerrados en el Ciclo 1

| Caso | Estado | Resultado principal |
|---|---|---|
| CU-01 Crear proyecto | CERRADO | proyecto persistente con UUID y owner |
| CU-02 Abrir/guardar | CERRADO | `ProjectDocument`, revisión y conflicto |
| CU-03 Modelado manual | CERRADO | clases, atributos, relaciones, multiplicidades, JointJS |
| CU-04 Validar UML | CERRADO | validación explícita y diagnósticos |
| CU-05 Undo/Redo | CERRADO | Command Bus e historial reversible |
| CU-06 Colaboración realtime | CERRADO | STOMP, servidor autoritativo y revisión |
| CU-07 Presencia | CERRADO | presencia efímera, selección y cursor |
| CU-08 Voz/lenguaje natural a UML | CERRADO | texto/voz -> plan -> BATCH -> preview -> aplicar |
| CU-28 Registrar cuenta | CERRADO | identidad local, BCrypt y JWT |
| CU-29 Iniciar sesión | CERRADO | autenticación stateless |
| CU-30 Proyectos propios | CERRADO | ownership persistente; posteriormente ampliado por CU-31 |

## Ciclo 2 — CU-31

C2-cu31-001 está completado:

- `ProjectMembership` EDITOR persistente;
- `ProjectAccessRole` OWNER/EDITOR/NONE;
- política central de acceso;
- REST y biblioteca con proyectos propios + compartidos;
- renombrar reservado al OWNER.

C2-cu31-002 está completado:

- `ProjectInvitation` persistente por correo normalizado;
- invitaciones internas sin SMTP/Internet;
- pending/accept/decline/cancel;
- aceptación transaccional que crea membership EDITOR;
- bandeja de invitaciones en biblioteca;
- dialog de Colaboradores en workspace;
- owner/editors visibles según permisos;
- aceptación sin modificar revisión UML.

C2-cu31-003 está completado:

- STOMP usa directamente la política central: SUBSCRIBE exige lectura y SEND exige edición;
- OWNER y EDITOR reales intercambian operaciones entre cuentas distintas; NONE es rechazado;
- presencia revalida acceso también dentro del controller y se prueba OWNER + EDITOR;
- Assistant texto/voz queda membership-aware y NONE se rechaza antes de invocar LLM/STT;
- invitaciones serializan mutaciones por proyecto para cerrar carreras de invite/accept;
- CU-31 queda formalmente CERRADO.

## Arquitectura validada

```text
adaptador UI / realtime / Assistant
              |
              v
       UmlCommand / BATCH
              |
              v
        Command Bus
              |
              v
     ProjectDocument
       /          \
  UmlModel    DiagramLayout
```

Además:

- backend autoritativo;
- revisión exacta para operaciones colaborativas;
- presencia fuera de persistencia;
- LLM sin autoridad de escritura;
- preview antes de aplicar IA;
- grounding y validación determinista;
- membership separada de ownership;
- invitación separada del permiso efectivo.

## Persistencia

- Spring Data JPA/Hibernate;
- H2 archivo en desarrollo;
- H2 memoria en tests;
- PostgreSQL permanece planificado;
- `project_memberships` y `project_invitations` son incorporaciones aditivas.

## Limitaciones vigentes

1. No existe eliminación de membership activa ni revocación inmediata de una sesión STOMP ya conectada; sigue fuera del alcance de CU-31.
2. CU-09 — Imagen → UML — todavía no está implementado.
3. XMI/Enterprise Architect no está implementado.
4. Modelo relacional y generadores no están implementados.
5. La aplicación generada y su asistente de voz aún no existen.
6. Auditoría histórica completa CU-26 permanece pendiente.
7. `docs/uml/` tiene catálogo preparado, pero los diagramas académicos aún deben elaborarse.

## Siguiente paso

```text
CU-09 — Imagen -> UML
  -> entrada visual
  -> convergencia en ProjectDocument/UmlModel
  -> sin ruta alternativa de mutación
```

CU-31 está cerrado. El Ciclo 2 permanece ABIERTO hasta cerrar CU-09.

## Hardening CU-08 previo a CU-09 — evidencia de migración

fix-013 introdujo el candidato native tools y lo endureció en v1.2/v1.3. La evidencia progresó de `tools=70 %` a `82 %` y finalmente, en la corrida de decisión de 20 intentos por categoría, `tools=100 %` frente a `legacy=94 %`, con safety en `100 %`. Esa evidencia autoriza el cutover de fix-014.

<!-- CU08-FIX-014-NATIVE-TOOLS-CUTOVER -->
### CU-08 hardening final — native tools oficial

`C2-cu08-fix-014` completa el cutover arquitectónico previo a CU-09. El benchmark A/B de decisión con 20 intentos por categoría obtuvo `legacy=94 %`, `tools=100 %`, `SAFETY_UNKNOWN_REFERENCE=100 %`. El planner JSON legacy se retira del runtime; Qwen2.5-3B-Instruct Q4_K_M + llama.cpp native tools pasa a ser la única planificación LLM. Texto y voz convergen en el mismo planner. Se incorpora planificación compuesta mediante previews efímeros y una suite holdout separada de la regresión conocida. CU-08 permanece CERRADO y CU-09 continúa siendo el siguiente caso funcional del Ciclo 2.


<!-- CU08-FIX-014-V1.3-HOLDOUT-HARDENING -->
### Evidencia holdout y hardening jerarquico

La primera ejecucion holdout posterior al cutover obtuvo 39.4 % (13/33). El resultado no reabre CU-08 funcionalmente, pero bloqueo el inicio de CU-09 hasta corregir el hardening: la mayoria de los fallos eran `LLM HTTP 400` porque el fallback de catalogo completo generaba ~5.1k tokens sobre un contexto de 4096; ademas, Qwen devolvia varias `tool_calls` validas en peticiones compuestas y el planner exigia artificialmente exactamente una.

Fix-014 v1.3 sustituye ese fallback por routing native jerarquico (`route_uml_request` sin enums del proyecto -> una tool UML pesada por step), acepta/deferiere llamadas extra de la misma ronda y liga multiplicidades explicitas desde el texto. CU-09 permanece como siguiente caso funcional, condicionado a una nueva corrida `regression` + `holdout` verde.


<!-- CU08-FIX-014-V1.4-HOLDOUT-ADJUDICATION -->
### Segunda evidencia holdout — route adjudication

Tras fix-014 v1.3 el holdout subio a `19/33 = 57.6 %`: desaparecieron los errores por contexto y `SAFETY_UNKNOWN_REFERENCE` quedo en `100 %`. Los fallos restantes mostraron pasos routed extra en peticiones simples y bursts de 20+ tool calls en un unico step.

Fix-014 v1.4 agrega adjudicacion project-aware de la ruta, colapsa peticiones simples a una unica operacion, ordena compuestos por dependencia, descarta tool calls repetidas y reintenta una vez cuando llama.cpp ignora `tool_choice=required` y responde texto. CU-09 continua bloqueado hasta repetir holdout y regression con esta revision.

<!-- CU08-FIX-014-V1.5-HOLDOUT-STATELESS -->
### Tercera evidencia holdout — compound stateless

Tras fix-014 v1.4 el holdout subió a `28/33 = 84.8 %`; `RENAME_CLASS`, `DELETE_CLASS`, `ADD_ATTRIBUTES`, `UPDATE_ATTRIBUTE`, `DELETE_ATTRIBUTE`, `CREATE_RELATIONSHIP`, `DELETE_RELATIONSHIP` y `SAFETY_UNKNOWN_REFERENCE` quedaron en `100 %`. Los únicos fallos fueron una creación natural mal detectada como compound, un extremo de multiplicidad conversacional y las tres solicitudes multi-tool dentro del gateway.

Fix-014 v1.5 elimina el historial `assistant/tool` entre steps compuestos y usa únicamente el `ProjectDocument` efímero como estado, corrige la detección `Añade ... una nueva clase` y amplía el binding de multiplicidad `ninguna/varias`. CU-09 continúa bloqueado hasta repetir holdout y regression.

<!-- CU08-FIX-014-V1.6-HOLDOUT-LAZY-PARSE -->
### Cuarta evidencia holdout — aislamiento final del gateway

Tras fix-014 v1.5 el holdout alcanzo `30/33 = 90.9 %`. `CREATE_CLASS`, `RENAME_CLASS`, `DELETE_CLASS`, `ADD_ATTRIBUTES`, `UPDATE_ATTRIBUTE`, `DELETE_ATTRIBUTE`, `CREATE_RELATIONSHIP`, `UPDATE_RELATIONSHIP`, `DELETE_RELATIONSHIP` y `SAFETY_UNKNOWN_REFERENCE` quedaron en `100 %`. El unico ERROR fue `MULTI_TOOL_COMPOUND=0/3`.

Los tres fallos compuestos fueron `UnexpectedEndOfInputException`: el gateway parseaba llamadas repetidas sobrantes y una de ellas quedaba truncada por el limite de completion, aunque la primera tool call util ya fuera valida. Fix-014 v1.6 aplica parse perezoso de la primera llamada compatible y un unico retry de truncamiento con 512 completion tokens. CU-09 permanece bloqueado hasta repetir holdout y regression con esta revision.
