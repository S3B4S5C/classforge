# Azure VM — ajuste de destino

El usuario eligió explícitamente una VM Azure en lugar de App Service.
La preparación vigente será Ubuntu24.04 LTS, Java21, nginx, HTTPS/WSS y systemd.
Se conserva el JAR con Angular y Groq, una instancia para el broker STOMP.
El usuario confirmó MongoDB Atlas como base de datos;
el backend acepta otro replica set mediante URI. No crear recursos ni conectarse
a una VM en esta tarea: aún faltan suscripción, IP administrativa y SSH.

## Implementación

- Bicep VM/NIC/VNet/NSG/IP estática con FQDN. SSH por clave, acceso22 solo desde
  IP administrativa /32;80/443 públicos, sin8082 ni27017. IP pública asociada a NIC
  da salida explícita incluso con subnet privada sin default outbound.
- Perfil `azure,azure-vm`: hosting SPA ya probado, backend127.0.0.1:8082,
  headers reenviados por nginx, JWT/Groq/Mongo por archivo root0600 fuera del bundle.
- Bundle JAR+SHA+installer+systemd+nginx+env de ejemplo. Instalador dedicado
  para VM nueva, preserva secretos y configuración TLS existente. No reinicia datos.
- Bootstrap HTTP sirve ACME y503; no expone login sin TLS. Certbot webroot obtiene
  certificado y activa proxy HTTPS con renovación y reload automático.
- Releases por hash con symlink current, rollback conservando versión anterior.
- Workflow manual solo build/tests/bundle. App Service permanece documentado como
  alternativa separada, sin publicación automática ni credenciales nuevas.

## Validación

Compilar Bicep, parsear PowerShell/Bash, probar rechazo de parámetros incompletos
y verificación SHA, revisar nginx/systemd en Linux si está disponible. Probar JAR
con ambos perfiles y smoke HTTP/WS usando Mongo aislado. Revisión independiente
y Git push a tiago autorizada. Distinguir pruebas locales de validación Azure/TLS.

La VM, IP, disco y tráfico pueden generar costo. El tamaño inicial propuesto
es Standard_B2als_v2 (revisar disponibilidad/cuota regional); no es un compromiso de
precio ni una creación de recursos. Atlas, DNS y certificado públicos siguen
pendientes hasta conocer los recursos reales.
