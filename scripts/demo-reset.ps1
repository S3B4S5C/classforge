$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dataDir = Join-Path $root "backend\data"
$stateDir = Join-Path $root ".demo"

New-Item -ItemType Directory -Force -Path $dataDir | Out-Null

$demoFiles = @(Get-ChildItem -Path $dataDir -Filter "classforge-demo*" -ErrorAction SilentlyContinue)
if ($demoFiles.Count -gt 0) {
    try {
        $demoFiles | Remove-Item -Force -ErrorAction Stop
    } catch {
        throw "Could not reset backend/data/classforge-demo*. Stop the backend manually with Ctrl+C, then run demo-reset.ps1 again. Original error: $($_.Exception.Message)"
    }
}

if (Test-Path $stateDir) {
    Remove-Item $stateDir -Recurse -Force -ErrorAction Stop
}

Write-Host "CU-27 demo data reset complete."
Write-Host "Only backend/data/classforge-demo* and the local .demo scratch directory were removed."
Write-Host "No Spring or Angular server was started or stopped."

& (Join-Path $PSScriptRoot "demo-start.ps1")
