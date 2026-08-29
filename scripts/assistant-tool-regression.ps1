param(
    [int]$Attempts = 20,
    [switch]$VerboseAttempts
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $root 'backend')
try {
    $report = Join-Path (Get-Location) 'build\reports\assistant-native-tools\regression.json'
    & .\gradlew.bat assistantE2EReliability "-Pattempts=$Attempts" '-Psuite=regression' "-Pverbose=$($VerboseAttempts.IsPresent.ToString().ToLowerInvariant())" "-PreportFile=$report"
    exit $LASTEXITCODE
} finally { Pop-Location }
