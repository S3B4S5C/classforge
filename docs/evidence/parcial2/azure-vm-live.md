# Azure VM publicada — evidencia

Fecha: 10 de noviembre de 2026. Código desplegado: `a08f9e7`, rama `tiago`.

URL pública: https://classforge-tiago-15124.eastus.cloudapp.azure.com

## Servicios instalados

- VM `classforge-vm`, Ubuntu 24.04.5 LTS x64, Standard_D2als_v7, East US.
  El tamaño B2als_v2 propuesto no estaba disponible en esta suscripción/región.
- Java 21.0.13, nginx 1.24.0 y Certbot 2.9.0. JAR completo con Angular,
  perfiles `azure,azure-vm`, sin perfil demo.
- MongoDB Atlas Free M0 en Azure Virginia-East2, MongoDB 8.0.37; base `classforge`.
  Usuario de aplicación limitado a `readWrite` en esa base. Acceso de red limitado
  a la IPv4 pública de la VM /32.
- Groq como único proveedor de IA; configuración privada instalada en
  `/etc/classforge/classforge.env`, permisos 0600, propietario root.
- `classforge`, nginx y `certbot.timer` activos. Servicio ClassForge habilitado
  para el arranque, con reinicio ante fallo. No se reinició la VM para probarlo.
- nginx publica 80/443. Java escucha solo en 127.0.0.1:8082; Mongo no está en la VM.
  NSG con HTTP/HTTPS públicos y SSH limitado a la IP administrativa.
- DNS público de Azure y certificado Let's Encrypt válido hasta el 8 de febrero de 2027.
  Renovación automática configurada; `certbot renew --dry-run` terminó con éxito.

## Validaciones remotas ejecutadas

- Transferencia SSH/SCP y SHA256 del bundle/JAR verificados en la VM.
- `nginx -t` correcto; estado systemd activo y permisos del archivo env verificados.
- Smoke público PowerShell aprobado: health 200/UP, rutas profundas Angular,
  API protegida 401 y handshake WebSocket por WSS. Validación TLS normal, sin bypass.
- Registro de dos cuentas sintéticas, creación de proyecto, invitación y aceptación.
  Inicio de sesión desde Brave en el sitio público y apertura del editor.
- Disponibilidad autenticada de texto, voz e imagen confirmada desde la VM.
- Llamadas reales a Groq: ASK, EDIT con propuesta, imagen a UML (`READY`) y voz.
  Cada solicitud conservó documento y revisión antes de Aplicar.
- Aplicar desde el navegador creó `DesplieguePrueba`, guardó revisión 1 y mostró
  `Guardado`/`Sincronizado`. El segundo usuario pudo leer esa revisión desde Atlas.
- Segundo usuario conectado mediante STOMP autenticado sobre WSS; snapshot de
  presencia con al menos dos participantes simultáneos.

Las cuentas y el proyecto de comprobación se conservan separados de los proyectos
del usuario. No se enviaron correos externos. Cada usuario puede registrarse en la web.

La suite previa de 512 backend y 46 frontend aprobados corresponde al código de
la preparación. En esta fase se hicieron pruebas de despliegue y llamadas reales;
no se volvió a ejecutar la suite completa ni se cambió Java/Angular.

## Artefacto y operación

Bundle: 165899972 bytes, SHA256:

```text
537b1d273911e9ad6e73008faf5c3f1b761641767af2be287a09f6437a85a587
```

JAR interno, SHA256:

```text
ab849ea3619982cea415bd4ee19c13faeac0723eeeba687d270be5d93f2f809a
```

La clave SSH, variables locales, credenciales y resultados privados se excluyen de
Git y del bundle. El workflow de GitHub solo compila/prueba/empaqueta; esta
publicación se realizó por SSH y no se configuró despliegue automático.

El mirror HTTP de Ubuntu en Azure no respondió durante apt. Se respaldó
`ubuntu.sources` y se usó `https://mirrors.edge.kernel.org/ubuntu/`, conservando
la validación de firmas Ubuntu y TLS. Las instalaciones finalizaron correctamente.

Pendiente del administrador: reemplazar la contraseña débil elegida para el usuario
Atlas y actualizar el env privado en la VM. Groq Free conserva sus cuotas; estas
pruebas no son una garantía de capacidad o disponibilidad ilimitadas. No se
configuraron backups adicionales ni alta disponibilidad de la aplicación.

Operación y actualización: [guía VM](../../runtime/azure-vm-deployment.md).
