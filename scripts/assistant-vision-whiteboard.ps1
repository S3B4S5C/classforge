param(
    [int]$Attempts = 2,
    [string]$VisionUrl = 'http://127.0.0.1:8094',
    [string]$VisionModel = 'vision-model',
    [string]$ModelLabel = 'Vision model under evaluation',
    [int]$TimeoutSeconds = 180,
    [int]$MaxCompletionTokens = 3200,
    [ValidateSet('single-pass', 'two-pass', 'hybrid-cv')]
    [string]$VisionMode = 'single-pass',
    [ValidateSet('original', 'board-crop', 'tiles')]
    [string]$ImageStrategy = 'original',
    [int]$RelationshipMaxCompletionTokens = 1800,
    [int]$HybridLocalizationTokens = 1200,
    [int]$HybridRelationshipTokens = 512,
    [int]$HybridMultiplicityTokens = 128,
    [switch]$HybridGeometryOnly,
    [switch]$VerboseAttempts
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function ConvertTo-ReportSlug([string]$Value) {
    $slug = ($Value.ToLowerInvariant() -replace '[^a-z0-9]+', '-').Trim([char]'-')
    if ([string]::IsNullOrWhiteSpace($slug)) { return 'vision-model' }
    return $slug
}

if ($ImageStrategy -eq 'tiles' -and $VisionMode -ne 'single-pass') {
    throw 'ImageStrategy=tiles requiere VisionMode=single-pass.'
}
if ($VisionMode -eq 'hybrid-cv' -and $ImageStrategy -ne 'original') {
    throw 'VisionMode=hybrid-cv requiere ImageStrategy=original.'
}
if ($HybridGeometryOnly.IsPresent -and $VisionMode -ne 'hybrid-cv') {
    throw 'HybridGeometryOnly requiere VisionMode=hybrid-cv.'
}

$repo = (Get-Location).Path
$modelSlug = ConvertTo-ReportSlug $ModelLabel
$stageSlug = if ($HybridGeometryOnly.IsPresent) { '-geometry-only' } else { '' }
$report = Join-Path $repo (
    'backend\build\reports\assistant-vision\whiteboard-' +
    $modelSlug + '-' + $ImageStrategy + '-' + $VisionMode + $stageSlug + '.json'
)

& .\scripts\assistant-vision-benchmark.ps1 `
    -Suite hardening `
    -CaseId 'library-whiteboard-realistic' `
    -Attempts $Attempts `
    -VisionUrl $VisionUrl `
    -VisionModel $VisionModel `
    -ModelLabel $ModelLabel `
    -TimeoutSeconds $TimeoutSeconds `
    -MaxCompletionTokens $MaxCompletionTokens `
    -VisionMode $VisionMode `
    -ImageStrategy $ImageStrategy `
    -RelationshipMaxCompletionTokens $RelationshipMaxCompletionTokens `
    -HybridLocalizationTokens $HybridLocalizationTokens `
    -HybridRelationshipTokens $HybridRelationshipTokens `
    -HybridMultiplicityTokens $HybridMultiplicityTokens `
    -HybridGeometryOnly:$HybridGeometryOnly `
    -MinSemanticPercent 0 `
    -MinSafetyPercent 0 `
    -MinSchemaPercent 0 `
    -ReportFile $report `
    -VerboseAttempts:$VerboseAttempts
