[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ResourceGroup,
    [Parameter(Mandatory = $true)][string]$ParametersFile,
    [string]$Location = 'brazilsouth',
    [ValidateSet('Validate','WhatIf','Create')][string]$Mode = 'Validate'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$parameterPath = (Resolve-Path -LiteralPath $ParametersFile).Path
$configuration = Get-Content -LiteralPath $parameterPath -Raw | ConvertFrom-Json
foreach ($name in @('vmName','adminUsername','sshPublicKey','adminPublicIp','dnsLabel')) {
    if ([string]::IsNullOrWhiteSpace($configuration.parameters.$name.value)) { throw "Complete $name in the VM parameter file." }
}
$address = $null
$adminIp = $configuration.parameters.adminPublicIp.value
if (-not [Net.IPAddress]::TryParse($adminIp,[ref]$address) -or
    $address.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork -or $adminIp -eq '0.0.0.0' -or
    $adminIp -notmatch '^\d{1,3}(\.\d{1,3}){3}$') {
    throw 'adminPublicIp must be a single public IPv4, without a /CIDR or wildcard.'
}
if ($configuration.parameters.sshPublicKey.value -notmatch '^ssh-(ed25519|rsa)\s+[A-Za-z0-9+/=]+(?:\s|$)') {
    throw 'Provide an OpenSSH PUBLIC key (ssh-ed25519 or ssh-rsa), never the private key.'
}
foreach ($name in @('vmName','adminUsername','dnsLabel')) {
    if ($configuration.parameters.$name.value -notmatch '^[a-z][a-z0-9-]{1,38}[a-z0-9]$') { throw "Invalid lowercase name: $name" }
}
if (-not (Get-Command az -ErrorAction SilentlyContinue)) { throw 'Install Azure CLI and sign in with az login.' }
& az account show --output none --only-show-errors
if ($LASTEXITCODE -ne 0) { throw 'Azure login/subscription is not ready.' }
if ($Mode -eq 'Create') {
    Write-Host 'Creating/updating VM, static public IP and disk; Azure charges may apply.'
    & az group create --name $ResourceGroup --location $Location --output none --only-show-errors
    if ($LASTEXITCODE -ne 0) { throw 'Resource group creation failed.' }
}
$operation = switch ($Mode) { 'Validate' {'validate'} 'WhatIf' {'what-if'} 'Create' {'create'} }
& az deployment group $operation --resource-group $ResourceGroup --name classforge-azure-vm `
    --template-file (Join-Path $repoRoot 'deploy/azure/vm/main.bicep') `
    --parameters "@$parameterPath" --output none --only-show-errors
if ($LASTEXITCODE -ne 0) { throw "Azure VM $Mode failed." }
if ($Mode -eq 'Create') {
    & az deployment group show --resource-group $ResourceGroup --name classforge-azure-vm --query properties.outputs --output json --only-show-errors
    if ($LASTEXITCODE -ne 0) { throw 'Could not read VM hostname/IP outputs.' }
}
Write-Host "VM $Mode complete; no application or runtime secrets were sent by this script."
