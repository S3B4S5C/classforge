# CU-27 — Demo reproducible final

La demo final usa el perfil Spring `demo` y el dominio canónico **Veterinaria CU-27**. El objetivo es que el examen parta siempre del mismo usuario, proyecto, UUIDs y modelo UML sin tocar la base de desarrollo normal.

## Estado determinista

```text
OWNER  demo@classforge.local / classforge-demo
EDITOR colaborador@classforge.local / classforge-demo

Proyecto   Veterinaria CU-27
Project ID 27000000-0000-0000-0000-000000000100
Revision   1 al arrancar
```

El perfil `demo` usa `backend/data/classforge-demo` y `ddl-auto=create`. Cada arranque manual del backend con ese perfil reconstruye el esquema demo y `DemoScenarioSeeder` carga `demo/veterinaria-cu27.xmi`. El perfil normal continúa usando `backend/data/classforge`.

## Regla de operación de los scripts

Los scripts de CU-27 **no administran servidores**. No abren Spring, no abren Angular y no cierran procesos. El backend y el frontend se ejecutan manualmente en dos consolas propias para que sus logs y su ciclo de vida queden visibles durante la demo.

- `demo-reset.ps1`: elimina exclusivamente la base demo `classforge-demo*` y el scratch local `.demo`.
- `demo-start.ps1`: valida que existan el perfil y el fixture y muestra los comandos manuales de arranque; no inicia procesos.
- `demo-stop.ps1`: es un recordatorio de compatibilidad; no termina procesos.
- `demo-smoke.ps1`: valida por HTTP una instancia demo que ya esté ejecutándose.

## Flujo recomendado desde cero

### 1. Detener manualmente instancias anteriores

Si Spring o Angular ya están ejecutándose, detenerlos en sus respectivas consolas con `Ctrl+C`. Esto evita que H2 mantenga bloqueado `classforge-demo*` durante el reset.

### 2. Configurar Java y resetear únicamente los datos demo

Desde la raíz del repo:

```powershell
$env:JAVA_HOME = "F:\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\scripts\demo-reset.ps1
```

El script no inicia ningún servidor. Al terminar, la siguiente ejecución manual del backend con perfil `demo` reconstruirá la BD y sembrará el escenario determinista.

### 3. Consola 1 — iniciar backend Spring manualmente

```powershell
cd backend
.\gradlew.bat bootRun --no-daemon --args="--spring.profiles.active=demo"
```

Esperar hasta que Spring quede escuchando en:

```text
http://localhost:8082
```

El arranque con perfil `demo` crea el esquema H2 demo y ejecuta `DemoScenarioSeeder`.

### 4. Validar que el escenario quedó listo

En una tercera consola, desde la raíz del repo:

```powershell
.\scripts\demo-smoke.ps1
```

Debe terminar en:

```text
CU-27 demo smoke GREEN.
```

Para exigir también los tres runtimes locales de IA:

```powershell
.\scripts\demo-smoke.ps1 -RequireAi
```

### 5. Consola 2 — iniciar Angular manualmente

```powershell
cd frontend
```

Si es la primera ejecución, cambió `package-lock.json` o no existe `node_modules`:

```powershell
npm ci
```

Luego:

```powershell
npm start
```

Angular queda disponible en:

```text
http://localhost:4200
```

El proxy Angular reenvía `/api/*` a `http://localhost:8082`.

### 6. Abrir la demo

Usar cualquiera de estas cuentas:

```text
OWNER : demo@classforge.local / classforge-demo
EDITOR: colaborador@classforge.local / classforge-demo
```

Proyecto esperado:

```text
Veterinaria CU-27
27000000-0000-0000-0000-000000000100
```

### 7. Detener la demo

Detener Angular y Spring manualmente con `Ctrl+C` en sus dos consolas. `demo-stop.ps1` no mata procesos.

## Runtimes locales

