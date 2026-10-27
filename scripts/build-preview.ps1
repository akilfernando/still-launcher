$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
Push-Location $project
$originalJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    New-Item -ItemType Directory -Path '.gradle/tmp','dist' -Force | Out-Null
    $socketPath = (Join-Path $project '.gradle/tmp').Replace('\','/')
    $env:JAVA_TOOL_OPTIONS = "$originalJavaOptions -Djdk.net.unixdomain.tmpdir=$socketPath".Trim()
    $sdkPath = (Join-Path $env:LOCALAPPDATA 'Android/Sdk').Replace('\','/')
    if (!(Test-Path 'local.properties')) { Set-Content 'local.properties' "sdk.dir=$sdkPath" }
    New-Item -ItemType Directory -Path '.gradle/policy-tests' -Force | Out-Null
    & javac -d .gradle/policy-tests app/src/main/java/com/still/launcher/Essentials.java tests/EssentialsTest.java app/src/main/java/com/still/launcher/GestureRules.java tests/GestureRulesTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Selection test compilation failed.' }
    & java -cp .gradle/policy-tests com.still.launcher.EssentialsTest
    if ($LASTEXITCODE -ne 0) { throw 'Selection policy checks failed.' }
    & java -cp .gradle/policy-tests com.still.launcher.GestureRulesTest
    if ($LASTEXITCODE -ne 0) { throw 'Gesture classification checks failed.' }
    & .\gradlew.bat assembleDebug assembleRelease lintRelease --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build or lint failed.' }
    # Local test certificate only. No production signing secret is used or embedded.
    $signer = Join-Path $sdkPath 'build-tools/34.0.0/apksigner.bat'
    & $signer sign --ks "$env:USERPROFILE/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out 'dist/StillLauncher-preview.apk' 'app/build/outputs/apk/release/app-release-unsigned.apk'
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & $signer verify --verbose 'dist/StillLauncher-preview.apk'
    if ($LASTEXITCODE -ne 0) { throw 'APK signature validation failed.' }
    Get-FileHash 'dist/StillLauncher-preview.apk' -Algorithm SHA256
} finally {
    $env:JAVA_TOOL_OPTIONS = $originalJavaOptions
    Pop-Location
}
