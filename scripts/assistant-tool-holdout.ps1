param(
    [int]$Attempts = 3,
    [switch]$VerboseAttempts
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $root 'backend')
try {
    $report = Join-Path (Get-Location) 'build\reports\assistant-native-tools\holdout.json'
    & .\gradlew.bat assistantE2EReliability "-Pattempts=$Attempts" '-Psuite=holdout' "-Pverbose=$($VerboseAttempts.IsPresent.ToString().ToLowerInvariant())" "-PreportFile=$report"
    exit $LASTEXITCODE
} finally { Pop-Location }
