param(
    [int]$Attempts = 2,
    [string]$VisionUrl = 'http://127.0.0.1:8094',
    [string]$VisionModel = 'vision-model',
    [string]$ModelLabel = 'Qwen3-VL-2B-Instruct Q4_K_M + mmproj Q8_0',
    [double]$MinSemanticPercent = 70,
    [switch]$VerboseAttempts
)

$ErrorActionPreference = 'Stop'

& .\scripts\assistant-vision-benchmark.ps1 `
    -Suite holdout `
    -Attempts $Attempts `
    -VisionUrl $VisionUrl `
    -VisionModel $VisionModel `
    -ModelLabel $ModelLabel `
    -MinSemanticPercent $MinSemanticPercent `
    -MinSafetyPercent 100 `
    -MinSchemaPercent 100 `
    -VerboseAttempts:$VerboseAttempts

