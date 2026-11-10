[CmdletBinding()]
param(
    [string]$OutputDirectory = '.azure-deploy',
    [switch]$SkipTests,
    [switch]$SkipInstall
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$outputPath = if ([IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory } else { Join-Path $repoRoot $OutputDirectory }

function Invoke-Checked([string]$Label, [scriptblock]$Action) {
    Write-Host "==> $Label"
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "$Label failed (exit $LASTEXITCODE)." }
}

Push-Location (Join-Path $repoRoot 'frontend')
try {
    if (-not $SkipInstall) { Invoke-Checked 'Install frontend dependencies' { npm ci } }
    if (-not $SkipTests) { Invoke-Checked 'Frontend characterization' { npm run test:characterization } }
    Invoke-Checked 'Angular production build' { npm run build }
} finally { Pop-Location }

$browserPath = Join-Path $repoRoot 'frontend/dist/classforge-frontend/browser'
if (-not (Test-Path -LiteralPath (Join-Path $browserPath 'index.html'))) { throw 'Angular browser output is missing.' }
Push-Location (Join-Path $repoRoot 'backend')
try {
    $gradleCommand = if ($env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }
    if (-not $SkipTests) { Invoke-Checked 'Backend tests (requires Mongo replica set)' { & $gradleCommand test --no-daemon --max-workers=1 } }
    Invoke-Checked 'Complete Azure JAR' { & $gradleCommand bootJar "-PfrontendDist=$browserPath" --no-daemon --max-workers=1 }
} finally { Pop-Location }

$jarPath = Join-Path $repoRoot 'backend/build/libs/classforge-backend-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Expected bootJar was not produced.' }
New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
$artifactPath = Join-Path $outputPath 'app.jar'
Copy-Item -LiteralPath $jarPath -Destination $artifactPath -Force
$checksum = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $outputPath 'app.jar.sha256'), "$checksum  app.jar`n")
Write-Host "Azure artifact: $artifactPath"
Write-Host "SHA256: $checksum"
