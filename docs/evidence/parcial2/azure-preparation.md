# Preparación Azure — verificación

Fecha: 9 de noviembre de 2026. Rama `tiago`, base `ba5325c`.
Destino preparado: App Service Linux Java SE21, una instancia, Angular en JAR,
MongoDB Atlas y Groq. No se ejecutó aprovisionamiento ni publicación en Azure.

## Resultado

| Comprobación | Evidencia |
| --- | --- |
| Backend completo | 522 casos: 512 aprobados, 10 opt-in omitidos, 0 fallos/errores |
| Frontend | 46 aprobados, 0 fallos |
| Angular producción + JAR Azure | Correctos, incluyendo reutilización de configuration cache |
| Bicep | Compilado con CLI0.49.2, sin errores; credenciales secureString |
| PowerShell / workflow | Scripts parseados, YAML parseado con dos jobs |
| Validación CLI | Rechaza CLI antigua y parámetros incompletos antes de operaciones cloud |
| Contenido del JAR | 22 entradas frontend; index.html y perfil azure presentes, sin archivos privados ni claves configuradas |
| Smoke HTTP / WebSocket | Health200, rutas SPA200, API privada401 y handshake WS correcto |
| Navegador sobre el JAR real | Dos usuarios, STOMP/presencia, assets, recarga de enlace profundo y móvil sin overflow ni errores JS |
| Build normal después del Azure | Sin entradas frontend residuales; local/AWS siguen separados |

La suite usa Java21.0.13 y Mongo8.0.17 replica set aislado en 27028. Angular se
construyó con Node24.22.1. El smoke del JAR usa puerto18083 y la base aislada
`cf_azure_smoke_20261109`. No se cambió la base habitual.

Se reprodujeron tres fallos SPA antes de implementar: rutas profundas, assets
públicos y respuesta de archivo inexistente. Las cinco pruebas dedicadas pasan
con la seguridad real; API nunca recibe el fallback HTML, POST a rutas SPA sigue
protegido y la funcionalidad no se activa en modo local/AWS.

El primer empaquetado detectó una incompatibilidad con la configuration cache de
Gradle9: `file()` dentro de `doFirst`. Se movió la resolución del archivo a la fase
de configuración, se ejecutó el mismo empaquetado y pasó, incluyendo un segundo
uso de caché. Se corrigió además el modo Git de gradlew a100755 para Linux.

## Artefacto local

`.azure-deploy/app.jar`, 172641528 bytes, acompañado de `app.jar.sha256`:

```text
b8e803dadc4058e68836ef7562099de658c12ec4c87505a96c46777d64bc29ff
```

El JAR y los parámetros privados se excluyen de Git. El archivo local de parámetros
está preparado con JWT aleatorio y Groq; la URI Atlas queda por completar.

## Alcance del smoke

Edge headless abrió dos contextos con cuentas de prueba y proyecto creados en la
base aislada, usando HTTP del mismo JAR, sin servidor Angular ni proxy externo.
La presencia mostró ambas sesiones, el móvil usó390x844 y el escritorio1440x1000.
Se compararon revisión y documento UML antes/después: permanecieron iguales.

La prueba de hosting no llamó a Groq: en navegador se simuló únicamente la respuesta
de disponibilidad IA. Las funciones con Groq real se verificaron en la entrega
anterior, documentada en [integración Groq](groq-integration.md).

La revisión independiente no encontró otros bloqueos de implementación. Faltan la
suscripción/recursos Azure, URI y allowlist Atlas y smoke contra el hostname público.
Compilar Bicep no verifica cuotas regionales, permisos ni conexión real Azure/Atlas.
El workflow se revisó y parseó localmente; no se ejecutó en GitHub Actions.

Runbook: [despliegue Azure](../../runtime/azure-deployment.md).
