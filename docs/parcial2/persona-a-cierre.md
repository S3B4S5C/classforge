# Persona A — trazabilidad, continuidad y MongoDB

## Alcance entregado

| Requisito | Implementación |
| --- | --- |
| RF25 — actividad y autoría | Operaciones y guardados completos con actor autenticado, origen, revisiones, descripción y payload versionado |
| RF26 — sesiones | Sesiones y participantes persistentes, cierre explícito/por inactividad y resumen determinista |
| RF27 — contexto | Historial filtrable, agregados, detalle de sesiones y novedades desde la última revisión vista |
| RF30 — notificaciones | Avisos persistentes para otros miembros, contador de no leídas, lectura individual/todas y realtime privado |
| Responsive | Activity Center, continuidad y notificaciones utilizables en escritorio, tablet y teléfono |
| NoSQL | MongoDB para usuarios, proyectos, membresías, invitaciones, chat IA, actividad, sesiones, visitas y notificaciones |

Los módulos de Persona A no consultan al LLM ni dependen del chat de Persona B. La integración
con el chat permanece funcional y usa también MongoDB. Se conservan el planner, preview y Apply.

## Elección de base de datos

Se eligió MongoDB por adaptación al código existente: el modelo UML ya es un documento y las
consultas de actividad combinan proyecto, autor, fechas, revisiones y sesión. Spring Data MongoDB
permite mantener la estructura de servicios/repositorios y reemplazar las consultas SQL por
consultas y agregaciones nativas. El UML se almacena como documento BSON anidado.

DynamoDB también sería válido, pero en esta aplicación implicaría rediseñar claves, índices y
patrones de consulta y dividir las instantáneas que excedan 400 KB. MongoDB permite documentos
de hasta 16 MiB. Se mantuvieron las instantáneas fuera del documento del proyecto, en registros
de actividad independientes, para que el historial pueda crecer sin un array ilimitado.

