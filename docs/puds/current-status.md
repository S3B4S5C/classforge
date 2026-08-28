# Estado actual PUDS

**Fecha de corte:** 28 de agosto de 2026.

```text
Fase PUDS: Elaboración
Ciclo: 1
Estado del Ciclo 1: CERRADO
Ciclo 2: NO ABIERTO
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
| CU-30 Proyectos propios | CERRADO | aislamiento por ownership |

## Capacidades técnicas adelantadas

CU-08 requirió validar antes de tiempo dos capacidades previstas en el backlog:

- CU-24 STT local: implementado como infraestructura mediante whisper.cpp;
- CU-25 IA local: implementado como infraestructura mediante llama.cpp + Gemma 3 4B.

Esto no significa que CU-19..23 de la futura aplicación generada estén terminados. Solo significa que la infraestructura local ya fue ejercitada dentro de ClassForge.

## Arquitectura que queda validada

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
- Whisper sin conocimiento del `ProjectDocument`;
- preview antes de aplicar IA;
- grounding y validación determinista;
- stale-plan guard;
- health de llama.cpp y whisper.cpp.

## Persistencia

- Spring Data JPA/Hibernate;
- H2 archivo en desarrollo;
- H2 memoria en tests;
- PostgreSQL permanece planificado.

## Limitaciones vigentes

1. La autorización colaborativa sigue siendo owner-only.
2. CU-31 membresía/invitaciones no está implementado.
3. Imagen a UML no está implementado.
4. XMI/Enterprise Architect no está implementado.
5. Modelo relacional y generadores no están implementados.
6. La aplicación generada y su asistente de voz aún no existen.
7. Auditoría histórica completa CU-26 permanece pendiente.
8. `docs/uml/` tiene catálogo preparado, pero los diagramas académicos aún deben elaborarse.

## Siguiente paso

No se declara automáticamente un "siguiente CU" en este documento.

Para abrir Ciclo 2 se debe:

1. seleccionar objetivo y casos desde `use-cases.md`;
2. declarar riesgos a reducir;
3. fijar criterios de salida;
4. crear su documento en `cycles/`;
5. crear los incrementos técnicos necesarios en `iterations/cycle-02/`.
