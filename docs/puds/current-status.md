# Estado actual PUDS

**Fecha de corte:** 13 de septiembre de 2026.

```text
Fase PUDS: Construcción
Ciclo 1: CERRADO
Ciclo 2: CERRADO
CU-31: CERRADO
CU-09: CERRADO
Ciclo 3: CERRADO
Ciclo 4: CERRADO
Ciclo 5: CERRADO
Ciclo 6: CERRADO
Ciclo 7: CERRADO
Ciclo 8: CERRADO
Ciclo 9: CERRADO
CU-12: CERRADO
CU-13: CERRADO
CU-14: CERRADO
Current increment: C9-cu10-cu11-001 CLOSED - XMI 2.1 / Enterprise Architect bidireccional
CU-15: CERRADO
CU-16: CERRADO
CU-17: CERRADO
CU-18: CERRADO
CU-19: CERRADO
CU-20..23: ABSORBIDOS POR CU-19
CU-10: CERRADO
CU-11: CERRADO
CU-26: DESCARTADO / FUERA DE ALCANCE
```

## Resumen ejecutivo del corte

Los Ciclos 1–9 están cerrados. El Ciclo 3 completó CU-12, CU-13 y CU-14; el Ciclo 4 cerró CU-15; el Ciclo 5 cerró CU-16; el Ciclo 6 cerró CU-17 Angular; el Ciclo 7 cerró CU-18 Flutter/Android; el Ciclo 8 cerró CU-19 y absorbió CU-20..23; y el Ciclo 9 cerró CU-10/CU-11 con import/export XMI 2.1 seguro, determinista y con round-trip semántico. CU-26 queda descartado/fuera de alcance por decisión de producto.

Como antecedente inmediato, el Ciclo 2 se cerró después de resolver sus dos riesgos principales:

1. colaboración entre cuentas reales mediante OWNER/EDITOR/NONE, invitaciones, STOMP y presencia (`CU-31`);
2. entrada visual segura al mismo modelo UML canónico mediante un pipeline local VLM + OpenCV + Java (`CU-09`).

CU-09 queda funcionalmente aceptado el 5 de septiembre de 2026. La imagen nunca se convierte en una segunda fuente de verdad: produce una propuesta estructurada, ésta se valida y compila a un `BATCH`, se previsualiza y sólo se persiste después de `Apply` por la autoridad colaborativa normal.

## Casos cerrados

| Caso | Estado | Resultado principal |
|---|---|---|
| CU-01 Crear proyecto | CERRADO | proyecto persistente con UUID y owner |
| CU-02 Abrir/guardar | CERRADO | `ProjectDocument`, revisión y conflicto |
| CU-03 Modelado manual | CERRADO | clases, atributos, relaciones, multiplicidades y JointJS |
| CU-04 Validar UML | CERRADO | validación explícita y diagnósticos |
| CU-05 Undo/Redo | CERRADO | Command Bus e historial reversible |
| CU-06 Colaboración realtime | CERRADO | STOMP, backend autoritativo y revisión |
| CU-07 Presencia | CERRADO | presencia efímera, selección y cursor |
| CU-08 Voz/lenguaje natural a UML | CERRADO | texto/voz -> plan -> BATCH -> preview -> aplicar |
| CU-09 Imagen/fotografía a UML | CERRADO | imagen -> VLM/OpenCV -> propuesta -> BATCH -> preview -> Apply -> persistencia |
| CU-10 Importar XMI Enterprise Architect | CERRADO | XMI 2.1 -> parser seguro -> ProjectDocument -> preview token -> apply con revisión |
| CU-11 Exportar XMI Enterprise Architect | CERRADO | ProjectDocument -> XMI 2.1 determinista -> round-trip semántico |
| CU-28 Registrar cuenta | CERRADO | identidad local, BCrypt y JWT |
| CU-29 Iniciar sesión | CERRADO | autenticación stateless |
| CU-30 Proyectos propios | CERRADO | ownership persistente |
| CU-31 Membresía e invitaciones | CERRADO | OWNER/EDITOR/NONE, invitaciones y colaboración real |
| CU-12 Modelo relacional | CERRADO | IR interna, efímera y determinista consumida por CU-13 |
| CU-13 Generar backend Spring Boot/JPA | CERRADO | ZIP reproducible Java 21/Spring Boot 4.0.8, export autorizado y acceptance compilable sobre H2 |
| CU-14 Generar API CRUD expresiva | CERRADO | DTOs/services/controllers; CRUD/filtros/paginación/relaciones; perfiles Simple/Auth con BCrypt + JWT |
| CU-15 Generar OpenAPI y Postman | CERRADO | OpenAPI 3.0.3 + Postman 2.1 deterministas, paridad de operaciones y seguridad Simple/Auth |
| CU-16 Generar Domain Manifest | CERRADO | `domain-manifest.json` schema 1.0 determinista, referencialmente validado y coherente con OpenAPI/Postman |
| CU-17 Generar frontend web Angular | CERRADO | `frontend/` standalone con dashboard, componentes específicos, CRUD/relaciones, Simple/Auth y production-build acceptance |
| CU-18 Generar frontend mobile Flutter | CERRADO | `mobile/` Flutter con dashboard, páginas específicas, CRUD/relaciones, Simple/Auth, secure JWT y Android debug-build acceptance |
| CU-19 Interactuar con datos mediante lenguaje natural y voz | CERRADO | Assistant Spring + Angular/Flutter; Whisper/Qwen native tools, grounding Java, reads directos y mutaciones preview/apply |
| CU-20..23 | ABSORBIDOS POR CU-19 | creación, relaciones, actualización y eliminación pasan a ser intents de CU-19 |

