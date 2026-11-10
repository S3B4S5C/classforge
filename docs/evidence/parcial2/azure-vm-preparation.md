# Preparación Azure VM — evidencia

Este documento conserva la preparación previa. El despliegue remoto posterior
y sus comprobaciones están en [Azure VM publicada](azure-vm-live.md).

Fecha: 10 de noviembre de 2026. Base96d21e8, rama `tiago`.
El usuario eligió VM Azure. La guía vigente es
[Azure VM](../../runtime/azure-vm-deployment.md).

## Verificación ejecutada

- Bicep VM compiló con CLI0.49.2. SSH usa la IP administrativa /32; únicamente
 22,80,443 tienen reglas de entrada. Java8082 y Mongo27017 no se publican.
  IP Standard estática asociada a NIC proporciona salida explícita.
- Bash5.3.15 parseó los tres scripts. El guard de entorno aceptó configuración
  completa y rechazó clave Groq vacía y origen HTTP. No imprimió valores privados.
- Provisioning PowerShell rechazó clave SSH ausente y un CIDR SSH abierto antes
  de cualquier llamada cloud. Los scripts parsearon sin errores.
- El bundle rechazó el JAR previo, que no incluía el perfil VM. Tras reconstruir,
  Angular y bootJar terminaron correctamente; el ZIP incluyó los nueve archivos
  esperados y el SHA interno del JAR coincidió.
- Se revisaron JAR y bundle: sin archivos privados ni credenciales configuradas.
  Los parámetros y env locales están ignorados por Git.
- nginx1.30.6 para Windows validó ambas configuraciones con `nginx -t` usando
  rutas/puertos aislados y certificado localhost efímero. No se instaló nginx
  globalmente ni se cambió el almacén de certificados del usuario.
- El bootstrap HTTP rechazó `/login` con503 hasta activar TLS en el harness.
- El JAR arrancó con `azure,azure-vm`, Mongo aislado y bind127.0.0.1. Solo el
  puerto de prueba se cambió a18083 para no ocupar el puerto habitual.
- Edge headless comprobó dos usuarios con autenticación, STOMP/presencia sobre
  **WSS a través de nginx**, archivos estáticos, recarga de ruta profunda, móvil
 390x844 sin overflow y cero errores JavaScript. Documento/revisión no cambiaron.
- Workflow YAML parseado: un job, build/tests/bundle manual; sin login ni
  publicación App Service, sin secretos cloud en GitHub.
- Revisión independiente sin hallazgos críticos ni importantes.

El smoke HTTPS usó `https://localhost:18443`, backend18083 y Mongo replica set
27028 con base `cf_azure_vm_smoke_20261110`. Se permitió el certificado efímero
solo dentro del contexto de prueba del navegador. La disponibilidad IA se simuló
en ese navegador; no hubo llamadas Groq en esta prueba de hosting.

No cambió el código Java/Angular funcional respecto a la suite previa de512
backend y46 frontend aprobados; esta entrega añade perfil, scripts y despliegue.
No se repitió la suite completa: se verificaron el nuevo paquete y el proxy.

## Artefactos

`.azure-deploy/classforge-azure-vm.zip`,165899972 bytes:

```text
537b1d273911e9ad6e73008faf5c3f1b761641767af2be287a09f6437a85a587
```

JAR interno:

```text
ab849ea3619982cea415bd4ee19c13faeac0723eeeba687d270be5d93f2f809a
```

Ambos tienen SHA256 acompañante y están excluidos de Git. Los secretos preparados
localmente se conservan fuera del ZIP; faltan URI Mongo y origen público reales.

## Límites

No se creó VM ni se ejecutaron SSH, apt, systemd o Certbot contra una máquina
remota. La comprobación nginx Windows no sustituye la instalación y validación
nginx1.24 de Ubuntu24.04. Certificado público, renovación ACME, reglas de red,
cuota regional, IP pública y conexión Atlas quedan por comprobar en Azure.
El usuario confirmó posteriormente MongoDB Atlas; el instalador no crea Mongo
ni modifica bases habituales. La guía y los valores de tamaño propuestos se
actualizaron a B2als_v2, sin reconstruir el bundle (no contiene infraestructura).
