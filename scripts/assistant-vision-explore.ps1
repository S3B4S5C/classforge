param(
    [int]$Attempts = 1,
    [string]$VisionUrl = 'http://127.0.0.1:8094',
    [string]$VisionModel = 'vision-model',
    [string]$ModelLabel = 'Vision model under evaluation',
    [switch]$VerboseAttempts
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

foreach ($suite in @('regression', 'holdout', 'hardening')) {
    Write-Host ('==> Exploratory Vision suite: ' + $suite) -ForegroundColor Cyan
    & .\scripts\assistant-vision-benchmark.ps1 `
        -Suite $suite `
        -Attempts $Attempts `
        -VisionUrl $VisionUrl `
        -VisionModel $VisionModel `
        -ModelLabel $ModelLabel `
        -MinSemanticPercent 0 `
        -MinSafetyPercent 0 `
        -MinSchemaPercent 0 `
        -VerboseAttempts:$VerboseAttempts
}

Write-Host ''
Write-Host 'Exploratory run complete. No quality gate was enforced.' -ForegroundColor Green
Write-Host 'Inspect backend\build\reports\assistant-vision before changing model/prompt.'
