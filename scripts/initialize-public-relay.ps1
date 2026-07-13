$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$configDir = Join-Path $root "output\config"
$keyPath = Join-Path $configDir "device-registration-key.txt"
New-Item -ItemType Directory -Force -Path $configDir | Out-Null

if (!(Test-Path $keyPath)) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $key = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    Set-Content -LiteralPath $keyPath -Value $key -NoNewline
}

Write-Host "Set this secret as DEVICE_REGISTRATION_KEY on the public relay host:"
Write-Output (Get-Content -Raw -LiteralPath $keyPath).Trim()
Write-Host ""
Write-Host "After deployment, build with:"
Write-Host '.\start-private-chat.cmd https://your-relay.example'
