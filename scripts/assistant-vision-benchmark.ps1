param(
    [ValidateSet('regression', 'holdout', 'hardening')]
    [string]$Suite = 'regression',
    [int]$Attempts = 1,
    [string]$VisionUrl = 'http://127.0.0.1:8094',
    [string]$VisionModel = 'vision-model',
    [string]$ModelLabel = 'Vision model under evaluation',
    [string]$CaseId = '',
    [int]$TimeoutSeconds = 90,
    [int]$MaxCompletionTokens = 2800,
    [ValidateSet('single-pass', 'two-pass', 'hybrid-cv')]
    [string]$VisionMode = 'single-pass',
    [ValidateSet('original', 'board-crop', 'tiles')]
    [string]$ImageStrategy = 'original',
    [int]$RelationshipMaxCompletionTokens = 1800,
    [int]$HybridLocalizationTokens = 1200,
    [int]$HybridRelationshipTokens = 512,
    [int]$HybridMultiplicityTokens = 128,
    [switch]$HybridGeometryOnly,
    [double]$MinSemanticPercent = 0,
    [double]$MinSafetyPercent = 100,
    [double]$MinSchemaPercent = 100,
    [switch]$VerboseAttempts,
    [string]$ReportFile = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Fail([string]$Message) { throw $Message }
function Require([bool]$Condition, [string]$Message) { if (-not $Condition) { Fail $Message } }
function CmdQuote([string]$Value) {
    Require (-not $Value.Contains('"')) 'Los argumentos del benchmark no pueden contener comillas dobles.'
    return '"' + $Value + '"'
}

Require ($Attempts -ge 1) 'Attempts debe ser >= 1.'
Require ($TimeoutSeconds -ge 10) 'TimeoutSeconds debe ser >= 10.'
Require ($MaxCompletionTokens -ge 256) 'MaxCompletionTokens debe ser >= 256.'
Require ($RelationshipMaxCompletionTokens -ge 256) 'RelationshipMaxCompletionTokens debe ser >= 256.'
Require ($HybridLocalizationTokens -ge 256) 'HybridLocalizationTokens debe ser >= 256.'
Require ($HybridRelationshipTokens -ge 256) 'HybridRelationshipTokens debe ser >= 256.'
Require ($HybridMultiplicityTokens -ge 1) 'HybridMultiplicityTokens debe ser >= 1.'
if ($ImageStrategy -ne 'original') {
    Require ($CaseId -eq 'library-whiteboard-realistic') 'board-crop/tiles solo estan habilitados para library-whiteboard-realistic.'
}
if ($ImageStrategy -eq 'tiles') {
    Require ($VisionMode -eq 'single-pass') 'tiles requiere VisionMode=single-pass.'
}
if ($VisionMode -eq 'hybrid-cv') {
    Require ($ImageStrategy -eq 'original') 'hybrid-cv requiere ImageStrategy=original.'
}
if ($HybridGeometryOnly.IsPresent -and $VisionMode -ne 'hybrid-cv') {
    throw 'HybridGeometryOnly requiere VisionMode=hybrid-cv.'
}

$twoPass = ($VisionMode -eq 'two-pass').ToString().ToLowerInvariant()
$hybridCv = ($VisionMode -eq 'hybrid-cv').ToString().ToLowerInvariant()

$repo = (Get-Location).Path
$gradle = Join-Path $repo 'backend\gradlew.bat'
Require (Test-Path -LiteralPath $gradle) 'Ejecuta este script desde la raiz del repo ClassForge.'

if ([string]::IsNullOrWhiteSpace($ReportFile)) {
    $ReportFile = Join-Path $repo ('backend\build\reports\assistant-vision\' + $Suite + '.json')
}

Write-Host '==> Smoke multimodal previo' -ForegroundColor Cyan
& (Join-Path $repo 'scripts\assistant-vision-smoke.ps1') -VisionUrl $VisionUrl -VisionModel $VisionModel

$gradleArgs = @(
    'assistantVisionBenchmark',
    ('-PvisionSuite=' + $Suite),
    ('-Pattempts=' + $Attempts),
    ('-PvisionUrl=' + $VisionUrl),
    ('-PvisionModel=' + $VisionModel),
    ('-PmodelLabel=' + $ModelLabel),
    ('-PtimeoutSeconds=' + $TimeoutSeconds),
    ('-PmaxCompletionTokens=' + $MaxCompletionTokens),
    ('-PvisionTwoPass=' + $twoPass),
    ('-PvisionImageStrategy=' + $ImageStrategy),
    ('-PvisionHybridCv=' + $hybridCv),
    ('-PvisionHybridGeometryOnly=' + $HybridGeometryOnly.IsPresent.ToString().ToLowerInvariant()),
    ('-PhybridLocalizationTokens=' + $HybridLocalizationTokens),
    ('-PhybridRelationshipTokens=' + $HybridRelationshipTokens),
    ('-PhybridMultiplicityTokens=' + $HybridMultiplicityTokens),
    ('-PrelationshipMaxCompletionTokens=' + $RelationshipMaxCompletionTokens),
    ('-PminSemanticPercent=' + $MinSemanticPercent.ToString([Globalization.CultureInfo]::InvariantCulture)),
    ('-PminSafetyPercent=' + $MinSafetyPercent.ToString([Globalization.CultureInfo]::InvariantCulture)),
    ('-PminSchemaPercent=' + $MinSchemaPercent.ToString([Globalization.CultureInfo]::InvariantCulture)),
    ('-Pverbose=' + $VerboseAttempts.IsPresent.ToString().ToLowerInvariant()),
    ('-PreportFile=' + $ReportFile)
)

if (-not [string]::IsNullOrWhiteSpace($CaseId)) {
    $gradleArgs += ('-PvisionCase=' + $CaseId.Trim())
}

$commandLine = 'call ' + (CmdQuote $gradle) + ' ' + (($gradleArgs | ForEach-Object { CmdQuote $_ }) -join ' ')
$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = 'cmd.exe'
$startInfo.WorkingDirectory = (Join-Path $repo 'backend')
$startInfo.UseShellExecute = $false
$startInfo.Arguments = '/d /c ' + $commandLine

Write-Host ('==> Vision ' + $Suite + ' benchmark') -ForegroundColor Cyan
$process = New-Object System.Diagnostics.Process
$process.StartInfo = $startInfo
Require ($process.Start()) 'No se pudo iniciar Gradle.'

$gpuPeakMiB = $null
$nvidia = Get-Command nvidia-smi -ErrorAction SilentlyContinue

while (-not $process.HasExited) {
    if ($null -ne $nvidia) {
        try {
            $values = & $nvidia.Source --query-gpu=memory.used --format=csv,noheader,nounits 2>$null
            foreach ($value in @($values)) {
                $parsed = 0
                if ([int]::TryParse(([string]$value).Trim(), [ref]$parsed)) {
                    if ($null -eq $gpuPeakMiB -or $parsed -gt $gpuPeakMiB) {
                        $gpuPeakMiB = $parsed
                    }
                }
            }
        } catch { }
    }
    Start-Sleep -Milliseconds 500
    $process.Refresh()
}

$process.WaitForExit()
$exitCode = $process.ExitCode

if (Test-Path -LiteralPath $ReportFile) {
    $report = Get-Content -Raw -LiteralPath $ReportFile | ConvertFrom-Json
    if ($null -ne $gpuPeakMiB) {
        $report.gpuPeakMiB = $gpuPeakMiB
    }
    $report | ConvertTo-Json -Depth 100 | Set-Content -Encoding utf8 -LiteralPath $ReportFile
    Write-Host ('Reporte: ' + $ReportFile)
    if ($null -ne $gpuPeakMiB) {
        Write-Host ('Pico de memoria GPU observado: ' + $gpuPeakMiB + ' MiB')
    } else {
        Write-Host 'nvidia-smi no disponible; gpuPeakMiB queda sin medir.' -ForegroundColor Yellow
    }
}

if ($exitCode -ne 0) {
    Fail ('Vision benchmark termino con codigo ' + $exitCode + '.')
}

Write-Host ('Vision ' + $Suite + ' benchmark OK.') -ForegroundColor Green
