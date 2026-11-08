# Persona A — etapa 1: actividad y sesiones persistentes

> Documento de la primera etapa. La persistencia fue migrada después a MongoDB;
> las instrucciones vigentes de almacenamiento y cierre están en [el cierre de Persona A](persona-a-cierre.md).

Esta etapa implementa los puntos 1–4: historial, registro transaccional de cambios aceptados,
consultas estructuradas y sesiones con participantes y resumen. Los módulos nuevos están en
`com.classforge.activity` y `com.classforge.session`; no dependen del asistente conversacional.

## Persistencia y garantías

Cada operación UML aceptada por `ProjectCollaborationService` genera exactamente una fila
`project_activities`, correlacionada por `operationId`, proyecto y revisión. Un BATCH genera una
actividad con los detalles ordenados de sus comandos. También se auditan los guardados completos
de `ProjectService.saveDocument`, incluidos los aplicados por la importación XMI.

El registro conserva UUID, proyecto, autor autenticado y nombre en ese momento, revisiones
anterior/resultante, tipo, origen, descripción, sesión y fecha UTC con precisión de microsegundos.
El payload JSON versionado conserva el comando (si existe), ambas instantáneas completas del
documento y los detalles legibles. Los nombres de elementos eliminados se resuelven desde la
instantánea anterior; los comandos de un lote se describen en su orden real de ejecución.

`ProjectOperationPersistedEvent` se publica después de validar y guardar el documento, dentro de
su transacción. `ProjectActivityRecorder` es un listener síncrono con propagación MANDATORY:
documento, actividad, sesión y participantes se confirman o revierten juntos. Una validación,
conflicto de revisión, denegación de acceso o fallo de auditoría no deja cambios parciales.
Las restricciones únicas `(project_id, operation_id)` y `(project_id, revision_after)` impiden
registros duplicados. Reenviar un identificador con una revisión nueva no representa una operación
nueva válida: se rechaza por la restricción y revierte toda esa escritura.

Los orígenes admitidos son `MANUAL`, `ASSISTANT_TEXT`, `ASSISTANT_VOICE`, `VISION`, `XMI` y
`SYSTEM`. Las solicitudes antiguas sin origen usan `MANUAL`. El cliente envía el origen al aplicar
las propuestas de texto, voz e imagen; XMI lo determina el servidor. Un guardado completo local
con varios orígenes se clasifica como consolidación `SYSTEM`. El origen es metadato descriptivo
y no concede permisos; el autor siempre lo determina el backend.

En `ProjectOperationRequest` se añade `source` opcional. En el guardado HTTP se admiten `source`
y `operationId` opcionales; si falta el ID se genera en el servidor:

```http
PUT /api/projects/{projectId}/document
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "baseRevision": 12,
  "document": {"schemaVersion":"1.0","umlModel":{"classes":[],"relationships":[]},"layout":{"nodes":{}}},
  "source": "MANUAL",
  "operationId": "ffef8fd0-0f5c-46f3-a028-9f1a44a95331"
}
```

Un guardado aceptado sin diferencias también incrementa la revisión, como ya ocurría, y se registra
como `SAVE_DOCUMENT` con cero cambios detallados. Las consultas, validaciones, preguntas IA y
previews no crean actividad. No se inventa historial para revisiones anteriores a esta implementación.

## Consultas de actividad

Todas las rutas requieren JWT y acceso OWNER/EDITOR al proyecto:

```text
GET /api/projects/{projectId}/activities
GET /api/projects/{projectId}/activities/{activityId}
GET /api/projects/{projectId}/activities/summary
```

Filtros combinables de listado y resumen:

| Parámetro | Semántica |
| --- | --- |
| `actorId` | UUID del autor |
| `from`, `to` | Instantes ISO-8601 con zona, ambos inclusivos |
| `afterRevision` | Revisión resultante estrictamente mayor |
| `beforeRevision` | Revisión resultante estrictamente menor |
| `operationType` | Tipo raíz: comandos UML existentes, BATCH o SAVE_DOCUMENT |
| `sessionId` | Actividades de una sesión del mismo proyecto |

`page` comienza en 0 y `size` acepta 1–100 (por defecto 20). El listado devuelve
`{items, page, size, totalElements, totalPages}`, ordenado por revisión descendente.
Los rangos inválidos, tipos desconocidos y parámetros mal formados devuelven 400.
Un proyecto inaccesible o identificador de detalle de otro proyecto devuelve 404.

El resumen consulta agregados en base de datos sobre todos los registros que cumplen los filtros:

```json
{
  "projectId": "...",
  "fromRevision": 12,
  "toRevision": 19,
  "totalChanges": 7,
  "participants": [{"userId":"...", "displayName":"Marco", "changes":7}]
}
```

En este agregado, `totalChanges` y los contadores de participantes cuentan operaciones aceptadas
(filas de actividad). Un BATCH cuenta una vez. Si no hay resultados, las revisiones son null y la
lista de participantes está vacía. Si un autor cambió de nombre, los registros mantienen sus nombres
originales y el agregado agrupa por UUID usando el nombre de su última actividad del intervalo.

