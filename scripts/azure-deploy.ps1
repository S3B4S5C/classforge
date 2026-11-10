[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ResourceGroup,
    [Parameter(Mandatory = $true)][string]$AppName,
    [string]$Artifact = '.azure-deploy/app.jar'
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command az -ErrorAction SilentlyContinue)) { throw 'Install Azure CLI and run az login first.' }
$cliVersionJson = & az version --output json --only-show-errors
if ($LASTEXITCODE -ne 0) { throw 'Could not read Azure CLI version.' }
if ([version](($cliVersionJson -join "`n" | ConvertFrom-Json).'azure-cli') -lt [version]'2.48.1') {
    throw 'Azure CLI 2.48.1 or later is required for Microsoft Entra deployment without SCM basic auth.'
}
$artifactPath = (Resolve-Path -LiteralPath $Artifact).Path
if (-not (Test-Path -LiteralPath "$artifactPath.sha256")) { throw 'Artifact SHA256 file is missing. Run azure-build-package.ps1.' }
$expectedHash = ((Get-Content -LiteralPath "$artifactPath.sha256" -Raw).Trim() -split '\s+')[0]
if ((Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Artifact SHA256 mismatch.' }
& az webapp deploy --resource-group $ResourceGroup --name $AppName --src-path $artifactPath --type jar `
    --timeout 600000 --output none --only-show-errors
if ($LASTEXITCODE -ne 0) { throw 'Azure JAR deployment failed.' }
$hostname = & az webapp show --resource-group $ResourceGroup --name $AppName --query defaultHostName -o tsv --only-show-errors
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($hostname)) { throw 'Could not resolve deployed hostname.' }
& (Join-Path $PSScriptRoot 'azure-smoke.ps1') -BaseUrl "https://$hostname"