La demo no intenta localizar binarios privados. Antes de pasos de IA/voz/imagen, arrancar las instancias documentadas en `assistant-local.md`:

```text
llama.cpp texto   127.0.0.1:8092
whisper.cpp       127.0.0.1:8093
llama.cpp vision  127.0.0.1:8094
```

Comprobar los tres runtimes a través del backend autoritativo:

```powershell
.\scripts\demo-smoke.ps1 -RequireAi
```

## Acceptance automatizado final

```powershell
.\scripts\demo-acceptance.ps1
```

Ejecuta el escenario demo más las regresiones XMI, Assistant generado, Flutter, Angular, Domain Manifest, OpenAPI/Postman, CRUD/Auth, Spring, full backend y build frontend. No deja servidores Spring/Angular de la demo ejecutándose. El reporte de la corrida queda en:

```text
backend/build/reports/demo/cu27-acceptance.json
```

## Enterprise Architect real

CU-10/CU-11 ya prueban XMI 2.1 automáticamente. Para la transición se añade un smoke con la Automation Interface de Enterprise Architect:

1. crear o copiar un repositorio EA **desechable** (`.qea`, `.eapx` o `.eap`);
2. mantener el backend demo arrancado manualmente en la consola 1;
3. ejecutar:

```powershell
.\scripts\demo-ea-smoke.ps1 -RepositoryPath "F:\demo\classforge-cu27.qea"
```

El script:

```text
ClassForge export XMI
 -> EA.Repository COM
 -> ImportPackageXMI
 -> ExportPackageXMI (XMI 2.1)
 -> ClassForge /xmi/import/preview
```

No aplica el XMI reimportado al proyecto: el último paso es preview. El reporte queda en `.demo/ea/ea-smoke.json`.

La Automation Interface oficial expone `Repository.GetProjectInterface()`, `Project.ImportPackageXMI(...)` y `Project.ExportPackageXMI(...)`; el script usa `xmiEA21 = 11`.

## Guion recomendado del examen

1. Ejecutar `demo-reset.ps1` con Spring y Angular detenidos.
2. Arrancar Spring manualmente con perfil `demo` en la consola 1.
3. Ejecutar `demo-smoke.ps1` y confirmar GREEN.
4. Arrancar Angular manualmente en la consola 2.
5. Login OWNER y abrir Veterinaria CU-27.
6. Mostrar clases/atributos/relaciones y validación.
7. Abrir segundo navegador con EDITOR y demostrar colaboración/presencia.
8. Ejecutar una modificación manual y Undo/Redo.
9. Usar Assistant de texto y voz sobre UML, revisar preview y Apply.
10. Mostrar Imagen → UML si los runtimes Vision están levantados.
11. Importar/exportar XMI desde el diálogo de ClassForge; usar `demo-ea-smoke.ps1` para evidencia con EA real.
12. Generar el sistema Spring en modo Simple o Auth; para Auth seleccionar `Usuario.username/password`.
13. Mostrar `openapi.yaml`, Postman y `domain-manifest.json`.
14. Compilar/abrir Angular o Flutter generado.
15. En la app generada ejecutar consultas por chat/voz y una mutación con confirmación.

Prompts sugeridos: `examples/veterinaria-cu27/demo-prompts.md`.

## Troubleshooting

Si `demo-reset.ps1` informa que no puede eliminar `classforge-demo*`, detener el backend demo manualmente con `Ctrl+C` y volver a ejecutar el reset.

Si el smoke no encuentra el proyecto determinista, confirmar que Spring se inició exactamente con:

```powershell
.\gradlew.bat bootRun --no-daemon --args="--spring.profiles.active=demo"
```

No usar `bootRun` sin el perfil `demo`, porque ese arranque utiliza la base normal `backend/data/classforge` y no el escenario CU-27.

## Regla de seguridad

Las credenciales `classforge-demo` y el secreto JWT demo existen exclusivamente para el perfil `demo`; no son configuración de producción.
