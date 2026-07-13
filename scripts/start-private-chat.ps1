param(
    [string]$RelayUrl = $env:BETWEEN_RELAY_URL,
    [switch]$NoOpen,
    [switch]$LocalDev
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Get-PrimaryLanIp {
    return Get-NetIPConfiguration |
        Where-Object { $_.IPv4DefaultGateway -and $_.IPv4Address } |
        ForEach-Object { $_.IPv4Address.IPAddress } |
        Where-Object { $_ -notlike "169.254.*" -and $_ -ne "127.0.0.1" } |
        Select-Object -First 1
}

function Test-PortListening([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Start-NpmService([string]$Name, [string[]]$Arguments, [int]$Port) {
    if (Test-PortListening $Port) {
        Write-Host "$Name already running on port $Port"
        return
    }

    New-Item -ItemType Directory -Force -Path "output\logs" | Out-Null
    Start-Process `
        -FilePath "npm.cmd" `
        -ArgumentList $Arguments `
        -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $root "output\logs\$Name.out.log") `
        -RedirectStandardError (Join-Path $root "output\logs\$Name.err.log") `
        -WindowStyle Hidden | Out-Null
}

if (!(Test-Path "node_modules")) {
    npm install
}

$configDir = Join-Path $root "output\config"
$registrationKeyPath = Join-Path $configDir "device-registration-key.txt"
$relayUrlPath = Join-Path $configDir "relay-url.txt"
New-Item -ItemType Directory -Force -Path $configDir | Out-Null

if (!(Test-Path $registrationKeyPath)) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $key = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    Set-Content -LiteralPath $registrationKeyPath -Value $key -NoNewline
}
$registrationKey = (Get-Content -Raw -LiteralPath $registrationKeyPath).Trim()

if ($LocalDev) {
    $pcIp = Get-PrimaryLanIp
    if (!$pcIp) { throw "Could not detect a LAN IP for local development." }
    $RelayUrl = "http://$pcIp`:8787"
} elseif ([string]::IsNullOrWhiteSpace($RelayUrl) -and (Test-Path $relayUrlPath)) {
    $RelayUrl = (Get-Content -Raw -LiteralPath $relayUrlPath).Trim()
}

if ([string]::IsNullOrWhiteSpace($RelayUrl)) {
    throw "A public relay URL is required. Run: .\start-private-chat.cmd https://your-relay.example"
}
$RelayUrl = $RelayUrl.TrimEnd('/')
if (!$LocalDev -and !$RelayUrl.StartsWith("https://")) {
    throw "Remote use requires an HTTPS relay URL so chat, codes, and files use encrypted WSS."
}
if (!$LocalDev) {
    Set-Content -LiteralPath $relayUrlPath -Value $RelayUrl -NoNewline
}

$apkPath = Join-Path $root "android\app\build\outputs\apk\debug\app-debug.apk"
$buildFingerprintPath = Join-Path $configDir "android-build-fingerprint.txt"
$latestAndroidWrite = Get-ChildItem -Path "android\app\src", "android\app\build.gradle.kts" -Recurse -File |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1 -ExpandProperty LastWriteTimeUtc
$fingerprintInput = "$RelayUrl|$registrationKey|$($latestAndroidWrite.Ticks)"
$fingerprint = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprintInput)))
$existingFingerprint = if (Test-Path $buildFingerprintPath) {
    (Get-Content -Raw -LiteralPath $buildFingerprintPath).Trim()
} else { "" }

if (!(Test-Path $apkPath) -or $existingFingerprint -ne $fingerprint) {
    Write-Host "Building Android APK for $RelayUrl..."
    Push-Location "android"
    try {
        & .\gradlew.bat :app:assembleDebug `
            "-PpocBackendUrl=$RelayUrl" `
            "-PpocRegistrationKey=$registrationKey"
        if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
    Set-Content -LiteralPath $buildFingerprintPath -Value $fingerprint -NoNewline
}

if ($LocalDev) {
    $env:DEVICE_REGISTRATION_KEY = $registrationKey
    $env:VITE_BACKEND_URL = $RelayUrl
    Start-NpmService -Name "backend" -Arguments @("--workspace", "backend", "run", "dev") -Port 8787
    Start-NpmService -Name "web" -Arguments @("--workspace", "web", "run", "dev", "--", "--host", "0.0.0.0") -Port 5173
    $webUrl = "http://127.0.0.1:5173/login"
} else {
    $webUrl = "$RelayUrl/login"
}

if (!$NoOpen) { Start-Process $webUrl }

Write-Host ""
Write-Host "Relay/site:    $RelayUrl"
Write-Host "Configured APK: $apkPath"
Write-Host "Install/update: adb install -r `"$apkPath`""
Write-Host ""
Write-Host "The phone and browser may now use unrelated Wi-Fi/mobile networks."
Write-Host "The deployed relay must use the DEVICE_REGISTRATION_KEY from:"
Write-Host $registrationKeyPath
