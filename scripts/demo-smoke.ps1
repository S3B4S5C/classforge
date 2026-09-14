param(
    [string]$BaseUrl = "http://127.0.0.1:8082",
    [switch]$RequireAi
)

$ErrorActionPreference = "Stop"
$ownerEmail = "demo@classforge.local"
$password = "classforge-demo"
$projectId = "27000000-0000-0000-0000-000000000100"

function Invoke-Json([string]$Method, [string]$Path, $Body = $null, [string]$Token = "") {
    $headers = @{ Accept = "application/json" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $params = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }
    return Invoke-RestMethod @params
}

$login = Invoke-Json POST "/api/auth/login" @{
    email = $ownerEmail
    password = $password
}
if (-not $login.accessToken) { throw "Demo login did not return an access token." }

$projects = @(Invoke-Json GET "/api/projects" $null $login.accessToken)
$project = $projects | Where-Object { $_.id -eq $projectId }
if (-not $project) { throw "Deterministic CU-27 project $projectId was not found." }
if ($project.name -ne "Veterinaria CU-27") { throw "Unexpected demo project name: $($project.name)" }
if ($project.accessRole -ne "OWNER") { throw "Demo owner does not have OWNER role." }
if (@($project.document.umlModel.classes).Count -ne 6) { throw "Expected 6 demo classes." }
if (@($project.document.umlModel.relationships).Count -ne 5) { throw "Expected 5 demo relationships." }

$health = Invoke-Json GET "/api/projects/$projectId/assistant/health" $null $login.accessToken
Write-Host "[ok] Demo login + deterministic veterinary project"
Write-Host "[ok] Project: $($project.name) / revision $($project.revision)"
Write-Host "[info] Assistant text : $($health.readyForText)"
Write-Host "[info] Assistant voice: $($health.readyForVoice)"
Write-Host "[info] Assistant image: $($health.readyForImage)"

if ($RequireAi -and (-not $health.readyForText -or -not $health.readyForVoice -or -not $health.readyForImage)) {
    throw "One or more local AI runtimes are not ready. Start llama :8092, whisper :8093 and vision :8094."
}

Write-Host "CU-27 demo smoke GREEN."
