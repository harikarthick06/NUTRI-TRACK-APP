# One-command NutriTrack setup with Docker (Windows PowerShell).
# Creates .env with random secrets if needed, builds and starts the app, then prints its address.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker Desktop is required: https://docs.docker.com/desktop/setup/install/windows-install/"
}
docker info *> $null
if ($LASTEXITCODE -ne 0) { Write-Error "Docker is installed but not running. Start Docker Desktop and try again." }

if (-not (Test-Path .env)) { Copy-Item .env.example .env }

function New-Secret([int]$bytes) {
    $buf = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buf)
    return $buf
}
function New-Alnum([int]$length) {
    $chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return -join ((New-Secret $length) | ForEach-Object { $chars[$_ % $chars.Length] })
}
# Sets KEY to a value only when it is missing, empty or still the example placeholder.
function Set-IfMissing([string]$key, [string]$value) {
    $lines = @(Get-Content .env)
    $line = $lines | Where-Object { $_ -match "^$key=" } | Select-Object -Last 1
    $current = if ($line) { $line.Substring($key.Length + 1) } else { "" }
    if ([string]::IsNullOrWhiteSpace($current) -or $current -eq "change-me") {
        $lines = @($lines | Where-Object { $_ -notmatch "^$key=" }) + "$key=$value"
        [System.IO.File]::WriteAllLines((Join-Path $PSScriptRoot ".env"), $lines)
        Write-Host "Generated $key in .env"
    }
}

Set-IfMissing "DB_PASSWORD" (New-Alnum 24)
Set-IfMissing "MYSQL_ROOT_PASSWORD" (New-Alnum 24)
Set-IfMissing "JWT_SECRET" ([Convert]::ToBase64String((New-Secret 48)))

$portLine = Get-Content .env | Where-Object { $_ -match "^APP_PORT=" } | Select-Object -Last 1
$port = if ($portLine) { $portLine.Substring(9) } else { "3000" }

Write-Host "Building and starting NutriTrack (the first run downloads images and takes a few minutes)..."
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { Write-Error "docker compose failed. See the output above." }

Write-Host -NoNewline "Waiting for the app to be ready"
for ($i = 0; $i -lt 120; $i++) {
    try {
        Invoke-WebRequest -Uri "http://localhost:$port/api/nutrients" -UseBasicParsing -TimeoutSec 2 | Out-Null
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) {
            Write-Host ""
            Write-Host "NutriTrack is running: http://localhost:$port"
            Write-Host "Stop it with: docker compose down   (your data is kept in the db-data volume)"
            exit 0
        }
    }
    Write-Host -NoNewline "."
    Start-Sleep -Seconds 2
}
Write-Host ""
Write-Error "The app didn't start. See the logs with: docker compose logs backend"
