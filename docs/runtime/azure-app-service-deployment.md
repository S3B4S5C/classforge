# Desplegar ClassForge en Azure App Service

> Alternativa histórica. El10nov2026 el usuario eligió una VM Azure.
> Seguir [la guía VM vigente](azure-vm-deployment.md). El workflow actual solo
> construye el bundle VM; la sección Actions de este documento describe la
> preparación previa, no el workflow vigente.

Preparación: 9 de noviembre de 2026. Código integrado en la rama `tiago`.
Arquitectura preparada: App Service **Linux / Java SE 21 / Basic B1**, una
instancia, Angular dentro del JAR, MongoDB Atlas y Groq. Sin Docker ni nginx en
App Service. No se han creado recursos ni publicado en una suscripción Azure.

## Costo y requisitos

App Service Basic es de pago, incluido cuando se detiene la app pero se conserva
el plan. Consultar el precio para región/suscripción y el saldo Azure for Students
antes de ejecutar Create. Atlas Free y Groq Free tienen límites; no garantizan
capacidad ilimitada. El modelo de visión Groq es preview.

Se necesitan Azure CLI **2.48.1 o posterior** autenticada (`az login` y `az account set --subscription
<id>`), permisos para App Service en el grupo elegido, JDK21, Node24 compatible
con Angular22 y PowerShell. Para las pruebas locales hace falta MongoDB replica
set. Los scripts de preparación no requieren credenciales Azure.

## 1. MongoDB Atlas

Crear un cluster Atlas (puede ser Free con proveedor Azure en una región admitida)
y un usuario de base de datos con `readWrite` sobre **classforge**. No usar la cuenta
de acceso al portal como usuario Mongo. Copiar la URI del driver Java y añadir
la base explícita:

```text
mongodb+srv://USUARIO:CLAVE_CODIFICADA@CLUSTER/classforge?retryWrites=true&w=majority
```

Codificar los caracteres reservados de usuario/contraseña. Usar el hostname real
de Atlas y mantener TLS habilitado. La aplicación comprueba replica set/mongos,
crea sus colecciones/índices y usa transacciones. No se ha probado una sustitución
por Azure Cosmos DB; este runbook prepara MongoDB Atlas.

La allowlist de Atlas debe incluir las IP de salida de App Service. Se obtienen
después de crear el recurso (paso 3). No hace falta permitir todo Internet.
Revisar la allowlist después de cambiar el plan o recrear recursos; Azure puede
cambiar esas IP. La base de la nube no recibe automáticamente los proyectos de
la base local. Si se necesitan, planificar una migración Mongo separada.

## 2. Parámetros privados

Desde la raíz:

```powershell
if (-not (Test-Path deploy/azure/parameters.local.json)) {
    Copy-Item deploy/azure/parameters.example.json deploy/azure/parameters.local.json
}
```

Editar **solo parameters.local.json**, que está ignorado por Git:

- `appName`: nombre globalmente único, por ejemplo `classforge-tiago-<sufijo>`;
- `jwtSecret`: secreto aleatorio nuevo, mínimo 64 caracteres;
- `mongoUri`: URI Atlas completa con base `classforge`;
- `groqApiKey`: clave Groq del backend;
- `sku`: B1 inicialmente; B2/B3 si la memoria o carga lo requieren;
- `additionalAllowedOrigins`: dominios HTTPS propios, si se usan. El hostname
  real de Azure se añade automáticamente.

Generar un secreto JWT sin mostrarlo ni pegarlo en el chat, escribiéndolo
directamente en el archivo privado:

```powershell
$parameterPath = 'deploy/azure/parameters.local.json'
$parameters = Get-Content $parameterPath -Raw | ConvertFrom-Json
$randomBytes = New-Object byte[] 64
$randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $randomGenerator.GetBytes($randomBytes) } finally { $randomGenerator.Dispose() }
$parameters.parameters.jwtSecret.value = [Convert]::ToBase64String($randomBytes)
$parameters | ConvertTo-Json -Depth 10 | Set-Content $parameterPath -Encoding utf8
```

Los tres parámetros sensibles son `secure` en Bicep y no aparecen en outputs.
App Service recibe variables de entorno. No se incluyen secretos en Angular,
JAR, artefactos GitHub ni commits. Azure protege App Settings; usuarios con
permisos sobre configuración pueden leerlas. Para requisitos mayores se pueden
reemplazar por referencias Key Vault con la identidad administrada del sitio y
los permisos correspondientes; eso requiere configurar también el vault.

## 3. Crear infraestructura (acción deliberada con costo)

La plantilla crea un plan dedicado de una instancia, sitio Java21, HTTPS,
Always On, TLS1.2 mínimo, health `/api/health`, perfil `azure` y App Settings.
Deshabilita FTP y credenciales básicas de SCM: los scripts usan Microsoft Entra.
Linux ya admite WebSocket; no hace falta activar el interruptor de Windows.

```powershell
# Solo cuando se haya elegido suscripción, región y costo:
.\scripts\azure-provision.ps1 -ResourceGroup rg-classforge `
  -ParametersFile deploy/azure/parameters.local.json -Location brazilsouth -Mode Create
```

El script imprime hostname e IP de salida, sin valores secretos. Registrar todas
las `outbound` en Atlas; se pueden incluir las `possibleOutbound` para cambios
futuros del plan. El hostname puede incluir un sufijo generado por Azure: usar
el que devuelve el recurso, sin construirlo a mano.

Para un grupo existente se puede validar primero sin modificar recursos:

```powershell
.\scripts\azure-provision.ps1 -ResourceGroup rg-classforge `
  -ParametersFile deploy/azure/parameters.local.json -Mode Validate
```

