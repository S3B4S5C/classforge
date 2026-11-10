[CmdletBinding()]
param(
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$NodeCommand = 'node'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$privateDir = Join-Path $repoRoot '.android-release'
$signingPath = Join-Path $privateDir 'signing.local.json'
if (-not (Test-Path -LiteralPath $signingPath)) { throw 'Run scripts/android-create-signing.ps1 once before a release build.' }
if (-not $AndroidSdk) { $AndroidSdk = Join-Path $repoRoot '.demo/android-sdk' }
$AndroidSdk = (Resolve-Path -LiteralPath $AndroidSdk).Path
$aapt = Join-Path $AndroidSdk 'build-tools/35.0.0/aapt.exe'
$apksigner = Join-Path $AndroidSdk 'build-tools/35.0.0/apksigner.bat'
if (-not (Test-Path -LiteralPath $aapt)) { throw 'Install Android platform36 and build-tools35.0.0 in -AndroidSdk.' }
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Set -JavaHome to JDK21 (or JAVA_HOME).' }
$signing = Get-Content -LiteralPath $signingPath -Raw | ConvertFrom-Json
$keyPath = Join-Path $privateDir $signing.keyStore
if (-not (Test-Path -LiteralPath $keyPath)) { throw 'Configured signing keystore is missing. Restore it instead of creating another key.' }
$names = @('JAVA_HOME','ANDROID_HOME','CLASSFORGE_ANDROID_KEYSTORE','CLASSFORGE_ANDROID_STORE_PASSWORD','CLASSFORGE_ANDROID_KEY_ALIAS','CLASSFORGE_ANDROID_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $env:ANDROID_HOME = $AndroidSdk
    $env:CLASSFORGE_ANDROID_KEYSTORE = $keyPath
    $env:CLASSFORGE_ANDROID_STORE_PASSWORD = $signing.storePassword
    $env:CLASSFORGE_ANDROID_KEY_ALIAS = $signing.keyAlias
    $env:CLASSFORGE_ANDROID_KEY_PASSWORD = $signing.keyPassword
    & $NodeCommand --test (Join-Path $PSScriptRoot 'android-release.test.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Android release contract tests failed.' }
    Push-Location (Join-Path $repoRoot 'android')
    try {
        & ./gradlew.bat :app:assembleRelease :app:lintRelease --no-daemon --max-workers=1
        if ($LASTEXITCODE -ne 0) { throw 'Android release build/lint failed.' }
    } finally { Pop-Location }
    $apk = Join-Path $privateDir 'classforge.apk'
    Copy-Item -LiteralPath (Join-Path $repoRoot 'android/app/build/outputs/apk/release/app-release.apk') -Destination $apk -Force
    $verification = & $apksigner verify --verbose --print-certs $apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $digestMatch = [regex]::Match(($verification -join "`n"),'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})')
    if (-not $digestMatch.Success) { throw 'Unable to determine the actual APK signer.' }
    $fingerprint = $digestMatch.Groups[1].Value.ToUpperInvariant()
    $badging = (& $aapt dump badging $apk) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='com.classforge.app'" -or $badging -notmatch "sdkVersion:'24'" -or $badging -notmatch "targetSdkVersion:'36'") { throw 'APK package/SDK differs from the release contract.' }
    & $NodeCommand (Join-Path $PSScriptRoot 'android-release.mjs') create com.classforge.app $fingerprint (Join-Path $privateDir 'assetlinks.json')
    if ($LASTEXITCODE -ne 0) { throw 'Unable to produce assetlinks.json.' }
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$apk.sha256","$hash  classforge.apk`n")
    $metadata = [ordered]@{packageName='com.classforge.app';versionName='1.0.0';versionCode=1;minSdk=24;targetSdk=36;origin='https://classforge-tiago-15124.eastus.cloudapp.azure.com';launchUrl='https://classforge-tiago-15124.eastus.cloudapp.azure.com/projects';apkSha256=$hash;signingCertificateSha256=$fingerprint;builtAtUtc=[DateTime]::UtcNow.ToString('o')}
    [IO.File]::WriteAllText((Join-Path $privateDir 'release.json'),($metadata | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    Write-Host "Verified signed APK: $apk"
    Write-Host "APK SHA256: $hash"
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
