param(
    [string]$BaseUrl = "http://127.0.0.1:8150/api/v1",
    [int]$TimeoutSec = 600
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "Invoke-Utf8Json.ps1")

function New-UnicodeString([int[]]$CodePoints) {
    return -join ($CodePoints | ForEach-Object { [char]$_ })
}

function Get-Utf8Hex([string]$Value) {
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    return (($utf8.GetBytes($Value) | ForEach-Object { $_.ToString("X2") }) -join " ")
}

$requirement = New-UnicodeString @(0x751F,0x6210,0x4E00,0x4E2A,0x5BA2,0x6237,0x7BA1,0x7406,0x540E,0x53F0,0xFF0C,0x5305,0x542B,0x5BA2,0x6237,0x5217,0x8868,0x3001,0x5BA2,0x6237,0x8BE6,0x60C5,0x548C,0x5BA2,0x6237,0x7F16,0x8F91,0x8868,0x5355,0x3002)
$suffix = Get-Date -Format 'yyyyMMddHHmmss'

Write-Host '=== Login ==='
$login = Invoke-Utf8JsonRestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body @{
    account = 'admin'
    password = 'admin123'
}
$token = $login.data.accessToken
$headers = @{ Authorization = "Bearer $token" }

Write-Host '=== Workspace ==='
$ws = Invoke-Utf8JsonRestMethod -Uri "$BaseUrl/workspaces" -Method Post -Headers $headers -Body @{
    name = "Phase12 UTF8 Final $suffix"
    description = 'Phase 12 AI_DIRECT acceptance'
}

Write-Host '=== App: UTF8 DeepSeek CRM Final ==='
$app = Invoke-Utf8JsonRestMethod -Uri "$BaseUrl/apps" -Method Post -Headers $headers -Body @{
    workspaceId = [long]$ws.data.id
    name = 'UTF8 DeepSeek CRM Final'
    description = $requirement
    appType = 'WEB_APP'
}
$appId = [long]$app.data.id

Write-Host '=== Generation Task ==='
Write-Host "Requirement HEX: $(Get-Utf8Hex $requirement)"
$task = Invoke-Utf8JsonRestMethod -Uri "$BaseUrl/generation-tasks" -Method Post -Headers $headers -Body @{
    workspaceId = [long]$ws.data.id
    appId = $appId
    taskType = 'RULE_GENERATION'
    requirement = $requirement
    idempotencyKey = "phase12-final-$suffix"
}
$taskId = [long]$task.data.taskId
Write-Host "appId=$appId taskId=$taskId taskStatus=$($task.data.taskStatus)"

$deadline = (Get-Date).AddSeconds($TimeoutSec)
do {
    Start-Sleep -Seconds 5
    $detail = Invoke-Utf8JsonRestMethod -Uri "$BaseUrl/generation-tasks/$taskId" -Method Get -Headers $headers
    $status = $detail.data.taskStatus
    Write-Host "poll: taskStatus=$status errorCode=$($detail.data.errorCode)"
    if ($status -in @('SUCCESS', 'FAILED', 'CANCELLED')) { break }
} while ((Get-Date) -lt $deadline)

$events = Invoke-Utf8JsonRestMethod -Uri "$BaseUrl/generation-tasks/$taskId/events" -Method Get -Headers $headers
$successEvent = @($events.data | Where-Object { $_.eventType -eq 'TASK_SUCCESS' }) | Select-Object -Last 1
$failedEvent = @($events.data | Where-Object { $_.eventType -eq 'TASK_FAILED' }) | Select-Object -Last 1

$result = [ordered]@{
    appId = $appId
    taskId = $taskId
    requirement = $requirement
    requirementHex = (Get-Utf8Hex $requirement)
    taskStatus = $detail.data.taskStatus
    errorCode = $detail.data.errorCode
    errorMessage = $detail.data.errorMessage
    successPayload = if ($successEvent) { $successEvent.eventPayloadJson } else { $null }
    failedPayload = if ($failedEvent) { $failedEvent.eventPayloadJson } else { $null }
}

$out = Join-Path $PSScriptRoot 'phase12-final-result.json'
($result | ConvertTo-Json -Depth 6) | Set-Content -Path $out -Encoding UTF8
Write-Host "Saved $out"
$result | ConvertTo-Json -Depth 6
