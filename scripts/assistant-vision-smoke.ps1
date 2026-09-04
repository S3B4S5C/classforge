param(
    [string]$VisionUrl = 'http://127.0.0.1:8094',
    [string]$VisionModel = 'vision-model',
    [string]$ImagePath = '.\backend\src\test\resources\assistant\vision\class-simple.png'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Fail([string]$Message) { throw $Message }
function Require([bool]$Condition, [string]$Message) { if (-not $Condition) { Fail $Message } }

$base = $VisionUrl.TrimEnd('/')
Require (Test-Path -LiteralPath $ImagePath) ('No existe la imagen smoke: ' + $ImagePath)

Write-Host '==> Vision runtime health' -ForegroundColor Cyan
$health = Invoke-RestMethod -Method Get -Uri ($base + '/health') -TimeoutSec 5
Require ($health.status -eq 'ok') ('/health no esta READY: ' + ($health | ConvertTo-Json -Compress))

Write-Host '==> Vision model identity' -ForegroundColor Cyan
$models = Invoke-RestMethod -Method Get -Uri ($base + '/v1/models') -TimeoutSec 5
$matchingModel = @($models.data | Where-Object { $_.id -eq $VisionModel -and ([string]$_.owned_by).ToLowerInvariant().Contains('llama') })
Require ($matchingModel.Count -gt 0) ('No se encontro el alias esperado en /v1/models: ' + $VisionModel)

Write-Host '==> Vision modality' -ForegroundColor Cyan
$props = Invoke-RestMethod -Method Get -Uri ($base + '/props') -TimeoutSec 5
Require ($null -ne $props.modalities) '/props no contiene modalities.'
Require ([bool]$props.modalities.vision) 'El runtime responde, pero modalities.vision != true.'

Write-Host '==> Multimodal base64 smoke' -ForegroundColor Cyan
$bytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $ImagePath))
$dataUrl = 'data:image/png;base64,' + [Convert]::ToBase64String($bytes)

$schema = [ordered]@{
    type = 'object'
    additionalProperties = $false
    properties = [ordered]@{
        visible = [ordered]@{ type = 'boolean' }
        className = [ordered]@{ type = 'string' }
    }
    required = @('visible', 'className')
}

$payload = [ordered]@{
    model = $VisionModel
    stream = $false
    temperature = 0
    max_tokens = 80
    messages = @(
        [ordered]@{
            role = 'user'
            content = @(
                [ordered]@{
                    type = 'text'
                    text = 'Inspecciona la imagen. Si contiene un cuadro de clase UML legible devuelve visible=true y className exactamente como aparece. Si no puedes leer una clase, devuelve visible=false y className vacio. No adivines nombres.'
                },
                [ordered]@{
                    type = 'image_url'
                    image_url = [ordered]@{ url = $dataUrl }
                }
            )
        }
    )
    response_format = [ordered]@{
        type = 'json_object'
        schema = $schema
    }
    reasoning_effort = 'none'
    chat_template_kwargs = [ordered]@{
        enable_thinking = $false
    }
}

$response = Invoke-RestMethod `
    -Method Post `
    -Uri ($base + '/v1/chat/completions') `
    -ContentType 'application/json' `
    -Body ($payload | ConvertTo-Json -Depth 20 -Compress) `
    -TimeoutSec 90

$choice = $response.choices[0]
Require ($null -ne $choice) 'llama.cpp no devolvio choices[0].'
Require ([string]$choice.finish_reason -ne 'length') 'La respuesta visual quedo truncada por max_tokens.'

$content = [string]$choice.message.content
Require (-not [string]::IsNullOrWhiteSpace($content)) 'llama.cpp no devolvio message.content.'
try {
    $result = $content | ConvertFrom-Json
} catch {
    $preview = $content.Replace("`r", ' ').Replace("`n", ' ').Trim()
    if ($preview.Length -gt 500) { $preview = $preview.Substring(0, 500) + '...' }
    Fail ('llama.cpp devolvio contenido no JSON aunque se solicito schema-constrained output. Raw content: ' + $preview)
}
Require ([bool]$result.visible) 'El endpoint acepto la imagen pero no reconocio el fixture Cliente.'
Require ([string]$result.className -eq 'Cliente') ('Respuesta visual inesperada: ' + $content)

Write-Host ''
Write-Host 'Vision smoke OK: health + identity + modalities.vision + base64 image.' -ForegroundColor Green
