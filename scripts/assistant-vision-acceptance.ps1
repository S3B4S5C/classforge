param(
    [int]$Attempts = 2,
    [string]$VisionUrl = 'http://127.0.0.1:8094',
    [string]$VisionModel = 'vision-model',
    [string]$ModelLabel = 'Selected Vision model',
    [double]$RegressionMin = 95,
    [double]$HoldoutMin = 85,
    [double]$HardeningMin = 75,
    [switch]$VerboseAttempts,
    [switch]$SkipCu08Regression
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Fail([string]$Message) { throw $Message }
function Require([bool]$Condition, [string]$Message) { if (-not $Condition) { Fail $Message } }

$repo = (Get-Location).Path
Require (Test-Path -LiteralPath (Join-Path $repo 'backend\gradlew.bat')) 'Ejecuta este script desde la raiz de ClassForge.'

& .\scripts\assistant-vision-benchmark.ps1 -Suite regression -Attempts $Attempts -VisionUrl $VisionUrl -VisionModel $VisionModel -ModelLabel $ModelLabel -MinSemanticPercent $RegressionMin -MinSafetyPercent 100 -MinSchemaPercent 100 -VerboseAttempts:$VerboseAttempts
& .\scripts\assistant-vision-benchmark.ps1 -Suite holdout -Attempts $Attempts -VisionUrl $VisionUrl -VisionModel $VisionModel -ModelLabel $ModelLabel -MinSemanticPercent $HoldoutMin -MinSafetyPercent 100 -MinSchemaPercent 100 -VerboseAttempts:$VerboseAttempts
& .\scripts\assistant-vision-benchmark.ps1 -Suite hardening -Attempts $Attempts -VisionUrl $VisionUrl -VisionModel $VisionModel -ModelLabel $ModelLabel -MinSemanticPercent $HardeningMin -MinSafetyPercent 100 -MinSchemaPercent 100 -VerboseAttempts:$VerboseAttempts
& .\scripts\assistant-vision-e2e.ps1 -VisionUrl $VisionUrl -VisionModel $VisionModel

if (-not $SkipCu08Regression) {
    Write-Host '==> CU08 native-tools regression' -ForegroundColor Cyan
    & .\scripts\assistant-tool-regression.ps1
}

$reportDir = Join-Path $repo 'backend\build\reports\assistant-vision'
$regression = Get-Content -Raw -LiteralPath (Join-Path $reportDir 'regression.json') | ConvertFrom-Json
$holdout = Get-Content -Raw -LiteralPath (Join-Path $reportDir 'holdout.json') | ConvertFrom-Json
$hardening = Get-Content -Raw -LiteralPath (Join-Path $reportDir 'hardening.json') | ConvertFrom-Json

$acceptance = [ordered]@{
    useCase = 'CU-09'
    result = 'PASS'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    model = $ModelLabel
    modelAlias = $VisionModel
    visionUrl = $VisionUrl
    regressionSemanticExact = $regression.semanticExactPercent
    holdoutSemanticExact = $holdout.semanticExactPercent
    hardeningSemanticExact = $hardening.semanticExactPercent
    regressionExecutable = $regression.executableAcceptedPercent
    holdoutExecutable = $holdout.executableAcceptedPercent
    hardeningExecutable = $hardening.executableAcceptedPercent
    regressionSchemaValid = $regression.schemaValidPercent
    holdoutSchemaValid = $holdout.schemaValidPercent
    hardeningSchemaValid = $hardening.schemaValidPercent
    regressionSafety = $regression.safetyInvalidImagePercent
    holdoutSafety = $holdout.safetyInvalidImagePercent
    hardeningSafety = $hardening.safetyInvalidImagePercent
    persistedImageE2E = $true
    cu08RegressionExecuted = (-not $SkipCu08Regression)
}

$acceptancePath = Join-Path $reportDir 'cu09-acceptance.json'
$acceptance | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 -LiteralPath $acceptancePath
Write-Host ('CU09 acceptance PASS. Report: ' + $acceptancePath) -ForegroundColor Green