`WhatIf` está disponible como modo adicional. La salida de Azure puede contener
detalles de infraestructura: mantener privados los logs y el archivo de parámetros.
La plantilla administra un sitio y plan dedicados; no aplicarla sobre un servicio
compartido sin revisar antes su configuración.

## 4. Construir el paquete

```powershell
docker compose -f compose.mongo.yml up -d --wait
.\scripts\azure-build-package.ps1
```

Ejecuta instalación de dependencias, caracterización frontend, build Angular,
suite backend y bootJar. Resultado: `.azure-deploy/app.jar` y `app.jar.sha256`.
Gradle incluye los archivos de Angular usando `-PfrontendDist=<browser>` sin
copiarlos a `src/main/resources`; las builds local/AWS siguientes no los arrastran.
`-SkipInstall` reutiliza node_modules; `-SkipTests` es solo para empaquetar una
revisión ya verificada, no reemplaza la suite. No se incluyen mapas de fuente.

## 5. Publicar y verificar

Con Atlas accesible desde las IP de salida y App Settings completas:

```powershell
.\scripts\azure-deploy.ps1 -ResourceGroup rg-classforge -AppName <nombre-real>
```

Comprueba SHA256, publica el JAR con Azure CLI y ejecuta smoke de health,
rutas Angular, API privada y handshake WebSocket. Se puede repetir solo el smoke:

```powershell
.\scripts\azure-smoke.ps1 -BaseUrl https://<hostname-real>
```

El perfil `azure` no activa `demo` ni crea contraseñas de examen. Registrar una
cuenta en la web, crear un proyecto e invitar a otra cuenta. Comprobar:

1. Dos sesiones reciben cambios, presencia, chat y notificaciones; recargar una
   ruta profunda del proyecto carga Angular.
2. ASK, REVIEW y SUMMARY_CURRENT responden con el documento actual sin editarlo.
3. EDIT y voz generan preview; el UML cambia después de Apply.
4. Imagen simple devuelve una propuesta válida; la cuota/errores son recuperables.
5. Exportar Simple/Auth y comprobar que su asistente espera su propia clave Groq.
6. Reiniciar App Service y comprobar persistencia del proyecto y conversación.

El smoke público no comprueba Groq ni una sesión STOMP autenticada. Esas pruebas
se completan con las dos cuentas anteriores. Health público es liveness; la
disponibilidad IA se consulta en el proyecto con JWT.

## GitHub Actions opcional

El workflow **Prepare or deploy Azure release** solo se ejecuta manualmente.
Por defecto compila/prueba y conserva JAR/SHA256 siete días, sin publicar.
Seleccionar la rama `tiago` y ejecutar desde Actions, o con GitHub CLI:

```text
gh workflow run azure-release.yml --ref tiago -f deploy=false
```

Para usar la casilla `deploy`, crear el environment GitHub **azure**, configurar
OIDC de Microsoft Entra para el sujeto
`repo:S3B4S5C/classforge:environment:azure`, y asignar al principal permisos de
despliegue sobre el sitio/grupo. En ese environment definir variables:

- AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_SUBSCRIPTION_ID;
- AZURE_RESOURCE_GROUP, AZURE_WEBAPP_NAME.

No se necesita un secreto de cliente ni publish profile. Groq/Atlas/JWT se quedan
en App Settings creadas antes. Si el workflow no aparece aún en la interfaz,
la definición puede necesitar existir en la rama predeterminada para dispatch;
el script local sigue disponible. No se fusiona automáticamente `tiago` a main.

## Operación, límites y recuperación

- Mantener **una instancia** y no activar autoscale: broker STOMP y presencia
  viven en memoria. Antes de escalar horizontalmente hace falta broker externo
  y revisar coordinación entre procesos. Las conexiones se restablecen al reiniciar.
- El puerto procede de `SERVER_PORT`; el cliente usa `/api` y WSS del mismo origen.
- Ver logs: `az webapp log tail --resource-group rg-classforge --name <nombre>`.
  Si falta salida habilitar logging de aplicación en App Service. No compartir
  logs con tokens/URIs privadas.
- Error Mongo al arrancar: comprobar base explícita, permisos, IP allowlist y DNS.
- Error Groq: comprobar App Settings, modelos y cuota. La guía de IA es
  [assistant-groq.md](assistant-groq.md).
- Conservar el JAR/SHA anterior; volver a publicarlo permite revertir código.
  No revierte cambios de datos. Planificar backups de Mongo por separado.
- Para detener cargos, revisar/eliminar el plan y recursos dedicados desde Azure
  cuando ya no se necesiten; detener solo la app no elimina el costo del plan.

## Fuentes oficiales

- [Java en App Service](https://learn.microsoft.com/en-us/azure/app-service/quickstart-java).
- [Publicación de JAR](https://learn.microsoft.com/en-us/azure/app-service/deploy-zip).
- [Publicación Entra sin autenticación básica](https://learn.microsoft.com/en-us/azure/app-service/configure-basic-auth-disable).
- [Variables y SERVER_PORT](https://learn.microsoft.com/en-us/azure/app-service/reference-app-settings).
- [WebSocket en Linux](https://learn.microsoft.com/en-us/troubleshoot/azure/app-service/faqs-app-service-linux-new).
- [IP de salida](https://learn.microsoft.com/en-us/azure/app-service/overview-inbound-outbound-ips).
- [Referencias Key Vault](https://learn.microsoft.com/en-us/azure/app-service/app-service-key-vault-references).
- [Precios App Service Linux](https://azure.microsoft.com/en-us/pricing/details/app-service/linux/).
- [Atlas Free](https://www.mongodb.com/docs/atlas/tutorial/deploy-free-tier-cluster/).
- [Límites Atlas](https://www.mongodb.com/docs/atlas/reference/free-shared-limitations/).
