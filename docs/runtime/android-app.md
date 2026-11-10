# ClassForge para Android

La aplicación Android es un cliente conectado mediante Trusted Web Activity.
Muestra el mismo ClassForge publicado en Azure, con sus funciones actuales y
conexiones HTTPS/WSS, sin duplicar backend, base o credenciales.

## Instalar y entrar

1. Descargar [classforge.apk](https://classforge-tiago-15124.eastus.cloudapp.azure.com/android/classforge.apk)
   desde el teléfono Android7 o posterior. Tener Chrome actualizado o un navegador
   compatible con TWA. El APK se instala directamente; no se publicó en Play Store.
2. Abrir el archivo y seguir el instalador de Android. Si solicita permiso para
   instalar desde el navegador/gestor de archivos, el propietario del dispositivo
   debe decidir si habilitar esa fuente para esta instalación.
3. Abrir **ClassForge**, iniciar sesión con la misma cuenta de la web o registrarse.
4. Abrir un proyecto propio/compartido. Los cambios se guardan en Atlas y se
   sincronizan con los demás miembros por WSS.

El editor completo, XMI, generación de proyectos, IA de texto/voz/imagen,
invitaciones, actividad y notificaciones son los del sistema web. La vista
**Asistente móvil** ofrece conversación compacta; aplicar propuestas y editar
el diagrama se hace desde el workspace. En pantalla pequeña se puede desplazar
la página y usar orientación horizontal para tener más espacio.

El navegador gestiona el micrófono, la selección de archivos y las descargas.
Para voz hay que conceder el permiso solicitado al navegador/sitio. No se exige
almacenamiento amplio, contactos, acceso SSH ni claves de Groq/Mongo en el móvil.
Las exportaciones XMI/ZIP aparecen en las descargas del dispositivo/navegador.

Desde la versión1.1.0, la campana permite activar notificaciones push de actividad
y sesiones cerradas. Android solicita permiso para **ClassForge** al activarlas.
Instalar la actualización sobre el APK anterior conserva la firma y los datos.
Ver [activación y operación de push](push-notifications.md).

Requiere Internet; no es una app de trabajo offline. La sesión y almacenamiento
del dominio se comparten con el navegador que proporciona TWA. Los cambios de la
web aparecen al recargar/abrir la app. Paquete, firma o dominio nuevos requieren
un APK nuevo. Si falla la verificación de dominio, se muestra una Custom Tab
con barra del navegador; no se omite la validación TLS para ocultarla.

## Compilar una actualización

Requisitos: PowerShell7 en Windows, Node20+, JDK21, Android SDK con
`platforms;android-36`, `build-tools;35.0.0` y platform-tools. El wrapper descarga
Gradle8.13 con SHA256 fijado. AGP8.13.2 y Android Browser Helper2.7.4 están fijados.
No se necesita Flutter para este cliente ClassForge; la generación Flutter de
los proyectos exportados es otra función y se conserva.

Crear la firma solo una vez:

```powershell
.\scripts\android-create-signing.ps1 -JavaHome C:/ruta/jdk-21
```

Respaldar juntos `.android-release/classforge-release.jks` y
`.android-release/signing.local.json` en un lugar privado. No publicarlos. Perder
esta clave impide actualizar la misma instalación con otra firma. El APK solo
contiene el certificado público, nunca la clave ni las credenciales del servidor.

Para versiones siguientes, incrementar `versionCode` y `versionName` en
`android/app/build.gradle`; los metadatos se leen del APK compilado.

```powershell
.\scripts\android-build.ps1 -AndroidSdk C:/ruta/Android/Sdk -JavaHome C:/ruta/jdk-21
```

El script ejecuta tests del contrato de release, assembleRelease y lintRelease;
verifica firma con apksigner y paquete/SDK con aapt. Produce en `.android-release/`:

- `classforge.apk` y `classforge.apk.sha256`.
- `assetlinks.json`, asociado a la firma real del APK.
- `release.json`, metadatos públicos sin secretos.

## Publicar en la VM existente

SSH debe estar habilitado para la IP administrativa actual /32. Conectar primero
y comprobar la huella de la VM. Luego:

```powershell
.\scripts\android-publish.ps1 -VmIp 172.190.151.24 `
  -KeyFile C:/ruta/classforge-tiago-key.pem `
  -AndroidSdk C:/ruta/Android/Sdk -JavaHome C:/ruta/jdk-21 -CheckOnly
.\scripts\android-publish.ps1 -VmIp 172.190.151.24 `
  -KeyFile C:/ruta/classforge-tiago-key.pem `
  -AndroidSdk C:/ruta/Android/Sdk -JavaHome C:/ruta/jdk-21
```

Se puede indicar `-KnownHostsFile` para un archivo SSH dedicado. El script usa
StrictHostKeyChecking=yes y solo copia los cuatro archivos públicos y el
instalador; no transfiere el directorio de firma completo. Comprueba checksum,
firma y DAL antes de SSH; `-CheckOnly` termina antes de conectar.

El instalador de la VM guarda versiones en
`/var/lib/classforge/android/releases/<sha256>` y cambia el enlace `current`.
Respalda la configuración nginx, instala un snippet de cuatro rutas exactas,
ejecuta `nginx -t` y recarga. Si falla, restaura configuración/enlace anterior.
No reinicia Java ni cambia env, Atlas o la API. Las rutas son:

- `/.well-known/assetlinks.json`, JSON público200 sin redirección/autenticación.
- `/android/classforge.apk`, APK descargable.
- `/android/classforge.apk.sha256` y `/android/release.json`.

El cliente vuelve a descargar DAL/APK y compara firma/checksum, y ejecuta el
smoke público de Angular/API/WSS. Después probar instalación Android. El botón
Actualizar de Play Store no aplica: instalar el APK nuevo firmado con la misma
clave sobre la instalación anterior.

## Verificación

Tests locales del contrato: `node --test scripts/android-release.test.mjs`.
Prueba instrumentada del arranque release: `:app:connectedReleaseAndroidTest`,
con las mismas variables de firma que usa `android-build.ps1` y un dispositivo
de prueba con Chrome. No sustituye las pruebas de UI completa ni un micrófono real.

Evidencia de esta entrega: [Android conectado](../evidence/parcial2/android-connected.md).

Referencias oficiales: [TWA](https://developer.android.com/develop/ui/views/layout/webapps/trusted-web-activities),
[Android Browser Helper](https://github.com/GoogleChrome/android-browser-helper),
[AGP8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
