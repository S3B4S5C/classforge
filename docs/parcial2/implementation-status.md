# Cierre de Persona A y migración NoSQL

Objetivo: completar última visita, notificaciones persistentes/realtime, Activity Center responsive
y pruebas de aceptación; migrar toda la base propia de ClassForge a MongoDB sin perder los datos
H2 existentes ni las garantías de autoría, revisión, sesiones y conversación compartida.

Decisión técnica: MongoDB, con Spring Data MongoDB y replica set para transacciones multidocumento.
DynamoDB requiere rediseñar patrones de acceso e impone 400 KB por elemento; la aplicación almacena
documentos UML e instantáneas de auditoría y necesita consultas combinables por varios campos.

Estado: completado y verificado el 8 de noviembre de 2026, en `codex/activity-sessions`.

- [x] Documentos, repositorios, consultas, índices, transacciones, concurrencia y secuencias en MongoDB.
- [x] Importación H2 con preservación de IDs/datos, verificación, rollback e idempotencia.
- [x] Perfiles, scripts, configuración de despliegue y pruebas adaptados a MongoDB real.
- [x] Última visita con lectura sin consumo y confirmación monotónica de la revisión vista.
- [x] Notificaciones de actividad/sesión persistentes, lectura individual/todas y realtime privado.
- [x] Activity Center responsive con filtros, sesiones, continuidad y campana global.
- [x] Backend: 456 casos, 446 aprobados y 10 opt-in omitidos, sin fallos; `bootJar` correcto.
- [x] Frontend: 46 pruebas aprobadas y compilación de producción correcta.
- [x] Aceptación real con dos usuarios, notificaciones STOMP y layouts desktop/tablet/teléfono.
- [x] Base local anterior importada: 2 usuarios y 3 proyectos, sin modificar el archivo H2 fuente.
- [x] Persistencia de esos datos verificada después de reiniciar el contenedor MongoDB.

Runbook: [persona-a-cierre.md](persona-a-cierre.md).
Resultados y capturas: [aceptación](../evidence/parcial2/acceptance.md).

Los generadores de aplicaciones del primer parcial conservan su contrato de salida Spring/JPA;
la migración NoSQL se aplica al almacenamiento propio de ClassForge, incluyendo el chat IA.

Fuentes técnicas:
- https://www.mongodb.com/docs/manual/reference/limits/
- https://www.mongodb.com/docs/manual/core/transactions-production-consideration/
- https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html
- https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Constraints.html