## CU-09 — estado final

### Flujo productivo

```text
imagen PNG/JPEG/WEBP
        |
        v
validación + normalización
        |
        v
Qwen3-VL semantic first pass
        |
        +-- < 4 clases --> semantic-only
        |
        +-- >= 4 clases --> OpenCV class regions
                              |
                              v
                        closed Bx -> classRef mapping
                              |
                              v
                        OpenCV physical topology
                              |
                              v
                        per-edge type/marker VLM
                              |
                              v
                        conditioned endpoint transcription
                              |
                              v
                        explicit edge attribution
                              |
                              v
                        Java multiplicity parser
                              |
                              v
                        VisionUmlProposal
        |
        v
canonicalización determinista de identificadores
        |
        v
AssistantSemanticPlan
        |
        v
UmlAssistantCommandResolver
        |
        v
BATCH + preview
        |
        v
Apply -> ProjectOperation -> ProjectCollaborationService
        |
        v
ProjectCommandExecutor -> validación -> persistencia -> revision + 1
```

### Autoridades

- `OpenCV/Java` decide cajas físicas y topología de conectores en el modo híbrido.
- Qwen3-VL interpreta clases/atributos y anota semántica local; no puede inventar endpoints físicos una vez fijada la geometría.
- Java valida grounding, multiplicidades, identificadores, revisión, comandos y `ProjectDocument`.
- JointJS continúa siendo proyección; `ProjectDocument/UmlModel` sigue siendo la fuente de verdad.
- El VLM nunca escribe directamente en el proyecto.

### Runtime final

```text
Qwen3-VL-4B-Instruct Q4_K_M
llama.cpp multimodal :8094
semantic max completion: 3200
hybrid mapping:          1200
relationship:             512
multiplicity:             128
hybrid enabled:          true
min semantic classes:       4
hybrid fallback:         false
```

`CLASSFORGE_ASSISTANT_VISION_HYBRID=false` conserva un kill switch semantic-only. `CLASSFORGE_ASSISTANT_VISION_HYBRID_FALLBACK=true` conserva rollback/debug explícito, pero no es el default productivo.

### Calibraciones cerradas

| Calibración | Estado | Resultado |
|---|---|---|
| Cal-011 | CLOSED | reconstrucción robusta de class regions físicas |
| Cal-012 | CLOSED | topología física de relaciones, 6 pares correctos y 0 extras en la pizarra focal |
| Cal-013 | CLOSED | anotación per-edge, multiplicidades y competitor-conditioned transcription; focal 3/3 Exact |
| Cal-014 | CLOSED | `hybrid-cv` promovido a ruta productiva para diagramas densos |
| Cal-015 | CLOSED | política fail-closed por defecto ante fallo híbrido |
| Cal-016 | CLOSED | E2E canónico plan -> preview -> collaboration Apply -> persistencia -> reopen |
| Cal-017 | CLOSED | canonicalización de identificadores visuales y validación de aplicabilidad del plan |

Cal-010 se conserva como antecedente histórico y fue superada por Cal-011 al retirar al VLM la autoridad de coordenadas de píxel.

## Evidencia final de CU-09

### Hardening focal realista

Fixture: `library-whiteboard-realistic`.

Corrida de estabilidad final de Cal-013:

```text
Attempts:        3
Exact:           3/3
Transport:       100.0%
Schema valid:    100.0%
Grounding:       100.0%
Classes:         18/18 expected, 0 unexpected
Attributes:      51/51 expected, 0 unexpected
Relationships:   18/18 expected, 0 unexpected
Multiplicity:    30/30 expected, 0 unexpected
Semantic exact:  100.0%
Safety invalid:  100.0%
GPU peak:        5455 MiB
Elapsed:         8m45s para 3 intentos
```

La evidencia demuestra estabilidad sobre el fixture focal. No se interpreta como una estimación estadística de generalización a cualquier pizarra.

### E2E canónico

Cal-016 verifica de forma determinista:

- `plan()` no persiste cambios;
- `READY` devuelve el `BATCH` y `baseRevision` usados por el producto;
- el preview del mismo `BATCH` coincide exactamente con el documento persistido y reabierto;
- un `BATCH` con múltiples hijos incrementa la revisión exactamente una vez;
- un plan stale se rechaza con `REVISION_CONFLICT` sin mutación parcial;
- un cambio de revisión durante la inferencia impide devolver `READY`.

