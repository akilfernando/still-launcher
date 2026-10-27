param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
function RunAdb([string[]]$CommandArgs) {
    $value = & $adb -s $Serial @CommandArgs
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $CommandArgs" }
    return $value
}
function Ui {
    Start-Sleep -Milliseconds 800
    RunAdb @('shell','uiautomator','dump','/sdcard/still-order-ui.xml') | Out-Null
    return [xml]((RunAdb @('shell','cat','/sdcard/still-order-ui.xml')) -join "`n")
}
function Node([xml]$Tree,[string]$Text) {
    $description = if ($Text -eq 'All apps  →') { 'All apps' } else { '' }
    $result = $Tree.SelectNodes('//node') | Where-Object {
        $_.package -eq 'com.still.launcher' -and ($_.text -eq $Text -or ($description -and $_.'content-desc' -eq $description))
    } | Select-Object -First 1
    if (!$result) { throw "Missing UI text: $Text" }
    return $result
}
function Touch($Target,[switch]$Long) {
    $p = [regex]::Matches($Target.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    $x = [string][int](($p[0]+$p[2])/2); $y = [string][int](($p[1]+$p[3])/2)
    if ($Long) { RunAdb @('shell','input','swipe',$x,$y,$x,$y,'900') | Out-Null }
    else { RunAdb @('shell','input','tap',$x,$y) | Out-Null }
}
function Home {
    RunAdb @('shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.HOME','-n','com.still.launcher/.MainActivity') | Out-Null
    return Ui
}
function Rows([xml]$Tree) {
    return @($Tree.SelectNodes('//node[@class="android.widget.ListView"]/node/node[@class="android.widget.TextView"]'))
}
$addedName = $null
$didSwap = $false
try {
    $tree = Home
    $original = @(Rows $tree)
    if ($original.Count -lt 2) {
        Touch (Node $tree 'All apps  →')
        $drawer = Ui
        $star = $drawer.SelectNodes('//node') | Where-Object { $_.package -eq 'com.still.launcher' -and $_.'content-desc' -like 'Add * to home' } | Select-Object -First 1
        if (!$star) { throw 'No available test app in visible drawer' }
        $addedName = $star.'content-desc'.Substring(4)
        $addedName = $addedName.Substring(0, $addedName.Length - 8)
        Touch $star
        $tree = Home
    }
    $before = @(Rows $tree)
    if ($before.Count -lt 2) { throw 'Need two Essentials for ordering test' }
    $first = [string]$before[0].text
    $second = [string]$before[1].text
    Touch $before[0] -Long
    $menu = Ui
    if ($menu.SelectNodes('//node') | Where-Object { $_.text -eq 'Move up' }) { throw 'First item offers invalid Move up' }
    Touch (Node $menu 'Move down')
    $didSwap = $true
    $after = @(Rows (Ui))
    if ($after[0].text -ne $second -or $after[1].text -ne $first) { throw 'Move down did not reorder Home' }
    'PASS: first item offers only valid movement; Move down reorders Home'
    RunAdb @('shell','am','force-stop','com.still.launcher') | Out-Null
    $after = @(Rows (Home))
    if ($after[0].text -ne $second -or $after[1].text -ne $first) { throw 'Order did not survive restart' }
    'PASS: reordered Home survives process restart'
    Touch $after[1] -Long
    Touch (Node (Ui) 'Move up')
    $restored = @(Rows (Ui))
    if ($restored[0].text -ne $first -or $restored[1].text -ne $second) { throw 'Move up did not restore order' }
    $didSwap = $false
    'PASS: Move up restores original order'
} finally {
    if ($didSwap) {
        $tree = Home
        $moved = @(Rows $tree) | Where-Object { $_.text -eq $first } | Select-Object -First 1
        Touch $moved -Long
        Touch (Node (Ui) 'Move up')
    }
    if ($addedName) {
        $tree = Home
        Touch (Node $tree 'All apps  →')
        $drawer = Ui
        $remove = $drawer.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq "Remove $addedName from home" } | Select-Object -First 1
        if (!$remove) { throw 'Temporary test favorite needs manual removal' }
        Touch $remove
        $tree = Home
        if (@(Rows $tree).Count -ne $original.Count) { throw 'Original favorite count not restored' }
        'PASS: temporary test favorite removed; original selection restored'
    }
}
