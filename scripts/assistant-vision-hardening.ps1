param(
    [int]$Attempts = 2,
    [string]$VisionUrl = 'http://127.0.0.1:8094',
    [string]$VisionModel = 'vision-model',
    [string]$ModelLabel = 'Vision model under evaluation',
    [double]$MinSemanticPercent = 0,
    [switch]$VerboseAttempts
)

$ErrorActionPreference = 'Stop'

& .\scripts\assistant-vision-benchmark.ps1 `
    -Suite hardening `
    -Attempts $Attempts `
    -VisionUrl $VisionUrl `
    -VisionModel $VisionModel `
    -ModelLabel $ModelLabel `
    -MinSemanticPercent $MinSemanticPercent `
    -MinSafetyPercent 0 `
    -MinSchemaPercent 0 `
    -VerboseAttempts:$VerboseAttempts
