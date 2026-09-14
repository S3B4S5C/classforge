# Guía rápida — Demo CU-27 con Spring y Angular en consolas separadas

Esta versión de los scripts **no inicia ni detiene servidores**. `demo-reset.ps1` solamente limpia el estado de la demo; tú controlas Spring y Angular manualmente.

## 0. Antes de resetear

Detén cualquier Spring/Angular anterior con `Ctrl+C` en sus consolas.

## 1. Reset de datos demo

Desde la raíz del repo:

```powershell
$env:JAVA_HOME = "F:\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\scripts\demo-reset.ps1
```

Se eliminan únicamente:

```text
backend/data/classforge-demo*
.demo/
```

La base normal `backend/data/classforge*` no se toca.

## 2. Consola 1 — Backend demo

```powershell
cd backend
.\gradlew.bat bootRun --no-daemon --args="--spring.profiles.active=demo"
```

Debe quedar en `http://localhost:8082`.

## 3. Verificación del fixture

En otra consola, desde la raíz:

```powershell
.\scripts\demo-smoke.ps1
```

Salida final esperada:

```text
CU-27 demo smoke GREEN.
```

## 4. Consola 2 — Frontend

```powershell
cd frontend
npm ci
npm start
```

`npm ci` solo es necesario cuando faltan dependencias o cambió el lockfile. Angular queda en `http://localhost:4200`.

## 5. Credenciales

```text
OWNER  demo@classforge.local / classforge-demo
EDITOR colaborador@classforge.local / classforge-demo

Proyecto   Veterinaria CU-27
Project ID 27000000-0000-0000-0000-000000000100
```

## 6. IA local opcional

Si vas a demostrar texto, voz e imagen, levanta también los runtimes indicados en `docs/runtime/assistant-local.md` y valida:

```powershell
.\scripts\demo-smoke.ps1 -RequireAi
```

## 7. Enterprise Architect opcional

Con el backend demo ya ejecutándose manualmente:

```powershell
.\scripts\demo-ea-smoke.ps1 -RepositoryPath "F:\demo\classforge-cu27.qea"
```

Usa un repositorio EA desechable.

## 8. Acceptance final

```powershell
.\scripts\demo-acceptance.ps1
```

## 9. Apagado

Presiona `Ctrl+C` en la consola de Angular y en la de Spring. `demo-stop.ps1` ya no mata procesos; solo recuerda este flujo.

Para el runbook detallado y troubleshooting, consulta `docs/runtime/cu27-demo.md`.