Fuentes: [límites MongoDB](https://www.mongodb.com/docs/manual/reference/limits/),
[límites DynamoDB](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Constraints.html),
[transacciones Spring Data MongoDB](https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html).

MongoDB standalone no soporta las transacciones necesarias: se utiliza un replica set, incluso
en desarrollo. El backend verifica ese requisito al arrancar y crea las colecciones e índices antes
de las primeras transacciones. En AWS se configura un replica set administrado o MongoDB Atlas.

## Arranque

Requisitos: Docker, Java 21 o superior compatible con el proyecto y Node compatible con Angular.

```powershell
# Desde la raíz
docker compose -f compose.mongo.yml up -d --wait
.\backend\gradlew.bat -p backend bootRun

# En otra consola
cd frontend
npm ci
npm start
```

El backend utiliza `mongodb://localhost:27017/classforge?replicaSet=rs0`. La URI se cambia con
`CLASSFORGE_MONGODB_URI`; puede contener credenciales de Atlas y debe mantenerse fuera del repositorio.
MongoDB conserva sus datos en el volumen Docker `classforge_classforge_mongo_data`. La publicación
local del puerto está limitada a `127.0.0.1`. `docker compose down` conserva el volumen.

La demo usa la base independiente `classforge_demo`, los usuarios del fixture existente y el perfil
`demo`. Al iniciar ese perfil se reconstruyen únicamente las filas/documentos de su fixture;
el perfil normal conserva los proyectos habituales. `scripts/demo-reset.ps1` reinicia exclusivamente
la base demo local y exige detener previamente el backend.

## Migrar una base H2 existente

Detener el backend anterior antes de importar. El archivo H2 se lee en modo read-only, se crea
una copia `.before-mongo.bak` y se verifica que sus bytes no hayan cambiado durante la lectura.
No se elimina ni modifica la base fuente. H2 se carga únicamente en la tarea offline de migración
y en sus pruebas; no forma parte del JAR de la aplicación.

```powershell
docker compose -f compose.mongo.yml up -d --wait
.\backend\gradlew.bat -p backend migrateLegacyDatabase `
  "-PlegacySource=F:/ruta/classforge.mv.db" `
  "-PmongoDatabase=classforge"
```

Se importan las tablas existentes de usuarios, proyectos, membresías, invitaciones, chat, actividad,
sesiones y participantes. Las tablas que no existían en una versión anterior se omiten. Se preservan
UUID, revisiones, hashes de contraseña, mensajes, secuencias, fechas, sesiones e instantáneas.
El formato UML vacío de CU-01 se convierte mediante el adaptador existente; un formato que no pueda
reconstruirse fielmente provoca un error antes de importar datos.

La importación completa usa una transacción MongoDB. Cada documento se vuelve a leer y comparar
antes de confirmar. Un ID ya existente en un destino no importado provoca rollback, sin sobrescribir
datos. Un marcador por SHA-256 permite repetir una importación completada sin duplicar registros
ni revertir cambios posteriores hechos en MongoDB. La secuencia de chat continúa después del
mayor valor importado.

## Concurrencia y durabilidad

- Las escrituras de un proyecto, cierres y confirmaciones de visita realizan una escritura de
  coordinación sobre ese proyecto. Los conflictos reales de MongoDB reintentan la transacción
  exterior; una revisión base obsoleta sigue siendo rechazada por el contrato existente.
- Documento UML, actividad, sesión, participantes y notificaciones se confirman o revierten juntos.
- Los reintentos transitorios no incluyen llamadas al modelo: esas llamadas siguen fuera de las
  transacciones de chat. Los tokens de generación y claves únicas conservan una sola respuesta.
- Índices únicos protegen operación/revisión, membresías, solicitud/rol de chat, secuencias,
  participantes, visitas y notificaciones. Un índice parcial permite una sola sesión OPEN por proyecto.
- Los avisos STOMP se publican después del commit. Si el broker falla, la notificación permanece
  disponible por HTTP; el cliente reconcilia al conectar y periódicamente.
- Los instantes se almacenan como cadenas UTC de ancho fijo con nueve decimales. Conservan
  precisión, orden cronológico e identidad de timestamps entre HTTP, realtime y recargas.
- El historial anterior a la instalación de la auditoría no se inventa. Se conserva la revisión del
  proyecto y se registran los cambios aceptados desde que existe el mecanismo.

## Última visita

```text
GET  /api/projects/{projectId}/since-last-visit?page=0&size=20
POST /api/projects/{projectId}/visits
     {"revision":42}
```

La consulta devuelve primera visita, última fecha/revisión vista, revisión actual, número total de
actividades nuevas, página de actividades y participantes agregados. Leer el endpoint no consume
novedades. El botón «Marcar esta revisión como vista» confirma la revisión de la instantánea que
el usuario está consultando. El backend impide revisiones futuras y retrocesos; una actividad que
llegue después de leer continúa apareciendo como nueva.

Las visitas son independientes por usuario/proyecto. La tarjeta aparece en el workspace, en la
vista informativa del asistente y en el Activity Center.

## Notificaciones

```text
GET  /api/notifications?page=0&size=20&unreadOnly=false&projectId={opcional}
GET  /api/notifications/unread-count
POST /api/notifications/{id}/read
POST /api/notifications/read-all?projectId={opcional}
STOMP /user/queue/notifications
```

Una actividad avisa al propietario y editores, excluyendo al autor. Un cierre explícito avisa a los
demás miembros; un cierre automático informa a todos. Se conserva el vínculo con actividad y sesión.
El contenido solo se consulta o marca para el destinatario autenticado. Adivinar el UUID de una
notificación no permite leerla o marcarla. Los clientes no pueden publicar en el canal de avisos
ni suscribirse a una ruta privada con el ID de otra persona.

La campana global muestra el contador, historial paginado, fecha y estado leído. Permite abrir la
actividad o sesión de origen y marcar una/todas como leídas. Otro navegador del mismo usuario
recibe la actualización del estado de lectura.

## Activity Center

Ruta: `/projects/{id}/activity`. Tiene acceso desde proyectos, workspace y asistente móvil.

- Línea de actividad con autor, descripción, fecha, origen y revisiones.
- Filtros combinables por integrante, operación, fechas, revisiones y sesión.
- Paginación de actividad mediante límite exclusivo de revisión para conservar continuidad al
  llegar cambios nuevos; las respuestas de un proyecto/filtro anterior se descartan.
- Sesiones paginadas con estado, participantes, resumen, detalle y cierre explícito.
- Detalle de actividad y payload de auditoría desplegable.
- Tarjeta de continuidad y acceso a notificaciones en todas las pantallas.

En teléfono se usa una columna, controles que se adaptan al ancho disponible y panel de avisos
ajustado al viewport. Esta pantalla no monta JointJS.

## Pruebas y evidencia

```powershell
docker compose -f compose.mongo.yml up -d --wait
.\backend\gradlew.bat -p backend test bootJar
cd frontend
npm run test:characterization
npm run build
```

Las pruebas de integración usan bases MongoDB aleatorias aisladas en un replica set real; se
eliminan únicamente esas bases al terminar. No hay fallback de las pruebas a H2 ni emulación de
transacciones. H2 solo se utiliza para crear/verificar fixtures del importador legado.

La cobertura nueva incluye novedades sin consumo, confirmaciones monotónicas, cambios concurrentes,
aislamiento de visitas, generación/lectura de notificaciones, rollback, fallo de broker, dos clientes
STOMP reales, controles de suscripción, reinicios de aplicación y migración completa/idempotente.
Las pruebas existentes de autenticación, proyectos, colaboración, XMI y chat se ejecutan en MongoDB.

Los resultados y capturas de la aceptación final se registran en `docs/evidence/parcial2/`.

### Resultado final — 8 de noviembre de 2026

- Backend completo: **456 casos, 446 aprobados, 10 omitidos opt-in, 0 fallos y 0 errores**,
  distribuidos en 128 suites. `test bootJar` terminó correctamente con MongoDB real.
- Frontend: **46 pruebas aprobadas, 0 fallos**; `npm run build` correcto.
- El JAR de ejecución no incluye H2, Hibernate, Spring Data JPA ni el driver PostgreSQL.
- Se importó la base local existente: **2 usuarios y 3 proyectos**. La repetición fue idempotente;
  el archivo fuente y su copia de seguridad se conservan. Tras reiniciar MongoDB permanecen
  los mismos registros y el marcador de importación.
- Dos usuarios reales demostraron autoría, filtros, llegada privada de avisos sin recarga,
  exclusión del autor, lectura individual/todas, cierre de sesión y confirmación de última visita.
- Las pantallas de 390 × 844, 768 × 1024 y 1280 × 900 no presentaron desbordamiento horizontal.

Las diez pruebas opt-in pertenecen a aceptación de proveedores IA y ejecución de aplicaciones
generadas. Conservan sus condiciones de habilitación; no se contabilizan como aprobadas.
No se ejecutó un despliegue AWS ni se llamó a proveedores IA externos para esta aceptación.

Informe reproducible y capturas: [aceptación final](../evidence/parcial2/acceptance.md).
