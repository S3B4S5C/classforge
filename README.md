# ClassForge

> Model it. Generate it. Talk to it.

ClassForge es una herramienta CASE web, colaborativa y offline-first para modelar diagramas de clases UML y utilizarlos como fuente de verdad para futuras transformaciones y generación de aplicaciones.

La visión completa del producto está en `docs/product/product.md`. El estado realmente implementado está en `docs/puds/current-status.md`.

## Estado actual

**Fase de Construcción — Ciclos 1, 2, 3, 4 y 5 CERRADOS; CU-16 CERRADO.**

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
- health de runtimes locales y diagnóstico del pipeline;
- CU-09 Imagen -> UML: pipeline local Qwen3-VL + OpenCV + Java, fail-closed, BATCH/preview/Apply canónico y persistencia validada;
- CU-12 UML -> `RelationalModel`: IR interna, efímera y determinista;
- CU-13 generación Spring Boot/JPA: Java 21, Spring Boot 4.0.8, Gradle Wrapper 9.2.0, entidades/repositorios, H2/PostgreSQL, ZIP determinista y acceptance compilable.
- CU-14 API CRUD expresiva: DTOs/services/controllers, búsqueda/filtros/orden/paginación/conteo y dos perfiles explícitos: CRUD simple o Sistema de Información con Auth (BCrypt + JWT);
- CU-15 OpenAPI/Postman: `openapi.yaml` 3.0.3 + `postman_collection.json` 2.1 deterministas desde un contrato HTTP canónico;
- CU-16 Domain Manifest: `domain-manifest.json` schema 1.0 determinista con UUIDs estables, IDs, atributos, relaciones, herencia, capacidades, operationIds y metadata Auth.

También están implementados como infraestructura transversal:

- CU-28 registrar cuenta;
- CU-29 iniciar sesión;
- CU-30 ownership persistente, ampliado en CU-31 con proyectos compartidos;
- CU-24 STT local y CU-25 IA local en el contexto del Asistente UML.

Aún no están implementados:

- CU-10/11 XMI Enterprise Architect;
- CU-17/18 frontend web/mobile generado;
- CU-19..23 voz sobre la aplicación generada;
- CU-26 auditoría histórica completa;
- CU-27 demo reproducible formal;

CU-31 quedó cerrado en C2-cu31-003. CU-09 — Imagen → UML — quedó cerrado el 5 de septiembre de 2026 después del hardening hybrid-CV, política fail-closed, E2E canónico, canonicalización de identificadores y smoke manual de producto. La validación multi-pizarra adicional se conserva como recomendación de robustez y no se presenta como evidencia ejecutada.

En el Ciclo 3, CU-12 cerró la transformación determinista UML -> modelo relacional, CU-13 la generación/exportación Spring Boot/JPA y CU-14 la API CRUD expresiva con perfiles Simple/Auth. La evidencia de CU-13 está en `docs/evidence/cu13/` y la de CU-14 en `docs/evidence/cu14/`.

El **Ciclo 4** está CERRADO con CU-15. Cada export CU-14 incluye `openapi.yaml` y `postman_collection.json` generados determinísticamente desde el mismo contrato canónico de API; Auth documenta bootstrap/login/Bearer JWT y Simple no declara seguridad.

El **Ciclo 5** está CERRADO con CU-16. Cada export CU-14/15 incluye `domain-manifest.json` schema `1.0`, generado directamente desde los modelos/planes canónicos y validado contra OpenAPI/Postman antes del ZIP.

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
llama-server texto   127.0.0.1:8092
whisper-server       127.0.0.1:8093
llama-server Vision  127.0.0.1:8094
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

Los **Ciclos 1, 2, 3, 4 y 5 están formalmente CERRADOS**. **CU-16 — Domain Manifest** está CERRADO con acceptance dedicado; el siguiente candidato es **CU-17 — frontend web Angular generado**.

La evidencia de cierre está en:

- CU-09: `docs/evidence/cu09/cu09-closure-report.md`;
- CU-13: `docs/evidence/cu13/cu13-closure-report.md` y `docs/evidence/cu13/cu13-acceptance.json`.
