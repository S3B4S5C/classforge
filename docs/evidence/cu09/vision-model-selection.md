# CU-09 — Evidencia de selección del VLM

**Estado:** decisión cerrada.  
**Fecha de cierre de CU-09:** 5 de septiembre de 2026.

## Decisión

Runtime visual seleccionado para CU-09:

```text
Qwen3-VL-4B-Instruct Q4_K_M
mmproj Q8_0
llama.cpp/Vulkan
puerto 8094
alias vision-model
parallel 1
contexto recomendado 6144
```

La selección del modelo se mantuvo durante las calibraciones posteriores; el trabajo se concentró en cambiar fronteras de autoridad entre VLM, OpenCV y Java, no en seguir escalando el modelo.

## Comparación 2B vs 4B

### Qwen3-VL-2B

En la pizarra realista densa, con timeout 180 s y hasta 4000 completion tokens:

- 2/2 intentos terminaron `OUTPUT_CONTRACT` por truncamiento `max_tokens`;
- pico GPU observado: 4417 MiB.

El 2B se descartó como runtime principal porque no completó de forma estable el caso realista bajo el budget probado.

### Qwen3-VL-4B

Antes del hardening geométrico, el 4B ya había demostrado:

- regression semantic exact 100 %;
- holdout semantic exact 100 %;
- safety 100 %;
- respuestas estructuradas completas sobre la pizarra con 3200 completion tokens;
- pico GPU alrededor de 5.4 GiB.

El problema restante era de topología y multiplicidades, no de transporte/schema.

## Qué hacía bien el 4B

La evidencia fue estable en:

- nombres de clases;
- atributos;
- tipos cuando eran explícitos;
- interpretación semántica local de markers;
- lectura de labels pequeños cuando se le mostraban crops apropiados.

La debilidad principal apareció al pedirle simultáneamente:

- localizar cajas en píxeles;
- seguir conectores largos/cruzados;
- decidir qué multiplicidad pertenece a qué connector.

## Evolución después de la selección

### Cal-006

Se congela el 4B como modelo seleccionado. La pizarra ya atraviesa transporte/schema/grounding, pero topología y multiplicidades siguen incompletas.

### Cal-007/008/009

Se experimenta con:

- prompt geométrico más explícito;
- segunda pasada global `relationships-only`;
- crop/tiles benchmark-only.

Ninguno supera consistentemente el mejor single-pass en la pizarra real.

### Cal-010..013

Se cambia la arquitectura:

```text
VLM -> semántica local
OpenCV -> geometría física
Java -> contratos, matching, parser, fail-closed
```

Esto permite mantener el 4B y resolver los fallos sin añadir un segundo modelo GPU.

## Evidencia focal final

Fixture `library-whiteboard-realistic`, `hybrid-cv`, estrategia original, 3 intentos consecutivos:

```text
PASS Exact      3/3
Transport       100.0 %
Schema valid    100.0 %
Grounding       100.0 %
Classes         18/18, 0 unexpected
Attributes      51/51, 0 unexpected
Relationships   18/18, 0 unexpected
Multiplicity    30/30, 0 unexpected
Semantic exact  100.0 %
Safety invalid  100.0 %
GPU peak        5455 MiB
Elapsed         8m45s
```

La memoria observada confirma que el 4B cabe en la GPU de referencia con el resto del diseño CPU-only de OpenCV.

## Configuración productiva asociada

```text
semantic completion tokens = 3200
mapping tokens             = 1200
relationship tokens        = 512
multiplicity tokens        = 128
hybrid enabled             = true
hybrid min classes         = 4
hybrid fallback            = false
```

## Conclusión

Qwen3-VL-4B Q4_K_M queda seleccionado no porque resuelva por sí solo toda la reconstrucción UML, sino porque ofrece suficiente capacidad semántica/local cuando se le asignan tareas delimitadas. La precisión final depende de la separación de responsabilidades implementada por Cal-011/012/013.
