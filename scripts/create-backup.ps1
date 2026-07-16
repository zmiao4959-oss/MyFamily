param(
    [string]$ServerUrl = "http://127.0.0.1:8080",
    [string]$PairingToken = $env:FAMILY_MEMORY_PAIRING_TOKEN,
    [string]$OutputDirectory = (Join-Path (Get-Location) "backups")
)
$ErrorActionPreference = "Stop"
function Read-PlainSecret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
if ([string]::IsNullOrWhiteSpace($PairingToken)) { $PairingToken = Read-PlainSecret "管理员配对令牌" }
$password = Read-PlainSecret "备份密码（至少 10 位，请妥善保存）"
if ($password.Length -lt 10) { throw "备份密码至少需要 10 位" }
$base = $ServerUrl.TrimEnd('/')
$pair = Invoke-RestMethod -Uri "$base/pair" -Method Post -ContentType "application/json" -Body (@{ pairing_token = $PairingToken; device_name = "backup-script" } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($pair.access_token)" }
try {
    $backup = Invoke-RestMethod -Uri "$base/backup/create" -Method Post -Headers $headers -ContentType "application/json" -Body (@{ password = $password } | ConvertTo-Json)
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $destination = Join-Path $OutputDirectory $backup.filename
    Invoke-WebRequest -Uri "$base/backup/$($backup.id)/download" -Headers $headers -OutFile $destination
    Write-Host "加密备份已保存：$destination"
    Write-Host "人物 $($backup.persons_count)，记录 $($backup.records_count)，媒体 $($backup.media_count)"
} finally {
    try { Invoke-RestMethod -Uri "$base/pair/revoke" -Method Post -Headers $headers | Out-Null } catch { }
    $password = $null
    $PairingToken = $null
}
