param(
    [string]$BaseUrl = "http://127.0.0.1:8150/api/v1"
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "Invoke-Utf8Json.ps1")

function Get-Utf8Hex([string]$Value) {
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    return (($utf8.GetBytes($Value) | ForEach-Object { $_.ToString("X2") }) -join " ")
}

$requirement = "生成一个客户管理后台，包含客户列表、客户详情和客户编辑表单。"
Write-Host "Requirement UTF-8 HEX: $(Get-Utf8Hex $requirement)"

$suffix = Get-Date -Format "yyyyMMddHHmmss"
$login = Invoke-Utf8JsonRestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body @{
    account = "admin"
    password = "admin123"
}
$token = $login.data.accessToken

$ws = Invoke-Utf8JsonRestMethod -Uri "$BaseUrl/workspaces" -Method Post -Headers @{
    Authorization = "Bearer $token"
} -Body @{
    name = "UTF8 Regression $suffix"
    description = "PowerShell UTF-8 regression"
}

$app = Invoke-Utf8JsonRestMethod -Uri "$BaseUrl/apps" -Method Post -Headers @{
    Authorization = "Bearer $token"
} -Body @{
    workspaceId = [long]$ws.data.id
    name = "UTF8 Regression App $suffix"
    description = $requirement
    appType = "WEB_APP"
}

$task = Invoke-Utf8JsonRestMethod -Uri "$BaseUrl/generation-tasks" -Method Post -Headers @{
    Authorization = "Bearer $token"
} -Body @{
    workspaceId = [long]$ws.data.id
    appId = [long]$app.data.id
    taskType = "RULE_GENERATION"
    requirement = $requirement
    idempotencyKey = "utf8-regression-$suffix"
}

Write-Host "Created taskId=$($task.data.taskId)"
Write-Host "Regression payload sent with UTF-8 bytes (no ??? expected in DB requirement)."
