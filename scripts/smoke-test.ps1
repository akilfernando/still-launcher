param([string]$Serial = 'emulator-5556', [switch]$PhysicalDevice)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$' -and !$PhysicalDevice) {
    throw 'Physical-device tests require explicit -PhysicalDevice and user authorization.'
}
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
function AdbCommand([string[]]$CommandArgs) {
    $result = & $adb -s $Serial @CommandArgs
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $CommandArgs" }
    return $result
}
function ReadUi {
    # One UI can expose the incoming tree before its transition accepts taps.
    if ($PhysicalDevice) { Start-Sleep -Milliseconds 1000 }
    AdbCommand @('shell','uiautomator','dump','/sdcard/still-ui.xml') | Out-Null
    [xml]($xmlText = (AdbCommand @('shell','cat','/sdcard/still-ui.xml')) -join "`n")
}
function FindNode([xml]$Ui, [string]$Text, [string]$Class = '') {
    $description = switch ($Text) {
        'STILL' { 'Still settings' }
        'All apps  →' { 'All apps' }
        '←  Home' { 'Home' }
        'Search apps' { 'Search installed apps' }
        default { '' }
    }
    if ($Text -eq 'Settings' -and $Class -eq 'android.widget.Button') { $description = 'Still settings' }
    # Never reuse coordinates from the outgoing screen after a Home transition.
    $Ui = ReadUi
    $node = $null
    for ($attempt = 0; $attempt -lt 4; $attempt++) {
        $node = $Ui.SelectNodes('//node') | Where-Object {
            $_.package -eq 'com.still.launcher' -and (
                ($description -and $_.'content-desc' -eq $description) -or
                (!$description -and $_.text -eq $Text -and (!$Class -or $_.class -eq $Class)))
        } | Select-Object -First 1
        if ($node) { break }
        # Package labels load off the main thread; wait for the observable UI.
        $Ui = ReadUi
    }
    if (!$node) { throw "Missing UI node: $Text $Class" }
    return $node
}
function TapNode($Node) {
    $points = [regex]::Matches($Node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    $x = [int](($points[0] + $points[2]) / 2)
    $y = [int](($points[1] + $points[3]) / 2)
    AdbCommand @('shell','input','tap',"$x","$y") | Out-Null
}
function Check([bool]$Condition, [string]$Label) {
    if (!$Condition) { throw "FAIL: $Label" }
    Write-Output "PASS: $Label"
}

$originalHome = ((AdbCommand @('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')) |
    Where-Object { $_ -match '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$' } | Select-Object -Last 1)
if (!$originalHome) { throw 'Could not identify the original Home app; refusing to change it.' }
try {
AdbCommand @('shell','cmd','package','set-home-activity','com.still.launcher/.MainActivity') | Out-Null
AdbCommand @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
$ui = ReadUi
FindNode $ui 'STILL' | Out-Null
Write-Output 'PASS: registered launcher responds to Home'
TapNode (FindNode $ui 'All apps  →')
$ui = ReadUi
TapNode (FindNode $ui 'Search apps' 'android.widget.EditText')
$ui = ReadUi
$field = FindNode $ui 'Search apps' 'android.widget.EditText'
Check ($field.focused -eq 'true') 'search receives focus'
AdbCommand @('shell','input','text','settings') | Out-Null
$ui = ReadUi
FindNode $ui 'Settings' 'android.widget.TextView' | Out-Null
$star = $ui.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq 'Add Settings to home' } | Select-Object -First 1
if ($star) { TapNode $star }
$ui = ReadUi
TapNode (FindNode $ui '←  Home')
$ui = ReadUi
$favorite = FindNode $ui 'Settings' 'android.widget.TextView'
Write-Output 'PASS: search and adding favorite'
TapNode $favorite
$ui = ReadUi
Check ($null -ne ($ui.SelectNodes('//node') | Where-Object { $_.package -eq 'com.android.settings' } | Select-Object -First 1)) 'favorite launches Settings'
AdbCommand @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
$ui = ReadUi
FindNode $ui 'YOUR ESSENTIALS' | Out-Null
Write-Output 'PASS: Home returns to favorites'
AdbCommand @('shell','am','force-stop','com.still.launcher') | Out-Null
AdbCommand @('shell','am','start','-n','com.still.launcher/.MainActivity') | Out-Null
$ui = ReadUi
FindNode $ui 'Settings' 'android.widget.TextView' | Out-Null
Write-Output 'PASS: favorites survive process restart'
TapNode (FindNode $ui 'All apps  →')
$ui = ReadUi
TapNode (FindNode $ui 'Search apps' 'android.widget.EditText')
$ui = ReadUi
Check ((FindNode $ui 'Search apps' 'android.widget.EditText').focused -eq 'true') 'search refocuses'
AdbCommand @('shell','input','text','zzzznotanapp') | Out-Null
$ui = ReadUi
FindNode $ui 'No matching apps.' | Out-Null
FindNode $ui '←  Home' | Out-Null
Write-Output 'PASS: empty search and Home button remain visible with keyboard'
AdbCommand @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
$ui = ReadUi
FindNode $ui 'YOUR ESSENTIALS' | Out-Null
Write-Output 'PASS: Home intent clears drawer and search'
TapNode (FindNode $ui 'Settings' 'android.widget.Button')
$ui = ReadUi
TapNode (FindNode $ui 'Battery guide')
$ui = ReadUi
FindNode $ui 'Battery guide' | Out-Null
Write-Output 'PASS: Samsung guide opens'
AdbCommand @('shell','input','keyevent','KEYCODE_BACK') | Out-Null
$services = (AdbCommand @('shell','dumpsys','activity','services','com.still.launcher')) -join "`n"
$unexpectedServices = @($services -split "`n" | Where-Object { $_ -match 'ServiceRecord' -and $_ -notmatch 'StillGestureService' })
Check ($unexpectedServices.Count -eq 0) 'no services other than the optional gesture accessibility service'
$crashes = (AdbCommand @('logcat','-d','-s','AndroidRuntime:E')) -join "`n"
Check ($crashes -notmatch 'Process: com.still.launcher') 'no launcher crashes in device log'
} finally {
    AdbCommand @('shell','cmd','package','set-home-activity',$originalHome) | Out-Null
    $restored = (AdbCommand @('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')) -join "`n"
    if (!$restored.Contains($originalHome)) { throw 'Original Home app restoration could not be verified.' }
    Write-Output 'PASS: original default Home app restored'
}
