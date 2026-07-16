$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    docker compose up -d --build
    docker compose ps
    Write-Host "本地服务已启动：http://127.0.0.1:8080/health"
} finally {
    Pop-Location
}
