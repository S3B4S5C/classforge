[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ResourceGroup,
    [Parameter(Mandatory = $true)][string]$ParametersFile,
    [string]$Location = 'brazilsouth',
    [ValidateSet('Validate', 'WhatIf', 'Create')][string]$Mode = 'Validate'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not (Get-Command az -ErrorAction SilentlyContinue)) { throw 'Install Azure CLI and run az login first.' }
$cliVersionJson = & az version --output json --only-show-errors
if ($LASTEXITCODE -ne 0) { throw 'Could not read Azure CLI version.' }
if ([version](($cliVersionJson -join "`n" | ConvertFrom-Json).'azure-cli') -lt [version]'2.48.1') {
    throw 'Azure CLI 2.48.1 or later is required for Microsoft Entra deployment without SCM basic auth.'
}
$parameterPath = (Resolve-Path -LiteralPath $ParametersFile).Path
$configuration = Get-Content -LiteralPath $parameterPath -Raw | ConvertFrom-Json
foreach ($name in @('appName', 'jwtSecret', 'mongoUri', 'groqApiKey')) {
    if ([string]::IsNullOrWhiteSpace($configuration.parameters.$name.value)) { throw "Complete $name in the ignored local parameter file." }
}
if ($configuration.parameters.jwtSecret.value.Length -lt 64) { throw 'JWT secret must contain at least 64 characters.' }
if ($configuration.parameters.mongoUri.value -notmatch '^mongodb(?:\+srv)?://[^/]+/[^?/]+' -and
    $configuration.parameters.mongoUri.value -notmatch '^@Microsoft.KeyVault\(') {
    throw 'Mongo URI must specify a database explicitly (for example /classforge).'
}
& az account show --output none --only-show-errors
if ($LASTEXITCODE -ne 0) { throw 'Azure login/subscription is not ready.' }
if ($Mode -eq 'Create') {
    Write-Host 'Creating/updating Azure resources. Basic App Service has a cost.'
    & az group create --name $ResourceGroup --location $Location --output none --only-show-errors
    if ($LASTEXITCODE -ne 0) { throw 'Resource group creation failed.' }
}
$operation = switch ($Mode) { 'Validate' { 'validate' } 'WhatIf' { 'what-if' } 'Create' { 'create' } }
& az deployment group $operation --resource-group $ResourceGroup --name classforge-azure `
    --template-file (Join-Path $repoRoot 'deploy/azure/main.bicep') `
    --parameters "@$parameterPath" --output none --only-show-errors
if ($LASTEXITCODE -ne 0) { throw "Azure $Mode failed. Review errors in the portal; do not publish parameter contents." }
Write-Host "Azure $Mode completed."
if ($Mode -eq 'Create') {
    $appName = $configuration.parameters.appName.value
    & az webapp show --resource-group $ResourceGroup --name $appName `
        --query '{host:defaultHostName,outbound:outboundIpAddresses,possibleOutbound:possibleOutboundIpAddresses}' --output json
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the non-secret hostname/outbound IPs.' }
    Write-Host 'Allow the outbound IPs in Atlas before deploying the JAR.'
}
