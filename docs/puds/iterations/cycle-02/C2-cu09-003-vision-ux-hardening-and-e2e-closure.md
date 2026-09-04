# C2-cu09-003 — Vision UX, hardening y E2E closure

**Estado del incremento:** IMPLEMENTADO / VALIDACIÓN REAL PENDIENTE  
**CU asociado:** CU-09 — Crear UML desde imagen/fotografía  
**Decisión de proceso:** terminar toda la implementación antes de calibrar y seleccionar definitivamente el VLM.

## Objetivo

Completar el alcance funcional de Imagen → UML y dejar preparados los mecanismos de experimentación y aceptación necesarios para cerrar CU-09 después de iterar el modelo visual. Este incremento no convierte una corrida no realizada en evidencia ni marca el CU como cerrado.

## Alcance implementado

- preparación de imagen desde selector, drag & drop, clipboard y cámara compatible;
- rotación, recorte conservador y reset local;
- cancelación/retry de análisis sin mutación;
- overlay de `VisionEvidence` cuando el proposal incluye bounding boxes;
- confidence visible únicamente como información;
- validación backend de bounding boxes contra dimensiones normalizadas;
- disposiciones `READY`, `NO_CHANGES` y `NO_ACTIONABLE_UML`;
- `command=null` para resultados sin cambios accionables;
- omisión conservadora de conflictos explícitos con atributos/relaciones existentes;
- dataset hardening y benchmark reutilizando el comparador semántico de CU09-002;
- modo exploratorio sin thresholds;
- E2E real de imagen → plan → comando canónico → persistencia → reopen;
- agregador de aceptación final con gates y regresión CU-08.

## No cambia

La única ruta de mutación continúa siendo:

```text
VisionUmlProposal
 -> grounding/compiler
 -> AssistantSemanticPlan
 -> UmlAssistantCommandResolver
 -> BATCH/preview
 -> Apply
 -> Command Bus
 -> ProjectDocument
```

No existe `VisionApply`, Image Command Bus ni escritura directa desde el VLM. Las imágenes tampoco pasan a formar parte de `ProjectDocument`.

## Política para cambios existentes

Si una clase/atributo/relación visual ya está representada, se omite y puede conducir a `NO_CHANGES`. Si la imagen contradice explícitamente tipo, visibilidad, nullability, identifier o multiplicidad de un elemento existente, CU09 no realiza un update silencioso: produce warning y omite ese cambio.

## Fase de calibración posterior

Aplicar este parche no requiere Qwen3-VL levantado. Los tests deterministas usan gateways fake/local HTTP fake y el build normal no habilita `AssistantVisionAcceptanceIntegrationTest`.

Para empezar la calibración después de terminar la implementación:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-explore.ps1 -Attempts 2 -VerboseAttempts
```

Puede repetirse cambiando `-ModelLabel`, modelo cargado, cuantización y/o prompt. Los thresholds son 0 en este modo para que los reportes informen el resultado sin bloquear la iteración.

## Aceptación de cierre

Cuando la calibración termine:

```powershell
pwsh -NoProfile -File .\scripts\assistant-vision-acceptance.ps1 -Attempts 2 -VerboseAttempts
```

Gates iniciales:

- regression semantic exact >= 95 %;
- holdout semantic exact >= 85 %;
- hardening semantic exact >= 75 %;
- schema valid = 100 %;
- safety invalid-image = 100 %;
- E2E real de persistencia verde;
- regresión de CU-08 verde.

Los thresholds pueden revisarse documentalmente si la evidencia demuestra que una métrica distinta representa mejor el riesgo, pero no deben reducirse ad hoc para fabricar un PASS.

## Estado PUDS

Después de aplicar CU09-003:

```text
CU-09: EN PROGRESO
C2-cu09-001: COMPLETADO
C2-cu09-002: IMPLEMENTADO / CALIBRACIÓN PENDIENTE
C2-cu09-003: IMPLEMENTADO / VALIDACIÓN FINAL PENDIENTE
```

Después de una aceptación real verde se crea/actualiza la evidencia y recién entonces se promueve:

```text
CU-09: CERRADO
```

## Definition of Done técnico del parche

- código de UX/hardening/E2E presente;
- tests deterministas CU09 verdes;
- backend `clean build` verde;
- frontend build verde;
- ninguna prueba real de VLM exigida durante la aplicación;
- scripts de exploración y aceptación disponibles;
- documentación expresa que el cierre formal depende de evidencia posterior.


## Cal-001 — primera iteración sobre evidencia real

La primera ejecución hardening posterior a la implementación obtuvo infraestructura 100 % estable pero `semantic exact=42.9 %` y `safety invalid=0 %`. No se cambia de modelo todavía: primero se endurece el prompt del baseline 2B contra cuatro patrones observados (tipos explícitos, multiplicidades, GENERALIZATION y rechazo de no-UML).

Durante la revisión se detectó además un error de ground truth: `shadow-association.png` muestra `Cliente(email:String) — Factura(total:Decimal)`, mientras el manifest esperaba `Pedido — LineaPedido`. Cal-001 corrige el manifest para que el benchmark mida al modelo y no una expectativa equivocada. CU-09 continúa `EN PROGRESO` hasta repetir explore y, finalmente, ejecutar acceptance.


## Cal-006 — Qwen3-VL-4B seleccionado y hardening de pizarra densa

Se congela Qwen3-VL-4B-Instruct Q4_K_M como candidato seleccionado para CU-09. La decisión se basa en evidencia reproducible: el 4B obtuvo 100 % semantic exact en regression y holdout, 100 % safety y completó 2/2 respuestas estructuradas de la pizarra real con 3200 tokens; el 2B truncó 2/2 aun con 4000 tokens. La selección no cierra CU-09: la topología y multiplicidades de la pizarra real siguen en calibración.

Cal-006 no relaja el fail-closed. Ajusta el contrato de grounding para aceptar un atributo cuando su `evidence.label` individual sufre un artefacto de separador pero el `evidence` de la clase contenedora respalda exactamente el símbolo; si ninguno lo respalda, se rechaza. `NO_ACTIONABLE_UML` pasa a ser incoherencia rechazable cuando coexistente con clases o relaciones.

El prompt se endurece sin cambiar el schema ni el compiler:

- atributo sin tipo visible => `dataType` omitido; no inferir DATE/INTEGER por nombre;
- evidence de atributo corto/literal, sin prefijar la clase;
- relaciones densas trazadas físicamente extremo a extremo; cruces sin nodo no conectan;
- segunda pasada exclusiva para conexiones faltantes y multiplicidades;
- `NO_ACTIONABLE_UML` solo con `classes=[]` y `relationships=[]`.

La acceptance formal se ejecutará únicamente después de volver verde el caso focal de pizarra y repetir las suites completas.
