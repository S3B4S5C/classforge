# CU-19 — Closure report

**Caso:** Interactuar con los datos de la aplicación generada mediante lenguaje natural y voz
**Ciclo:** 8
**Estado:** CERRADO tras ejecutar el patch runner con todos los gates GREEN.

CU-19 absorbe CU-20..23 y entrega un único pipeline de consulta, creación, relaciones, actualización y eliminación. El Spring generado reutiliza las instancias locales de whisper.cpp y Qwen/llama.cpp, mientras Angular y Flutter solo consumen el API Assistant del backend generado.

La IA propone native tool calls; Java grounded-resuelve y valida contra metadata derivada de Domain Manifest. Las consultas pueden ejecutarse directamente. Las mutaciones requieren preview sanitizado y token opaco antes de `/apply`; los campos sensibles no se exponen al cliente.

Evidencia estructurada: `cu19-acceptance.json`.


## Hardening de acceptance en Windows

Durante la recertificación manual posterior al patch se observó un fallo de infraestructura en `generatedFlutterAcceptance` cuando JUnit extraía el proyecto Flutter en una unidad distinta del `PUB_CACHE`. Kotlin incremental no puede relativizar rutas entre raíces de Windows diferentes (`F:` frente a `C:`). El task fija ahora `java.io.tmpdir` bajo `backend/build/tmp/generated-flutter-acceptance`, manteniendo el proyecto temporal en el volumen del repositorio sin desactivar compilación incremental ni alterar el proyecto Flutter generado.
