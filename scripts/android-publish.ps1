[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$VmIp,
    [Parameter(Mandatory)][string]$KeyFile,
    [string]$SshUser = 'azureuser',
    [string]$KnownHostsFile,
    [string]$ReleaseDirectory,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$NodeCommand = 'node',
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $ReleaseDirectory) { $ReleaseDirectory = Join-Path $repoRoot '.android-release' }
& $NodeCommand (Join-Path $PSScriptRoot 'android-release.mjs') validate $ReleaseDirectory
if ($LASTEXITCODE -ne 0) { throw 'Release preflight rejected; no SSH connection attempted.' }
if ($VmIp -notmatch '^[a-zA-Z0-9.-]+$' -or $SshUser -notmatch '^[a-z_][a-z0-9_-]*$') { throw 'Invalid SSH target.' }
if (-not $AndroidSdk) { $AndroidSdk = Join-Path $repoRoot '.demo/android-sdk' }
$apksigner = Join-Path $AndroidSdk 'build-tools/35.0.0/apksigner.bat'
$priorJava = $env:JAVA_HOME
try {
    if ($JavaHome) { $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path }
    $signature = & $apksigner verify --print-certs (Join-Path $ReleaseDirectory 'classforge.apk') 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Actual APK signature verification failed.' }
} finally { $env:JAVA_HOME = $priorJava }
$metadata = Get-Content -LiteralPath (Join-Path $ReleaseDirectory 'release.json') -Raw | ConvertFrom-Json
$actualSigner = [regex]::Match(($signature -join "`n"),'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})').Groups[1].Value
if (-not $actualSigner -or $actualSigner.ToUpperInvariant() -ne $metadata.signingCertificateSha256.Replace(':','').ToUpperInvariant()) { throw 'APK signer differs from published association.' }
if ($CheckOnly) { Write-Host 'Release preflight, checksum, signature and association passed; no SSH connection attempted.'; return }
$KeyFile = (Resolve-Path -LiteralPath $KeyFile).Path
$options = @('-i',$KeyFile,'-o','BatchMode=yes','-o','ConnectTimeout=10','-o','StrictHostKeyChecking=yes')
if ($KnownHostsFile) { $options += @('-o',"UserKnownHostsFile=$((Resolve-Path -LiteralPath $KnownHostsFile).Path)") }
$target = "$SshUser@$VmIp"
$remoteStage = '/tmp/classforge-android-' + [guid]::NewGuid().ToString('N')
& ssh @options $target "mkdir -m 700 '$remoteStage'"
if ($LASTEXITCODE -ne 0) { throw 'Unable to create remote Android staging directory.' }
$files = @('classforge.apk','classforge.apk.sha256','assetlinks.json','release.json') | ForEach-Object { Join-Path $ReleaseDirectory $_ }
$files += Join-Path $repoRoot 'deploy/azure/vm/install-android.sh'
& scp @options @files "${target}:$remoteStage/"
if ($LASTEXITCODE -ne 0) { throw 'Android release transfer failed.' }
& ssh @options $target "sudo bash '$remoteStage/install-android.sh' '$remoteStage'"
if ($LASTEXITCODE -ne 0) { throw 'Remote Android installation failed.' }
$base = $metadata.origin
$links = (Invoke-WebRequest -Uri "$base/.well-known/assetlinks.json").Content
$publicLinks = Join-Path $ReleaseDirectory 'assetlinks.public.json'
[IO.File]::WriteAllText($publicLinks,$links,[Text.UTF8Encoding]::new($false))
& $NodeCommand (Join-Path $PSScriptRoot 'android-release.mjs') verify com.classforge.app $actualSigner $publicLinks
if ($LASTEXITCODE -ne 0) { throw 'Published association does not match the signed APK.' }
$download = Join-Path $ReleaseDirectory 'downloaded-public.apk'
Invoke-WebRequest -Uri "$base/android/classforge.apk" -OutFile $download
if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash.ToLowerInvariant() -ne $metadata.apkSha256) { throw 'Public APK checksum mismatch.' }
& (Join-Path $PSScriptRoot 'azure-smoke.ps1') -BaseUrl $base
Write-Host "APK published and verified: $base/android/classforge.apk"
