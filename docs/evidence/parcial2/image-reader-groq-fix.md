# Lector de imágenes: Groq en Azure

Verificado el 10 de noviembre de 2026.

El reporte del usuario era «No pudimos interpretar esta imagen». En la prueba
real con `library-whiteboard-realistic.png`, la primera inferencia devolvió una
propuesta semántica válida. El modo híbrido añadió mapeo de cajas, clasificación
de cada conector y lectura de multiplicidades por extremo. Estas consultas
adicionales devolvieron HTTP429 y descartaron la lectura inicial.

La configuración general y de Azure ahora conserva una sola consulta visual.
Grounding, bounds, referencias, compilación, revisión del proyecto y preview
siguen siendo obligatorios; los cambios requieren Apply. El modo híbrido puede
activarse explícitamente. La interfaz diferencia cuota/tiempo de espera de una
respuesta visual inválida, sin mostrar mensajes privados del proveedor.

Validación:

- Regresión RED: el default anterior intentó el camino híbrido y falló por cuota.
- Backend: 537 pruebas, 527 pasadas, 10 opcionales omitidas, cero fallos.
- Frontend: `test:characterization` y build de producción correctos; incluye tres
  regresiones nuevas para errores del lector.
- Artefacto público verificado sin credenciales privadas.
- Azure: SHA256 del JAR
  `879398a2d2d0fefea9b76cbb372fdfde5021d4647e52e7ed733b2dea912f1646`.
- Smoke público: health, rutas Angular, API protegida y WebSocket correctos.
- Imagen real de prueba: HTTP200, READY en 6,638s; seis CREATE_CLASS y cinco
  CREATE_RELATIONSHIP. La revisión y el documento persistido no cambiaron.
- Servicios ClassForge/nginx activos y configuración privada conservada.

El APK conectado utiliza este mismo backend y web; este arreglo no requiere
reemplazar el APK. La prueba documentada usa un fixture del repositorio, no la
imagen específica del usuario. La cuota gratuita sigue siendo compartida y
puede requerir esperar entre solicitudes.
