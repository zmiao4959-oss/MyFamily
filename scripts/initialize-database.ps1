$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    docker compose up -d postgres
    docker compose run --rm api alembic upgrade head
    docker compose exec -T api alembic current
} finally {
    Pop-Location
}
