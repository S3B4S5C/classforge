[CmdletBinding()]
param(
    [string]$Output = "classforge-aws-deploy-bundle.zip",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Stage = Join-Path $Root ".aws-deploy-bundle"

function Invoke-Checked([string]$Label, [scriptblock]$Action) {
    Write-Host "`n==> $Label"
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE."
    }
    Write-Host "[ok] $Label"
}

if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force $Stage | Out-Null

Push-Location (Join-Path $Root "backend")
try {
    if (-not $SkipTests) {
        Invoke-Checked "Backend tests" { .\gradlew.bat test --no-daemon }
    }
    Invoke-Checked "Spring Boot jar" { .\gradlew.bat bootJar --no-daemon }
} finally { Pop-Location }

Push-Location (Join-Path $Root "frontend")
try {
    if (-not (Test-Path "node_modules")) {
        Invoke-Checked "Frontend dependencies" { npm ci }
    }
    if (-not $SkipTests) {
        Invoke-Checked "Frontend characterization tests" { npm run test:characterization }
    }
    Invoke-Checked "Angular production build" { npm run build }
} finally { Pop-Location }

$Jar = Get-ChildItem (Join-Path $Root "backend\build\libs\*.jar") |
    Where-Object { $_.Name -notmatch '-plain\.jar$' } |
    Sort-Object Length -Descending |
    Select-Object -First 1
if (-not $Jar) { throw "No Spring Boot jar found under backend/build/libs." }
Copy-Item $Jar.FullName (Join-Path $Stage "classforge.jar")

$Browser = Join-Path $Root "frontend\dist\classforge-frontend\browser"
if (-not (Test-Path $Browser)) {
    $Browser = Join-Path $Root "frontend\dist\classforge-frontend"
}
if (-not (Test-Path (Join-Path $Browser "index.html"))) {
    throw "Angular browser output not found under frontend/dist/classforge-frontend."
}
Copy-Item $Browser (Join-Path $Stage "frontend") -Recurse

Copy-Item (Join-Path $Root "deploy\aws\nginx") $Stage -Recurse
Copy-Item (Join-Path $Root "deploy\aws\systemd") $Stage -Recurse
Copy-Item (Join-Path $Root "deploy\aws\classforge.env.example") $Stage
Copy-Item (Join-Path $Root "deploy\aws\install-origin.sh") $Stage
Copy-Item (Join-Path $Root "deploy\aws\install-whisper.sh") $Stage
Copy-Item (Join-Path $Root "deploy\aws\iam") $Stage -Recurse

$OutputPath = if ([IO.Path]::IsPathRooted($Output)) { $Output } else { Join-Path $Root $Output }
if (Test-Path $OutputPath) { Remove-Item -Force $OutputPath }
Compress-Archive -Path (Join-Path $Stage "*") -DestinationPath $OutputPath -CompressionLevel Optimal
Remove-Item -Recurse -Force $Stage

Write-Host "`nAWS deploy bundle: $OutputPath"
Get-FileHash -Algorithm SHA256 $OutputPath | Format-List