### Smoke manual de producto

El smoke manual final verificó satisfactoriamente:

1. imagen real -> `READY` -> preview -> `Apply` -> reload/reopen;
2. preparación local de imagen (`rotate`, `crop`, `reset`, cancelación) sin mutación previa;
3. fail-closed con VLM no disponible, mensaje `VISION` y `Analizar nuevamente`;
4. rechazo de plan stale y regeneración posterior;
5. permisos OWNER/EDITOR y rechazo de un usuario sin permiso de edición.

Durante este smoke se descubrió y corrigió Cal-017: el texto visual puede contener `Categoría`, `Préstamo` o `añoPublicacion`, mientras el dominio exige identificadores compatibles con código. La evidencia visual permanece literal; la frontera de compilación genera identificadores válidos de forma determinista antes del `AssistantSemanticPlan`.

## Decisión de aceptación y validaciones diferidas

CU-09 se cierra como **implementado y aceptado funcionalmente** con la evidencia anterior. Para mantener la documentación honesta, las siguientes validaciones no se presentan como ejecutadas en este corte:

- benchmark estadístico con las dos pizarras adicionales que se habían propuesto para medir generalización;
- ejecución archivada del agregador completo `assistant-vision-acceptance.ps1` posterior a todas las calibraciones;
- un `frontend build` final separado del smoke interactivo no quedó registrado como evidencia de este corte.

Estas actividades quedan como **validación adicional recomendada**, no como funcionalidad faltante de CU-09. La aceptación registrada asume conscientemente ese riesgo residual. La evidencia canónica del cierre está en `docs/evidence/cu09/cu09-closure-report.md` y `docs/evidence/cu09/cu09-acceptance.json`.

## Ciclo 2 — resultado

El objetivo del Ciclo 2 queda satisfecho:

- `CU-31` demuestra colaboración entre cuentas reales sobre la autoridad canónica;
- `CU-09` demuestra entrada visual segura sobre la misma autoridad canónica.

Por tanto:

```text
Ciclo 2: CERRADO
```

CU-10/CU-11 quedan cerrados en el Ciclo 9. El único caso funcional planificado restante es CU-27; CU-26 se conserva como descartado/fuera de alcance.

## CU-13 — cierre final

C3-cu13-006 completa la validación de `GeneratedProject` y la prueba ejecutable del artefacto entregado. Antes de archivar se exige skeleton obligatorio, acuerdo package/path, referencias internas resolubles y ausencia de marcadores FreeMarker sin resolver. El acceptance genera desde `UmlModel`, produce el ZIP determinista, extrae solo en temporales y ejecuta el wrapper generado con Java 21.

La matriz cubre simple ID, composite ID, 1:N, 1:1, N:M, aggregation, composition, composite FK, JOINED, multi-level JOINED y relación a subclass. Todos los proyectos deben aprobar `clean build` y `ApplicationTests.contextLoads()` sobre H2 sin cambios manuales. La fixture de relaciones se genera dos veces y exige igualdad byte a byte y SHA-256.

Evidencia: `docs/evidence/cu13/cu13-closure-report.md` y `docs/evidence/cu13/cu13-acceptance.json`.

## Limitaciones vigentes del producto

1. No existe eliminación de membership activa ni revocación inmediata de una sesión STOMP ya conectada.
2. La generalización estadística de Imagen -> UML sobre múltiples pizarras reales puede reforzarse con un conjunto adicional de fotografías.
3. XMI/Enterprise Architect está implementado para el subconjunto UML canónico de CU-10/CU-11; no se preserva metadata propietaria de diagramas, perfiles o tagged values de EA.
4. `RelationalModel` no tiene UI ni se persiste. CU-13 está CERRADO: exporta un backend Spring Boot/JPA reproducible, con fallback PK explícito y no persistente, validación estructural previa al ZIP y acceptance que compila los proyectos generados y carga Spring sobre H2.
5. CU-14 está cerrado con dos modos explícitos. CRUD simple no genera seguridad; Auth exige entidad + atributos STRING de usuario/password, hashea passwords con BCrypt, excluye password de responses, expone bootstrap inicial + login y protege el resto con JWT. CU-17 genera el frontend web y CU-19 ya incorpora el Assistant de chat/voz en los clientes generados, reutilizando Whisper/Qwen locales.
6. CU-26 auditoría histórica fue descartado/fuera de alcance por decisión de producto.
7. `docs/uml/` conserva el catálogo de diagramas académicos pendientes de elaboración/presentación final.

## Próximo paso PUDS

Ciclos 3–9 están CERRADOS. CU-10/CU-11 quedan cerrados con evidencia en `docs/evidence/cu10-cu11/`; CU-26 está DESCARTADO / FUERA DE ALCANCE. El siguiente y último caso funcional planificado es CU-27 — demo reproducible, incluyendo smoke manual de Enterprise Architect.
