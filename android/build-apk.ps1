param(
    [string]$SdkPath = $env:ANDROID_HOME,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
if (!$SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

$buildTools = Join-Path $SdkPath 'build-tools\36.0.0'
$signer = Join-Path $buildTools 'apksigner.bat'
if (!(Test-Path -LiteralPath $signer)) { throw 'Install Android SDK Build Tools 36.0.0.' }

if (!$env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK installation.' }
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
$env:ANDROID_HOME = $SdkPath

$gradle = Join-Path $PSScriptRoot 'gradlew.bat'
$gradleArgs = @('--no-daemon', '-p', $PSScriptRoot, ':app:assembleRelease')
if ($Offline) { $gradleArgs += '--offline' }
& $gradle @gradleArgs
if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }

$signingDir = Join-Path $PSScriptRoot '.signing'
$keystore = Join-Path $signingDir 'taskflow.p12'
$passwordFile = Join-Path $signingDir 'password'
$unsigned = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
$apk = Join-Path $PSScriptRoot 'build\TaskFlow.apk'

if ((Test-Path -LiteralPath $keystore) -and !(Test-Path -LiteralPath $passwordFile)) {
    throw 'Restore the original keystore and password together before signing another build.'
}

$oldPassword = $env:TASKFLOW_APK_KEY_PASSWORD
try {
    if (!(Test-Path -LiteralPath $passwordFile)) {
        New-Item -ItemType Directory -Path $signingDir -Force | Out-Null
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $bytes = New-Object byte[] 32
            $random.GetBytes($bytes)
            [IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($bytes))
        } finally {
            $random.Dispose()
        }
    }
    $env:TASKFLOW_APK_KEY_PASSWORD = [IO.File]::ReadAllText($passwordFile)
    if (!(Test-Path -LiteralPath $keystore)) {
        & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias taskflow `
            -storepass:env TASKFLOW_APK_KEY_PASSWORD -keyalg RSA -keysize 3072 `
            -validity 10000 -dname 'CN=TaskFlow'
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the APK signing key.' }
    }

    & $signer sign --ks $keystore --ks-key-alias taskflow `
        --ks-pass env:TASKFLOW_APK_KEY_PASSWORD --out $apk $unsigned
    if ($LASTEXITCODE -ne 0) { throw 'Could not sign the APK.' }

    & $signer verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    Write-Host "APK: $apk"
} finally {
    $env:TASKFLOW_APK_KEY_PASSWORD = $oldPassword
}
