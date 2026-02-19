$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$gradlewPath = ".\gradlew.bat"
$apkPath = ".\app\build\outputs\apk\debug\app-debug.apk"

# Check ADB
if (-not (Test-Path $adbPath)) {
    Write-Host "Error: ADB not found at $adbPath" -ForegroundColor Red
    exit 1
}

Write-Host "Building APK (assembleDebug)..." -ForegroundColor Cyan
& $gradlewPath assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build Failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

if (-not (Test-Path $apkPath)) {
    Write-Host "Error: APK not found at $apkPath" -ForegroundColor Red
    exit 1
}

Write-Host "Build Successful. Discovering devices..." -ForegroundColor Cyan

# Get Device List (Skip header 'List of devices attached', and empty lines)
$devicesOutput = & $adbPath devices
$deviceSerials = @()

foreach ($line in $devicesOutput) {
    if ($line -match "^(\S+)\s+device$") {
        $deviceSerials += $matches[1]
    }
}

if ($deviceSerials.Count -eq 0) {
    Write-Host "No connected devices found." -ForegroundColor Yellow
    Write-Host "Please connect devices via USB or use 'setup_wireless.ps1' to pair first."
    exit 0
}

Write-Host "Found $($deviceSerials.Count) device(s): $($deviceSerials -join ', ')" -ForegroundColor Green

foreach ($serial in $deviceSerials) {
    Write-Host "Installing to device: $serial..." -ForegroundColor Cyan
    # -r = replace existing application
    # -t = allow test packages
    & $adbPath -s $serial install -r -t $apkPath
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Success on $serial!" -ForegroundColor Green
    } else {
        Write-Host "Failed to install on $serial." -ForegroundColor Red
    }
}

Write-Host "Done." -ForegroundColor Cyan