Para integración interna, `ProjectActivityQueryService` expone `getRecentActivity`,
`getActivityByActor`, `getSinceRevision`, `findSince`, `findBetween`, `find` y `summarize`.
Todos reciben la identidad del usuario y validan acceso, incluso fuera del controlador HTTP.

## Sesiones de trabajo

La primera actividad abre una sesión. La actividad posterior reutiliza la sesión abierta mientras
no hayan transcurrido 30 minutos de inactividad. Al alcanzar ese límite se cierra la anterior y se
abre una nueva, incluso si ambas actividades ocurren el mismo día. También existe cierre explícito.
Una tarea periódica cierra sesiones inactivas aunque no llegue ninguna otra operación.

La sesión persiste inicio, fin, revisión inicial/final, última actividad, estado OPEN/CLOSED, motivo
de cierre, resumen y contadores. Sus participantes son filas persistentes con UUID, nombre y
cantidades de operaciones/cambios. El resumen determinista se actualiza con cada actividad y al
cierre: participantes, operaciones por tipo y rango de revisiones, sin llamadas al LLM.
`totalOperations` cuenta actividades raíz; `totalChanges` y `operationCounts` cuentan los detalles
de comandos o diferencias reales de un guardado completo. No se duplican las filas de auditoría
para representar los subcomandos de un BATCH.

El bloqueo de la fila del proyecto serializa operaciones, cierres explícitos y mantenimiento.
Una clave única nullable garantiza una sola sesión abierta por proyecto. El cierre es idempotente;
la próxima actividad abre otra sesión. Las consultas usan la revisión inicial para ordenar sesiones,
por lo que siguen siendo correctas aunque dos sesiones compartan exactamente el mismo timestamp.
Al cerrar por inactividad, el fin lógico es `lastActivityAt + inactivityTimeout`; `updatedAt` indica
cuándo se realizó el cierre. El mantenimiento vuelve a comprobar la inactividad bajo bloqueo para
no cerrar una sesión que recibió actividad mientras esperaba.

```text
GET  /api/projects/{projectId}/sessions?status=OPEN&page=0&size=20
GET  /api/projects/{projectId}/sessions/{sessionId}
GET  /api/projects/{projectId}/sessions/current-or-last
POST /api/projects/{projectId}/sessions/{sessionId}/close
GET  /api/projects/{projectId}/activities?sessionId={sessionId}
```

`current-or-last` devuelve 204 cuando aún no existe una sesión; en otro caso, la sesión más reciente
con resumen y participantes. El cierre exige permiso de edición. Los IDs de sesión de otros proyectos
no dan acceso a su información ni permiten cerrarlos. `ProjectSessionClosedEvent` deja disponible
el evento transaccional para la etapa posterior de notificaciones.

| Variable | Predeterminado | Uso |
| --- | --- | --- |
| `CLASSFORGE_SESSION_INACTIVITY_TIMEOUT` | `PT30M` | Duración positiva antes del cierre |
| `CLASSFORGE_SESSION_SWEEP_INTERVAL` | `PT1M` | Intervalo entre barridos |
| `CLASSFORGE_SESSION_MAINTENANCE_ENABLED` | `true` | Activar cierre periódico |

Desactivar el mantenimiento no desactiva el cierre por inactividad cuando llega una nueva operación.
Las tablas e índices se crean con la política JPA `ddl-auto=update` existente. La base normal en archivo
conserva datos; el perfil demo existente recrea sus datos y no sirve para conservar trabajo habitual.

## Verificación

Verificado el 08/11/2026 en la rama `codex/activity-sessions`:

- Backend: 435 casos encontrados, 425 aprobados, 10 omitidos opt-in heredados y cero fallos/errores.
- Se añadieron 30 pruebas backend y se amplió la prueba STOMP existente con comprobaciones de auditoría.
- Frontend: 39 pruebas aprobadas, incluidas 3 nuevas sobre orígenes de cambios.
- Empaquetado `bootJar`, build Angular de producción y `git diff --check` satisfactorios.
- La prueba de persistencia reinició dos veces el contexto completo contra una base H2 en archivo.
- La prueba de mantenimiento ejecutó el cierre mediante el scheduler real, sin una operación posterior.

```powershell
.\backend\gradlew.bat -p backend test
.\backend\gradlew.bat -p backend bootJar
cd frontend
npm run test:characterization
npm run build
```

La cobertura nueva verifica autoría/revisiones/orígenes, reconstrucción de descripciones, lotes,
rollback externo y por fallo de auditoría, guardado completo, XMI, filtros y paginación, agregados,
sesiones/participantes/resúmenes, cierre explícito y automático, concurrencia y aislamiento HTTP.
Una prueba abre la aplicación dos veces contra la misma H2 en archivo para verificar persistencia
tras reinicio. La prueba STOMP existente también comprueba la correlación y el origen persistidos.
Las pruebas del cliente verifican la conservación y el reinicio de los orígenes al guardar/sincronizar.

La siguiente etapa comprende última visita, notificaciones e interfaz Activity Center. Esta etapa
entrega sus contratos backend y no modifica la conversación ni el planner IA existentes.
