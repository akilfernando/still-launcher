$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$buildTools = Join-Path $sdk 'build-tools/34.0.0'
$androidJar = Join-Path $sdk 'platforms/android-35/android.jar'
$output = Join-Path $project '.gradle/device-fixture'
New-Item -ItemType Directory -Force "$output/classes","$output/dex" | Out-Null
& javac -source 8 -target 8 -classpath $androidJar -d "$output/classes" "$project/tests/device-fixture/TestActivity.java"
if ($LASTEXITCODE -ne 0) { throw 'Fixture Java compilation failed.' }
& "$buildTools/d8.bat" --lib $androidJar --min-api 28 --output "$output/dex" "$output/classes/com/still/launcher/regression/TestActivity.class"
if ($LASTEXITCODE -ne 0) { throw 'Fixture dex compilation failed.' }
& "$buildTools/aapt2.exe" link -I $androidJar --manifest "$project/tests/device-fixture/AndroidManifest.xml" -o "$output/unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Fixture packaging failed.' }
& jar uf "$output/unsigned.apk" -C "$output/dex" classes.dex
if ($LASTEXITCODE -ne 0) { throw 'Fixture dex packaging failed.' }
& "$buildTools/apksigner.bat" sign --ks "$env:USERPROFILE/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$output/test-app.apk" "$output/unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Fixture signing failed.' }
Write-Output "$output/test-app.apk"
