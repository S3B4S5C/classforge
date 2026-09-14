param(
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"
}
$flutter = (Get-Command flutter -ErrorAction Stop).Source
$reportDir = Join-Path $root "backend\build\reports\demo"
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

function Run([string]$Label, [scriptblock]$Command) {
    Write-Host ""
    Write-Host "==> $Label"
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE." }
    Write-Host "[ok] $Label"
}

Push-Location (Join-Path $root "backend")
try {
    Run "CU-27 deterministic demo scenario" { .\gradlew.bat demoScenarioAcceptance --no-daemon }
    Run "CU-10/CU-11 XMI regression" { .\gradlew.bat xmiEnterpriseArchitectAcceptance --no-daemon }
    Run "CU-19 generated Assistant regression" { .\gradlew.bat generatedAssistantAcceptance --no-daemon }
    Run "CU-18 Flutter regression" { .\gradlew.bat generatedFlutterAcceptance "-PflutterCommand=$flutter" --no-daemon }
    Run "CU-17 Angular regression" { .\gradlew.bat generatedAngularAcceptance --no-daemon }
    Run "CU-16 Domain Manifest regression" { .\gradlew.bat domainManifestAcceptance --no-daemon }
    Run "CU-15 OpenAPI/Postman regression" { .\gradlew.bat springApiArtifactsAcceptance --no-daemon }
    Run "CU-14 CRUD/Auth regression" { .\gradlew.bat springCrudGenerationAcceptance --no-daemon }
    Run "CU-13 Spring generation regression" { .\gradlew.bat springGenerationAcceptance --no-daemon }
    Run "Backend test suite" { .\gradlew.bat test --no-daemon }
} finally { Pop-Location }

Push-Location (Join-Path $root "frontend")
try {
    Run "Frontend generation tests" { npm.cmd run test:generation }
    Run "ClassForge Angular production build" { npm.cmd run build }
} finally { Pop-Location }

Push-Location $root
try {
    Run "git diff --check" { git diff --check }
} finally { Pop-Location }

$report = [ordered]@{
    schemaVersion = "1.0"
    useCase = "CU-27"
    status = "PASS"
    scenario = "Veterinaria CU-27"
    projectId = "27000000-0000-0000-0000-000000000100"
    generatedAt = (Get-Date).ToString("o")
    gates = @(
        "demoScenarioAcceptance", "xmiEnterpriseArchitectAcceptance", "generatedAssistantAcceptance",
        "generatedFlutterAcceptance", "generatedAngularAcceptance", "domainManifestAcceptance",
        "springApiArtifactsAcceptance", "springCrudGenerationAcceptance", "springGenerationAcceptance",
        "backendTest", "frontendGenerationTests", "frontendBuild", "gitDiffCheck"
    )
}
$report | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 (Join-Path $reportDir "cu27-acceptance.json")
Write-Host ""
Write-Host "CU-27 automated acceptance GREEN."
Write-Host "Report: $reportDir\cu27-acceptance.json"
