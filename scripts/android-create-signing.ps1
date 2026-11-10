[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$privateDir = Join-Path $repoRoot '.android-release'
$configPath = Join-Path $privateDir 'signing.local.json'
$keyPath = Join-Path $privateDir 'classforge-release.jks'
if ((Test-Path -LiteralPath $configPath) -or (Test-Path -LiteralPath $keyPath)) {
    throw 'Signing material already exists. Preserve it for updates; do not regenerate it.'
}
$keytool = if ($JavaHome) { Join-Path $JavaHome 'bin/keytool.exe' } else { (Get-Command keytool).Source }
if (-not (Test-Path -LiteralPath $keytool)) { throw 'Set -JavaHome to a JDK containing keytool.' }
New-Item -ItemType Directory -Path $privateDir -Force | Out-Null
if ($env:OS -eq 'Windows_NT') {
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($sid)
    $acl.SetAccessRuleProtection($true,$false)
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    Set-Acl -LiteralPath $privateDir -AclObject $acl
}
$password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$priorPassword = $env:CLASSFORGE_ANDROID_STORE_PASSWORD
try {
    $env:CLASSFORGE_ANDROID_STORE_PASSWORD = $password
    & $keytool -genkeypair -keystore $keyPath -storetype JKS -alias classforge -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=ClassForge' -storepass:env CLASSFORGE_ANDROID_STORE_PASSWORD -keypass:env CLASSFORGE_ANDROID_STORE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Release key generation failed.' }
    $config = @{keyStore='classforge-release.jks';keyAlias='classforge';storePassword=$password;keyPassword=$password}
    [IO.File]::WriteAllText($configPath,($config | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    & $keytool -exportcert -keystore $keyPath -alias classforge -storepass:env CLASSFORGE_ANDROID_STORE_PASSWORD -file (Join-Path $privateDir 'signing-certificate.der')
    if ($LASTEXITCODE -ne 0) { throw 'Certificate export failed.' }
    Write-Host 'Private release signing created. Back up .android-release/signing.local.json and classforge-release.jks together outside Git.'
} finally { $env:CLASSFORGE_ANDROID_STORE_PASSWORD = $priorPassword }
