param(
    [ValidateRange(1, 1000)]
    [int]$Attempts = 20,

    [string]$Prompt = 'Crea una asociacion entre 4nimal y mascota',

    [string[]]$Classes = @('Animal', 'Mascota'),

    [string]$ExpectedSource = 'Animal',

    [string]$ExpectedTarget = 'Mascota',

    [ValidateSet('ASSOCIATION', 'AGGREGATION', 'COMPOSITION', 'GENERALIZATION')]
    [string]$ExpectedType = 'ASSOCIATION',

    [string]$LlamaUrl = 'http://127.0.0.1:8092',

    [string]$Model = 'local-model',

    [switch]$VerboseAttempts,

    [switch]$FailOnFinalError
)

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $RepoRoot 'backend'
$Gradle = Join-Path $Backend 'gradlew.bat'

if (-not (Test-Path -LiteralPath $Gradle)) {
    throw "No se encontro el wrapper Gradle en $Gradle"
}

$ClassList = ($Classes | ForEach-Object { $_.Trim() } | Where-Object { $_ }) -join ','

Push-Location $Backend
try {
    & $Gradle assistantReliability `
        "-Pattempts=$Attempts" `
        "-Pprompt=$Prompt" `
        "-Pclasses=$ClassList" `
        "-PexpectedSource=$ExpectedSource" `
        "-PexpectedTarget=$ExpectedTarget" `
        "-PexpectedType=$ExpectedType" `
        "-PllamaUrl=$LlamaUrl" `
        "-Pmodel=$Model" `
        "-Pverbose=$($VerboseAttempts.IsPresent.ToString().ToLowerInvariant())" `
        "-PfailOnFinalError=$($FailOnFinalError.IsPresent.ToString().ToLowerInvariant())"

    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
