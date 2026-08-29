param(
    [ValidateRange(1, 1000)]
    [int]$Attempts = 5,

    [ValidateRange(0, 100)]
    [double]$ErrorFailurePercent = 60,

    [string]$LlamaUrl = "http://127.0.0.1:8092",

    [string]$Model = "local-model",

    [switch]$VerboseAttempts
)

$ErrorActionPreference = "Stop"
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $repoRoot "backend"
$reportDir = Join-Path $backend "build\reports\assistant-tool-ab"
$legacyReport = Join-Path $reportDir "legacy.json"
$toolsReport = Join-Path $reportDir "tools.json"

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
Remove-Item -LiteralPath $legacyReport, $toolsReport -Force -ErrorAction SilentlyContinue

function Invoke-Benchmark([string]$Mode, [string]$ReportFile) {
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " Assistant E2E benchmark: $Mode"
    Write-Host "============================================================"

    $gradleArgs = @(
        "assistantE2EReliability",
        "--rerun-tasks",
        "--no-build-cache",
        "-Pattempts=$Attempts",
        "-PerrorFailurePercent=$ErrorFailurePercent",
        "-PllamaUrl=$LlamaUrl",
        "-Pmodel=$Model",
        "-PplannerMode=$Mode",
        "-PreportFile=$ReportFile",
        "-Pverbose=$($VerboseAttempts.IsPresent.ToString().ToLowerInvariant())"
    )

    & .\gradlew.bat @gradleArgs
    return $LASTEXITCODE
}

Push-Location $backend
try {
    $legacyExit = Invoke-Benchmark "legacy" $legacyReport
    $toolsExit = Invoke-Benchmark "tools" $toolsReport
}
finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $legacyReport)) {
    throw "El benchmark legacy no genero $legacyReport"
}
if (-not (Test-Path -LiteralPath $toolsReport)) {
    throw "El benchmark tools no genero $toolsReport. Comprueba /props y que Qwen este iniciado con --jinja."
}

$legacy = Get-Content -LiteralPath $legacyReport -Raw | ConvertFrom-Json
$tools = Get-Content -LiteralPath $toolsReport -Raw | ConvertFrom-Json

$legacyByCategory = @{}
foreach ($category in $legacy.categories) {
    $legacyByCategory[$category.category] = $category
}

Write-Host ""
Write-Host "A/B FINAL - LEGACY vs NATIVE TOOLS"
Write-Host "=================================="
Write-Host ("{0,-30} {1,10} {2,10} {3,10} {4,10}" -f "CATEGORY", "LEGACY", "TOOLS", "DELTA", "TOOLS")

foreach ($category in $tools.categories) {
    $legacyCategory = $legacyByCategory[$category.category]
    $legacyApproval = if ($null -eq $legacyCategory) { 0.0 } else { [double]$legacyCategory.approval }
    $toolsApproval = [double]$category.approval
    $delta = $toolsApproval - $legacyApproval

    Write-Host (
        "{0,-30} {1,9:N1}% {2,9:N1}% {3,9:+0.0;-0.0;0.0}% {4,10}" -f `
            $category.category,
            $legacyApproval,
            $toolsApproval,
            $delta,
            $category.status
    )
}

$overallDelta = [double]$tools.overallApproval - [double]$legacy.overallApproval
Write-Host ""
Write-Host ("LEGACY overall: {0:N1}%" -f [double]$legacy.overallApproval)
Write-Host ("TOOLS  overall: {0:N1}%" -f [double]$tools.overallApproval)
Write-Host ("DELTA:          {0:+0.0;-0.0;0.0}%" -f $overallDelta)
Write-Host ""
Write-Host "Safety rule: SAFETY_UNKNOWN_REFERENCE queda ERROR con un solo unsafe accept."
Write-Host "Reports: $reportDir"

$toolFailures = @($tools.categories | Where-Object { @($_.failures).Count -gt 0 })
if ($toolFailures.Count -gt 0) {
    Write-Host ""
    Write-Host "TOOLS FAILURE DETAILS"
    Write-Host "====================="
    foreach ($category in $toolFailures) {
        Write-Host ""
        Write-Host ("Failures - {0}" -f $category.category)
        foreach ($failure in @($category.failures)) {
            Write-Host ("  #{0} [{1}] HTTP={2} stage={3} reason={4}" -f `
                $failure.attempt,
                $failure.variant,
                $failure.httpStatus,
                $failure.stage,
                $failure.reason)
            Write-Host ("     prompt: {0}" -f $failure.prompt)
            Write-Host ("     detail: {0}" -f $failure.detail)
        }
    }
}

if ($toolsExit -ne 0) {
    exit $toolsExit
}

exit 0
