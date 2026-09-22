param(
    [int]$Attempts = 2,
    [string]$VisionUrl = 'http://127.0.0.1:8094',
    [string]$VisionModel = 'vision-model',
    [string]$ModelLabel = 'Vision model under evaluation',
    [int]$TimeoutSeconds = 180,
    [int]$MaxCompletionTokens = 3200,
    [switch]$VerboseAttempts,
    [switch]$SkipHybrid
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repo = Split-Path -Parent $PSScriptRoot
$benchmark = Join-Path $PSScriptRoot 'assistant-vision-benchmark.ps1'
if (-not (Test-Path -LiteralPath $benchmark)) {
    throw ('No se encontro el benchmark base en: ' + $benchmark)
}

$reportDir = Join-Path $repo 'backend\build\reports\assistant-vision'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

# The base benchmark resolves repo-relative files through Get-Location. Keep the
# wrapper callable from any PowerShell directory while restoring the caller's
# location afterwards.
Push-Location $repo
try {
    Write-Host '==> AssociationClass VLM hardening: single-pass' -ForegroundColor Cyan
    & $benchmark `
        -Suite hardening `
        -CaseId 'pedido-producto-association-class' `
        -Attempts $Attempts `
        -VisionUrl $VisionUrl `
        -VisionModel $VisionModel `
        -ModelLabel ($ModelLabel + ' AssociationClass single-pass') `
        -TimeoutSeconds $TimeoutSeconds `
        -MaxCompletionTokens $MaxCompletionTokens `
        -VisionMode 'single-pass' `
        -MinSemanticPercent 100 `
        -MinSafetyPercent 100 `
        -MinSchemaPercent 100 `
        -VerboseAttempts:$VerboseAttempts `
        -ReportFile (Join-Path $reportDir 'association-class-single-pass.json')

    if (-not $SkipHybrid.IsPresent) {
        Write-Host '==> AssociationClass VLM hardening: hybrid-cv' -ForegroundColor Cyan
        & $benchmark `
            -Suite hardening `
            -CaseId 'pedido-producto-association-class' `
            -Attempts $Attempts `
            -VisionUrl $VisionUrl `
            -VisionModel $VisionModel `
            -ModelLabel ($ModelLabel + ' AssociationClass hybrid-cv') `
            -TimeoutSeconds $TimeoutSeconds `
            -MaxCompletionTokens $MaxCompletionTokens `
            -VisionMode 'hybrid-cv' `
            -MinSemanticPercent 100 `
            -MinSafetyPercent 100 `
            -MinSchemaPercent 100 `
            -VerboseAttempts:$VerboseAttempts `
            -ReportFile (Join-Path $reportDir 'association-class-hybrid-cv.json')
    }

    Write-Host 'AssociationClass VLM hardening OK.' -ForegroundColor Green
} finally {
    Pop-Location
}
