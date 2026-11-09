# Integración y migración a Groq — evidencia

Fecha: 2026-11-08. Rama de trabajo: `tiago`.
Base integrada: `e293845`, que ya contiene las entregas A y B y la persistencia MongoDB.

## Verificación final

| Verificación | Resultado |
| --- | --- |
| Suite backend completa | 517 casos: 507 aprobados, 10 opt-in omitidos, 0 fallos y 0 errores |
| `generatedAssistantAcceptance` | 1 caso adicional aprobado; genera y compila completamente los proyectos Simple y Auth |
| `bootJar` | Correcto |
| Frontend `test:characterization` | 46 aprobados: generación 15, comandos 5, workspace 10, asistente 9, actividad/notificaciones 7 |
| Angular producción | Build correcto |
| Sintaxis del script PowerShell de bundle | Parser sin errores |
| `git diff --check` | Sin errores de espacios |

Se usó Java 21.0.13, Node 24.22.1 y MongoDB 8.0.17 con replica set real.
MongoDB fue una instancia nativa aislada en el puerto 27028 y directorio `.demo`;
las pruebas crearon bases privadas. No se migró ni reinició la base habitual del usuario.

Los límites temporales del runner se ajustaron para la memoria disponible:
Gradle 256 MB, worker de pruebas 384 MB, un worker y caché de cuatro contextos.
Los primeros intentos se interrumpieron por memoria del equipo; la pasada final
completa terminó correctamente. El perfil demo de la suite recibió también la
URI de pruebas para no intentar conectarse al puerto habitual 27017.

Las diez pruebas omitidas pertenecen a acceptance/benchmarks opt-in heredados.
La aceptación de los asistentes generados, incluida en ese grupo, se ejecutó
además explícitamente con `generatedAssistantAcceptance` y pasó para Simple y Auth.

## Groq real

La API de modelos autenticada confirmó disponibilidad de:

- `openai/gpt-oss-120b`;
- `qwen/qwen3.8-27b`;
- `whisper-large-v3-turbo`.

Se inició el JAR empaquetado con la configuración normal Groq, perfil demo,
backend en 18082 y base privada `cf_groq_smoke_20261108`.
Todas las llamadas siguientes usaron el proveedor real, no un servidor simulado:

| Función | Resultado observado |
| --- | --- |
| Health | Texto, voz e imagen disponibles, nombres `groq-text`, `groq-speech`, `groq-vision` |
| ASK | HTTP200; identificó correctamente las 6 clases y 5 relaciones de Veterinaria |
| REVIEW | HTTP200; revisión sobre la instantánea actual, sin guardar UML |
| SUMMARY_CURRENT | HTTP200; resumen de clases y relaciones actuales |
| EDIT | HTTP200; BATCH para crear `PruebaGroq`, pendiente de Apply |
| Voz | Audio sintético español → Whisper en Groq → propuesta BATCH para clase factura y atributo id de tipo Long |
| Imagen → UML | Fixture público `class-simple.png` → HTTP200, disposición READY y BATCH |

En cada caso se compararon documento y revisión antes/después de proponer:
se mantuvieron iguales. No se aplicaron cambios UML automáticamente.
La prueba real de imágenes usó un fixture simple; no se presenta como benchmark
de precisión de todas las pizarras o diagramas densos.

## Casos detectados y corregidos

- **Timeout total:** se reprodujo en Java 21 el bloqueo tras recibir headers
  pero no el cuerpo. Tanto ClassForge como el cliente exportado usan un plazo
  completo con cancelación. La prueba con cuerpo retenido ahora devuelve un
  error saneado y termina dentro del plazo.
- **Historial creciente:** se reservan instrucciones, snapshot y pregunta; se
  incluyen turnos recientes completos que quepan en 18.000 caracteres, máximo
  veinte mensajes. No se truncan JSON ni preguntas y la conversación no queda
  bloqueada por mensajes antiguos largos.
- **Visión:** se mantiene `reasoning_effort=none` para que las etapas cortas no
  consuman su presupuesto en thinking. El schema semántico Groq omite bbox
  opcional; OpenCV mantiene la geometría. No se inventan coordenadas y los
  validadores siguen rechazando respuestas de bounding box parcial.
- **Cuotas y autenticación:** HTTP429 y Retry-After sobreviven a excepciones
  envueltas. Un 401 del proveedor se transforma en 503 sin invalidar el JWT humano.
- **Generación:** los validadores de artefactos admiten los placeholders Groq y
  exigen los transportes nuevos. Los ocho Java del asistente se compilaron también
  directamente y su transporte se probó con HTTP local, además del build completo.
- **Interfaz:** se actualizaron las referencias activas que anunciaban IA local;
  ahora se informa que Groq requiere Internet.

La validación existente de nombres se mantiene: una transcripción con un
identificador no admitido por las reglas de código se rechaza, en lugar de
persistir un nombre inválido o cambiarlo silenciosamente.

## Credenciales y despliegue

La clave se configura en `.env` local ignorado o en el entorno del backend.
No se incorpora al código, los ZIP exportados ni el frontend. El bundle AWS
activo no instala ni habilita Whisper local ni incluye la política Bedrock.

Groq Free tiene cuotas por organización y el modelo visual está en preview.
La configuración y fuentes oficiales están en
[assistant-groq.md](../../runtime/assistant-groq.md).
