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

$repoRoot = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $repoRoot "backend"

Push-Location $backend
try {
    $gradleArgs = @(
        "assistantE2EReliability",
        "-Pattempts=$Attempts",
        "-PerrorFailurePercent=$ErrorFailurePercent",
        "-PllamaUrl=$LlamaUrl",
        "-Pmodel=$Model",
        "-Pverbose=$($VerboseAttempts.IsPresent.ToString().ToLowerInvariant())"
    )

    & .\gradlew.bat @gradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
