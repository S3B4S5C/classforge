param(
    [Parameter(Mandatory = $true)]
    [string]$RepositoryPath,
    [string]$BaseUrl = "http://127.0.0.1:8082"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$root = Split-Path -Parent $PSScriptRoot
$projectId = "27000000-0000-0000-0000-000000000100"
$workDir = Join-Path $root ".demo\ea"
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

if (-not (Test-Path $RepositoryPath)) {
    throw "Enterprise Architect repository not found: $RepositoryPath. Use a disposable .qea/.eapx/.eap repository."
}

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body (@{
    email = "demo@classforge.local"
    password = "classforge-demo"
} | ConvertTo-Json -Compress)
$token = $login.accessToken
if (-not $token) { throw "Could not authenticate against the CU-27 demo backend." }

$classforgeXmi = Join-Path $workDir "classforge-veterinaria.xmi"
$eaRoundTrip = Join-Path $workDir "enterprise-architect-roundtrip.xmi"
Invoke-WebRequest -Method Get -Uri "$BaseUrl/api/projects/$projectId/xmi/export" `
    -Headers @{ Authorization = "Bearer $token" } -OutFile $classforgeXmi -UseBasicParsing

$repository = $null
try {
    try {
        $repository = New-Object -ComObject EA.Repository
    } catch {
        throw "Enterprise Architect Automation COM object (EA.Repository) is not registered for this PowerShell architecture. Start the matching 64/32-bit PowerShell or install EA Automation support."
    }

    if (-not $repository.OpenFile((Resolve-Path $RepositoryPath).Path)) {
        throw "Enterprise Architect could not open repository: $RepositoryPath"
    }

    $modelName = "ClassForge CU27 Smoke " + (Get-Date -Format "yyyyMMdd-HHmmss")
    $model = $repository.Models.AddNew($modelName, "")
    if (-not $model.Update()) { throw "EA could not create smoke model: $($model.GetLastError())" }
    $repository.Models.Refresh()

    $project = $repository.GetProjectInterface()
    $packageXmlGuid = $project.GUIDtoXML($model.PackageGUID)
    $importResult = $project.ImportPackageXMI($packageXmlGuid, $classforgeXmi, 0, 1)
    if ($importResult) { throw "EA ImportPackageXMI failed: $importResult" }

    # xmiEA21 = 11 in the Enterprise Architect XMIType enumeration.
    $exportResult = $project.ExportPackageXMI($packageXmlGuid, 11, 0, -1, 1, 0, $eaRoundTrip)
    if ($exportResult) { throw "EA ExportPackageXMI failed: $exportResult" }
    if (-not (Test-Path $eaRoundTrip)) { throw "EA did not produce the round-trip XMI file." }
} finally {
    if ($repository) {
        try { $repository.CloseFile() } catch {}
        try { $repository.Exit() } catch {}
        [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($repository) | Out-Null
    }
}

$handler = New-Object System.Net.Http.HttpClientHandler
$client = New-Object System.Net.Http.HttpClient($handler)
$client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $token)
$multipart = New-Object System.Net.Http.MultipartFormDataContent
$stream = [System.IO.File]::OpenRead($eaRoundTrip)
try {
    $content = New-Object System.Net.Http.StreamContent($stream)
    $content.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("application/xml")
    $multipart.Add($content, "file", "enterprise-architect-roundtrip.xmi")
    $response = $client.PostAsync("$BaseUrl/api/projects/$projectId/xmi/import/preview", $multipart).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) { throw "ClassForge rejected EA round-trip XMI: HTTP $([int]$response.StatusCode) $body" }
    $preview = $body | ConvertFrom-Json
} finally {
    $stream.Dispose()
    $multipart.Dispose()
    $client.Dispose()
    $handler.Dispose()
}

if ($preview.classCount -lt 6) { throw "EA round-trip returned fewer than 6 supported classes." }
if ($preview.relationshipCount -lt 5) { throw "EA round-trip returned fewer than 5 supported relationships." }

$report = [ordered]@{
    schemaVersion = "1.0"
    status = "PASS"
    repository = (Resolve-Path $RepositoryPath).Path
    classForgeExport = $classforgeXmi
    enterpriseArchitectExport = $eaRoundTrip
    classCount = $preview.classCount
    attributeCount = $preview.attributeCount
    relationshipCount = $preview.relationshipCount
    checkedAt = (Get-Date).ToString("o")
}
$reportPath = Join-Path $workDir "ea-smoke.json"
$report | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $reportPath

Write-Host "Enterprise Architect CU-27 smoke GREEN."
Write-Host "EA re-export was accepted by ClassForge preview: $($preview.classCount) classes / $($preview.relationshipCount) relationships."
Write-Host "Report: $reportPath"
