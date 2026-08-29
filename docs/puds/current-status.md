# Estado actual PUDS

**Fecha de corte:** 28 de agosto de 2026.

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
