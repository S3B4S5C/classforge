param(
    [string]$VisionUrl = 'http://127.0.0.1:8094',
    [string]$VisionModel = 'vision-model'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Fail([string]$Message) { throw $Message }
function Require([bool]$Condition, [string]$Message) { if (-not $Condition) { Fail $Message } }

$repo = (Get-Location).Path
$gradle = Join-Path $repo 'backend\gradlew.bat'
Require (Test-Path -LiteralPath $gradle) 'Ejecuta este script desde la raiz de ClassForge.'

& .\scripts\assistant-vision-smoke.ps1 -VisionUrl $VisionUrl -VisionModel $VisionModel

Push-Location (Join-Path $repo 'backend')
try {
    & .\gradlew.bat assistantVisionAcceptance `
        ('-PvisionUrl=' + $VisionUrl) `
        ('-PvisionModel=' + $VisionModel)
    if ($LASTEXITCODE -ne 0) {
        Fail ('Vision E2E termino con codigo ' + $LASTEXITCODE + '.')
    }
} finally {
    Pop-Location
}

Write-Host 'Vision E2E OK: imagen real -> plan -> comando canonico -> persistencia -> reopen.' -ForegroundColor Green
