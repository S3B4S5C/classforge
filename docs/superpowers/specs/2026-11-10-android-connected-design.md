# APK ClassForge conectado a Azure

El usuario eligió «APK conectado a la versión web; decidí la implementación y
continuá» el 10 de noviembre de 2026. Se ejecuta sin nuevas preguntas de diseño.

## Resultado

APK Android firmado e instalable, nombre ClassForge, paquete
`com.classforge.app`, versión1.0.0/código1. Abre `/projects` en
`https://classforge-tiago-15124.eastus.cloudapp.azure.com` mediante Trusted Web
Activity (TWA). Android7/API24 mínimo; target/compile36. Icono con la marca CF.
La interfaz y todas las operaciones siguen siendo las del sistema desplegado.

Se mantienen registro/login, proyectos, colaboradores/invitaciones, actividad,
notificaciones, canvas UML, undo/redo, persistencia, IA de texto/voz/imagen con
preview/Apply, XMI y generación/descarga de proyectos. La app y la web comparten
Atlas, API, revisión de documentos y colaboración WSS. No contiene claves de
Groq, MongoDB, JWT, SSH ni cuentas preconfiguradas.

Requiere Internet y navegador Android compatible (Chrome actualizado recomendado).
El navegador gestiona permisos de micrófono, selector de archivos y descargas;
la TWA comparte el almacenamiento/sesión del dominio con ese navegador. Los
cambios de la web aparecen al recargar la app, sin recompilar el APK. La firma,
el paquete o la URL fija sí requieren una versión nueva. No se promete offline,
notificaciones push nuevas, iOS ni publicación en Play Store.

## Enfoques

Elegido: TWA con Android Browser Helper oficial. Conserva el sistema real y las
capacidades del navegador; necesita asociación verificable entre APK y dominio.
Una app Flutter independiente duplicaría editor/cliente y su mantenimiento. Un
WebView exigiría puentes propios para grabación, descargas blob y archivos. Se
descartan ambos para el alcance conectado que aprobó el usuario.

## Implementación

- Módulo independiente `android/`, Gradle8.13, AGP8.13.2, Android Browser Helper
  2.7.4, Java17 de lenguaje y JDK21 para compilar. Dependencias fijadas.
- Launcher oficial, solo origen HTTPS exacto en metadatos y app links; fallback
  Custom Tab/navegador para equipos sin TWA, sin WebView con puentes JavaScript.
  Sin HTTP en claro, sin permisos de almacenamiento amplio, cámara o contactos.
- Firma release generada y conservada en `.android-release/` ignorado por Git.
  Una clave estable permite instalar futuras actualizaciones encima. Contraseñas
  via variables de entorno de proceso; no se incluyen en el código ni el APK.
- Archivo público `/.well-known/assetlinks.json` declara exclusivamente el paquete
  y SHA256 de la firma release. Debe dar200 JSON sin redirección/autenticación.
- nginx sirve asociación y APK desde un directorio dedicado de archivos públicos.
  Script de instalación valida entradas, respaldo/nginx -t y reload. No cambia
  env privado, JAR, base de datos ni reglas de autenticación API.
- Scripts reproducibles para firma, build/verificación de APK y publicación de
  archivos Android. Se entregan APK local + checksum y enlace HTTPS descargable.

## Verificación y límites

Tests de generación/validación DAL rechazan huellas/paquetes inválidos; asociación
de salida consistente con firma real. Compilar release, lint, verificar firma con
apksigner y paquete/SDK con aapt. Escanear artefactos publicados por secretos.
Probar health/API privada/WSS después del reload nginx; comprobar DAL desde fuera.
En emulador Android con Chrome: instalar APK, validar origen sin barra de URL,
login y proyecto sincronizado, interacción UML y navegación, selección de imagen,
descarga XMI/ZIP y permiso de voz. Usar únicamente cuentas/proyecto de prueba.
Registrar claramente cualquier función no comprobable en un emulador (micrófono
físico y fidelidad táctil) y no confundir éxito de compilación con validación de UI.

## Criterios de entrega

APK firmado verificable, dominio asociado con esa firma, servidor saludable,
conexiones a la instancia existente sin introducir secretos, código/docs en `tiago`
y guía para instalar, actualizar y conservar la firma. Publicación automática de
cambios de código/GitHub y acceso a portales cloud no forman parte del APK.
