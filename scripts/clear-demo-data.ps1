param(
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
$base = $ServerUrl.TrimEnd('/')
$pair = Invoke-RestMethod -Uri "$base/pair" -Method Post -ContentType "application/json" -Body (@{ pairing_token = $PairingToken; device_name = "clear-demo-script" } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($pair.access_token)" }
try {
    $result = Invoke-RestMethod -Uri "$base/demo" -Method Delete -Headers $headers
    if ($result.cleared) { Write-Host "Fictional demo data cleared." }
} finally {
    try { Invoke-RestMethod -Uri "$base/pair/revoke" -Method Post -Headers $headers | Out-Null } catch { }
    $PairingToken = $null
}
