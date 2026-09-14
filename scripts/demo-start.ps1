$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$demoProfile = Join-Path $root "backend\src\main\resources\application-demo.yml"
$demoFixture = Join-Path $root "backend\src\main\resources\demo\veterinaria-cu27.xmi"

if (-not (Test-Path $demoProfile)) {
    throw "CU-27 demo profile not found: $demoProfile"
}
if (-not (Test-Path $demoFixture)) {
    throw "CU-27 demo fixture not found: $demoFixture"
}

Write-Host ""
Write-Host "CU-27 DEMO PREPARED"
Write-Host "No Spring or Angular server was started by this script."
Write-Host ""
Write-Host "Backend console:"
Write-Host "  cd `"$root\backend`""
Write-Host "  .\gradlew.bat bootRun --no-daemon --args=`"--spring.profiles.active=demo`""
Write-Host ""
Write-Host "Frontend console:"
Write-Host "  cd `"$root\frontend`""
Write-Host "  npm ci        # only when dependencies are missing/outdated"
Write-Host "  npm start"
Write-Host ""
Write-Host "After the backend is ready, validate the deterministic demo with:"
Write-Host "  .\scripts\demo-smoke.ps1"
Write-Host ""
Write-Host "Owner    : demo@classforge.local / classforge-demo"
Write-Host "Editor   : colaborador@classforge.local / classforge-demo"
Write-Host "Project  : Veterinaria CU-27"
Write-Host "ProjectId: 27000000-0000-0000-0000-000000000100"
