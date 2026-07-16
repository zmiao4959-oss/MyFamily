param(
    [Parameter(Mandatory = $true)][string]$BackupId,
    [string]$ServerUrl = "http://127.0.0.1:8080",
    [string]$PairingToken = $env:FAMILY_MEMORY_PAIRING_TOKEN
)
$ErrorActionPreference = "Stop"
function Read-PlainSecret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
if ([string]::IsNullOrWhiteSpace($PairingToken)) { $PairingToken = Read-PlainSecret "Administrator pairing token" }
$password = Read-PlainSecret "Backup password"
$base = $ServerUrl.TrimEnd('/')
$pair = Invoke-RestMethod -Uri "$base/pair" -Method Post -ContentType "application/json" -Body (@{ pairing_token = $PairingToken; device_name = "restore-script" } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($pair.access_token)" }
try {
    $body = @{ password = $password } | ConvertTo-Json
    $manifest = Invoke-RestMethod -Uri "$base/backup/$BackupId/inspect" -Method Post -Headers $headers -ContentType "application/json" -Body $body
    Write-Host "Backup date: $($manifest.created_at)"
    Write-Host "People $($manifest.counts.persons), records $($manifest.counts.records), media $($manifest.counts.media)"
    Write-Warning "Restore replaces server data after first creating a safety backup."
    if ((Read-Host "Type RESTORE to continue") -ne "RESTORE") { Write-Host "Cancelled"; return }
    $result = Invoke-RestMethod -Uri "$base/backup/$BackupId/restore" -Method Post -Headers $headers -ContentType "application/json" -Body (@{ password = $password; confirmation = "RESTORE" } | ConvertTo-Json)
    Write-Host "Restore complete. Safety backup ID: $($result.safety_backup_id)"
} finally {
    try { Invoke-RestMethod -Uri "$base/pair/revoke" -Method Post -Headers $headers | Out-Null } catch { }
    $password = $null
    $PairingToken = $null
}
