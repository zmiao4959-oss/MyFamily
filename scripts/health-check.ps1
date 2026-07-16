param([string]$ServerUrl = "http://127.0.0.1:8080")
$ErrorActionPreference = "Stop"
$health = Invoke-RestMethod -Uri "$($ServerUrl.TrimEnd('/'))/health" -TimeoutSec 10
if ($health.status -ne "ok" -or $health.database -ne "ok") { throw "Server health check failed." }
$health | ConvertTo-Json
