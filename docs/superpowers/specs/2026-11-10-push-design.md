# Notificaciones push para ClassForge web/TWA

Solicitud: añadir push al APK conectado aprobado. Se conserva la preferencia del
usuario de delegar implementación y continuar sin nuevas preguntas de diseño.

## Alcance y decisiones

Web Push estándar/VAPID para los eventos existentes de la campana: actividad de
otros miembros y sesiones cerradas. No se añaden chat, invitaciones, campañas ni
eventos distintos. Sirve web y TWA, aun con sus páginas cerradas cuando navegador/
sistema permiten entrega en segundo plano. Sin cuenta Firebase adicional. No se
garantiza entrega si usuario fuerza detención de Chrome/app, bloquea permisos,
carece de red o el sistema suspende servicios.

Alternativas: FCM nativo exigiría proyecto/credenciales Google y otro cliente
independiente; avisos por WebSocket requieren página activa. Web Push aprovecha
la arquitectura TWA elegida y comparte backend/permisos de usuario.

Usuario activa explícitamente desde campana, acepta permiso y puede desactivar
o enviar prueba a este dispositivo. No solicitar permiso automáticamente. Aviso
genérico sin nombres/modelo; click abre actividad autenticada. Logout/desactivar
borra vínculo servidor y anula suscripción navegador; SW filtra por usuario local
y cierra avisos antiguos al desvincular. Cambiar de cuenta no hereda avisos.

## Arquitectura

- API autenticada `/api/push`: configuración pública VAPID, alta/baja/status de
  suscripción propia y prueba limitada a un aviso/minuto por dispositivo.
- Endpoint HTTPS solamente de proveedores conocidos (Chrome FCM, Mozilla y Apple),
  sin userinfo/fragment/puertos arbitrarios/redirecciones. Validar claves P256/auth
  y longitud; máximo10 suscripciones por usuario; endpoint único sin reasignación
  entre usuarios. No devolver ni registrar endpoints/claves privados.
- Suscripciones Mongo y outbox creados en la transacción de la notificación.
  Trabajador posterior con lease, cinco intentos, backoff y TTL24h; nunca red dentro
  de la operación UML. Elimina suscripciones404/410, reintenta429/5xx/transporte;
  errores definitivos no bloquean el sistema ni la campana. Revisa acceso actual
  al proyecto antes de enviar. IDs de outbox deterministas evitan duplicados.
- Biblioteca Java `nl.martijndwars:web-push:5.1.2` para cifrado AES128GCM/VAPID;
  transporte HTTP Java con timeouts, sin redirects. VAPID generado localmente,
  privado solo en env root0600 VM y respaldo local ignorado. Config por defecto
  desactivada; habilitar producción tras configurar las claves.
- SW específico sin caching de API/HTML/JWT; eventos push/click y vínculo usuario
  IndexedDB. Icono CF y URLs internas validadas. UI soporta denegación, falta de
  soporte, fallo de red, reintento y estados de suscripción del dispositivo.
- TWA1.1.0/código2: POST_NOTIFICATIONS, servicio oficial DelegationService con
  icono blanco y NotificationPermissionRequestActivity. Conservar firma/paquete/
  dominio. Click vuelve al proyecto; Android13+ requiere permiso.

## Entrega y verificación

Tests Java de límites, ownership, transacciones/outbox, errores/reintentos y
revocación; tests frontend/SW de payload/URL/account switch. Compilar web/backend
y APK/lint/firma. Probar suscripción y entrega real, con página cerrada, click y
baja; documentar cualquier dependencia del dispositivo. Desplegar Azure, verificar
API/HTTPS/WSS/DAL anteriores y APK descargable, publicar código en `tiago`.
