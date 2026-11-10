# Notificaciones push

ClassForge usa Web Push con VAPID y un service worker HTTPS. El APK 1.1.0 delega
la presentación a Android mediante Android Browser Helper. Conserva el mismo
dominio, cuenta, proyectos y firma que el APK anterior. No requiere crear una
cuenta Firebase ni incluir claves de Groq/Mongo en el teléfono.

## Activar en un dispositivo

1. Instalar el APK actualizado o abrir la web en un navegador compatible.
2. Iniciar sesión, abrir la campana y pulsar **Activar notificaciones push**.
3. Permitir notificaciones cuando Android o el navegador lo solicite.
4. Pulsar **Enviar aviso de prueba**. Se permite una prueba cada 60 segundos.

Los avisos cubren actividad de otros miembros y sesiones de trabajo cerradas.
Las operaciones propias no generan avisos para su autor. El aviso abre la
actividad del proyecto; los detalles siguen requiriendo sesión y acceso al
proyecto. El texto visible en pantalla bloqueada es genérico.

**Desactivar push** afecta al dispositivo actual. Cerrar sesión elimina su
suscripción y limpia la asociación local; cambiar de cuenta bloquea los avisos
de la cuenta anterior antes de consultar el servidor. La campana conserva las
notificaciones persistidas aunque push esté desactivado.

Se necesita conexión y permiso del sistema/navegador. Puede funcionar con la
ventana cerrada, según las políticas de segundo plano del dispositivo. Forzar
la detención del navegador, denegar permisos o restringir su batería puede
impedir la entrega. No es un canal de entrega garantizada ni habilita trabajo
offline. Si el permiso está bloqueado, habilitarlo desde ajustes de Android o
del sitio y volver al panel. Mantener Chrome actualizado para usar el APK.

## Configuración del servidor

Generar el par solo una vez, en una ubicación privada fuera de Git:

```powershell
node scripts/push-create-vapid.mjs C:/ruta-privada/vapid.local.env
```

Copiar las cuatro variables al entorno privado de la aplicación en la VM:

```dotenv
CLASSFORGE_PUSH_ENABLED=true
CLASSFORGE_PUSH_VAPID_PUBLIC_KEY=REEMPLAZAR
CLASSFORGE_PUSH_VAPID_PRIVATE_KEY=REEMPLAZAR
CLASSFORGE_PUSH_VAPID_SUBJECT=https://classforge-tiago-15124.eastus.cloudapp.azure.com
```

El archivo `/etc/classforge/classforge.env` debe pertenecer a root y tener
permisos 0600. Conservar las variables Mongo/Groq/JWT existentes. Reiniciar
`classforge` tras el cambio. Sin configuración completa, dejar push desactivado.
El arranque rechaza un par VAPID inválido. Respaldarlo en privado: rotarlo obliga
a renovar las suscripciones de los dispositivos existentes. Solo la clave
pública se entrega a usuarios autenticados; la privada permanece en el servidor.

No abrir puertos adicionales de entrada: se usan 443 y las conexiones salientes
HTTPS a los proveedores de push. Los endpoints aceptados son FCM, Mozilla y
Apple; se rechazan destinos ajenos, URLs locales y redirecciones HTTP.

## Persistencia y operación

- `push_subscriptions`: suscripciones propias, máximo 10 por usuario, claves del
  dispositivo y generación de suscripción. No imprimirlas en logs.
- `push_quotas`: contador transaccional de dispositivos por usuario.
- `push_deliveries`: outbox en la misma transacción que la notificación.
  El worker procesa fuera de la transacción de edición, en su propio ejecutor.
- Lease 30 segundos, máximo 5 intentos, espera progresiva y `Retry-After` numérico.
  Los mensajes caducan a las 24 horas; TTL del proveedor 1 hora. La limpieza TTL de
  Mongo es eventual. 404/410 elimina la suscripción; 429/5xx permite reintento.
- Antes de enviar, se vuelve a comprobar la suscripción, usuario y acceso al
  proyecto. No se envían avisos de proyectos cuyo acceso se perdió.

El service worker solo guarda el identificador de cuenta en IndexedDB; no
almacena JWT, HTML autenticado ni respuestas de API. No hay caché offline ni
interceptor de peticiones en este worker. La API `/api/push/*` requiere JWT.

## Verificación de cambios

```powershell
cd frontend
npm run test:characterization
npm run build
cd ../backend
./gradlew.bat test --no-daemon --max-workers=1
```

El backend necesita Mongo en replica set; usar una instancia aislada para
pruebas. Las pruebas verifican validación/SSRF, claves, propiedad, cuotas,
rollback/deduplicación, reintentos, bajas, pérdida de acceso y firma/cifrado VAPID.
Las del frontend incluyen solapamiento real de eventos del worker, logout,
cambio de cuenta durante permisos y limpieza aunque falle la red.

Para una prueba real: activar un dispositivo del propietario, dejar la app en
segundo plano y editar el proyecto compartido con otra cuenta. Verificar el
aviso, pulsarlo y comprobar el proyecto. Desactivar push y repetir la operación:
la campana debe seguir recibiendo la actividad, sin nuevo aviso del dispositivo.

La evidencia de publicación se registra en
[push Android](../evidence/parcial2/push-notifications.md).
