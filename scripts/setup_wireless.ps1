$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$gradlewPath = ".\gradlew.bat"

# Check if ADB exists
if (-not (Test-Path $adbPath)) {
    Write-Host "Error: ADB not found at $adbPath" -ForegroundColor Red
    exit 1
}

Write-Host "Checking for connected Android devices..." -ForegroundColor Cyan
$devices = & $adbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match "\w+\s+device" }

if ($devices) {
    Write-Host "Device found: $devices" -ForegroundColor Green
} else {
    Write-Host "No connected devices found." -ForegroundColor Yellow
    $userInput = Read-Host "Enter Wireless Debugging IP:Port (e.g. 10.0.0.23:44455) [Press Enter to skip/cancel]"
    
    if (-not [string]::IsNullOrWhiteSpace($userInput)) {
        Write-Host "Connecting to $userInput..." -ForegroundColor Cyan
        & $adbPath connect $userInput
        
        # Verify connection
        $devices = & $adbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match "\w+\s+device" }
        if (-not $devices) {
            Write-Host "Failed to connect. Please check IP:Port or Pair again." -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "No IP provided. Aborting." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Building and Installing App..." -ForegroundColor Cyan
& $gradlewPath installDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "Success! App installed." -ForegroundColor Green
} else {
    Write-Host "Build/Install Failed." -ForegroundColor Red
    exit $LASTEXITCODE
}
