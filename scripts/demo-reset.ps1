$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue) {
    throw "Deten el backend antes de reiniciar la base demo."
}
& docker compose -f (Join-Path $root "compose.mongo.yml") up -d --wait
if ($LASTEXITCODE -ne 0) { throw "No se pudo iniciar MongoDB." }
# Fixed local demo database only. The normal classforge database and legacy H2 backups are preserved.
& docker exec classforge-mongo mongosh --quiet --eval "db.getSiblingDB('classforge_demo').dropDatabase()"
if ($LASTEXITCODE -ne 0) { throw "No se pudo reiniciar classforge_demo." }
Write-Host "Base MongoDB classforge_demo reiniciada."
& (Join-Path $PSScriptRoot "demo-start.ps1")
