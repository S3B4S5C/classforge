# ClassForge

> Model it. Generate it. Talk to it.

ClassForge es una herramienta CASE web, colaborativa y offline-first para modelar diagramas de clases UML y utilizarlos como fuente de verdad para futuras transformaciones y generación de aplicaciones.

La visión completa del producto está en `docs/product/product.md`. El estado realmente implementado está en `docs/puds/current-status.md`.

## Estado actual

**Ciclo 1 cerrado. Ciclo 2 de Elaboración ABIERTO — CU-31 CERRADO; CU-09 SIGUIENTE.**

En ClassForge llamamos **Ciclo** a una **iteración PUDS**.

El incremento ejecutable actual incluye:

- autenticación local, JWT, BCrypt y ownership;
- CU-01 crear proyecto;
- CU-02 abrir y guardar `ProjectDocument` con revisión;
- CU-03 modelado UML manual mediante Angular + JointJS;
- clases, atributos, layout, relaciones y multiplicidades;
- Association, Aggregation, Composition y Generalization;
- CU-04 validación UML explícita;
- CU-05 Command Bus + Undo/Redo;
- CU-06 colaboración autoritativa Spring WebSocket/STOMP;
- CU-07 presencia efímera;
- CU-08 lenguaje natural y voz local para proponer cambios UML;
- llama.cpp + Qwen2.5-3B-Instruct Q4_K_M con native tool calling como planner local;
- whisper.cpp como Speech-to-Text local;
- preview, grounding, validación, BATCH atómico y protección por revisión;
- health de runtimes locales y diagnóstico del pipeline.

También están implementados como infraestructura transversal:

- CU-28 registrar cuenta;
- CU-29 iniciar sesión;
- CU-30 ownership persistente, ampliado en CU-31 con proyectos compartidos;
- CU-24 STT local y CU-25 IA local en el contexto del Asistente UML.

Aún no están implementados:

- CU-09 imagen a UML;
- CU-10/11 XMI Enterprise Architect;
- CU-12 modelo relacional;
- CU-13..18 generación backend/frontend/mobile;
- CU-19..23 voz sobre la aplicación generada;
- CU-26 auditoría histórica completa;
- CU-27 demo reproducible formal;
- CU-31 cerrado: membership, invitaciones, STOMP, presencia y Assistant validados con OWNER/EDITOR/NONE.

CU-31 quedó cerrado en C2-cu31-003 con colaboración multi-cuenta real, defensa de acceso unificada y hardening concurrente de invitaciones. CU-09 — Imagen → UML — es el siguiente caso del Ciclo 2.

## Arquitectura vigente

```text
UI manual / colaboración / Asistente
                |
                v
          UmlCommand / BATCH
                |
                v
          UmlCommandBus
                |
                v
       UmlCommandExecutor
                |
                v
         ProjectDocument
        /               \
   UmlModel          DiagramLayout
        |
        +--> validación
        +--> JointJS como proyección
        +--> persistencia autoritativa
```

Principios:

1. `ProjectDocument` es la raíz documental persistida.
2. `UmlModel` es la fuente de verdad semántica.
3. `DiagramLayout` se mantiene separado de la semántica.
4. JointJS representa el modelo; no es el modelo.
5. UUID es identidad estable.
6. Las mutaciones pasan por comandos tipados.
7. El backend valida, controla revisión y persiste como autoridad final.
8. Presencia es efímera y no incrementa revisión.
9. Whisper transcribe; Qwen selecciona tools UML; Java grounded-resuelve, valida y previsualiza.
10. La IA nunca modifica directamente `ProjectDocument`.

## Ejecutar backend

```powershell
cd backend
.\gradlew.bat bootRun
```

Backend:

```text
http://localhost:8082
```

Health:

```text
GET http://localhost:8082/api/health
```

## Ejecutar frontend

```powershell
cd frontend
npm install
npm start
```

Frontend:

```text
http://localhost:4200
```

El proxy Angular reenvía `/api/*` a `http://localhost:8082`.

## Runtimes locales del Asistente

```text
llama-server   127.0.0.1:8092
whisper-server 127.0.0.1:8093
```

Consultar `docs/runtime/assistant-local.md`.

## Persistencia

- Spring Data JPA/Hibernate;
- H2 en archivo durante desarrollo;
- H2 en memoria para tests;
- PostgreSQL permanece como destino posterior.

Un posible archivo `*.classforge` continúa siendo un formato portable futuro, no la persistencia primaria.

## Documentación

- `docs/README.md`: índice documental y fuente prevista para el Word final;
- `docs/product/`: visión y alcance del producto objetivo;
- `docs/puds/`: proceso, ciclos, casos de uso y estado;
- `docs/architecture/`: decisiones técnicas y correspondencia con código;
- `docs/runtime/`: instalación y ejecución de runtimes locales;
- `docs/uml/`: catálogo de diagramas UML académicos y su estado.

## Próximo hito

El **Ciclo 2 está formalmente ABIERTO**; CU-31 está cerrado y CU-09 es el siguiente caso.

Antes de implementar el siguiente CU se debe seleccionar el objetivo del nuevo ciclo desde `docs/puds/use-cases.md` y registrar su objetivo, riesgos y criterios de salida.
