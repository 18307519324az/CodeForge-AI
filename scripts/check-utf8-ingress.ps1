param(
    [string]$BaseUrl = "http://127.0.0.1:8150/api/v1"
)

$ErrorActionPreference = "Stop"

function New-UnicodeString([int[]]$CodePoints) {
    return -join ($CodePoints | ForEach-Object { [char]$_ })
}

function Get-Utf8Hex([string]$Value) {
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    return (($utf8.GetBytes($Value) | ForEach-Object { $_.ToString("X2") }) -join " ")
}

function Invoke-Utf8JsonRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [Parameter(Mandatory = $true)]
        [hashtable]$BodyObject,
        [hashtable]$Headers = @{}
    )

    $json = $BodyObject | ConvertTo-Json -Depth 10
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $bytes = $utf8.GetBytes($json)

    Write-Host "=== Request ===" -ForegroundColor Cyan
    Write-Host "URI: $Uri"
    Write-Host "JSON: $json"
    Write-Host "UTF8_HEX: $(Get-Utf8Hex $json)"

    $requestHeaders = @{
        "Content-Type" = "application/json; charset=utf-8"
        "Accept" = "application/json"
    }
    foreach ($key in $Headers.Keys) {
        $requestHeaders[$key] = $Headers[$key]
    }

    $response = Invoke-WebRequest -Uri $Uri -Method $Method -Headers $requestHeaders -Body $bytes
    $responseText = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())

    Write-Host "=== Response ===" -ForegroundColor Yellow
    Write-Host "StatusCode: $($response.StatusCode)"
    Write-Host "Body: $responseText"
    return $responseText | ConvertFrom-Json
}

$suffix = Get-Date -Format "yyyyMMddHHmmss"
$account = "utf8user$suffix"
$password = "password123"
$email = "utf8-$suffix@example.com"
$displayName = New-UnicodeString @(0x4E2D, 0x6587, 0x6D4B, 0x8BD5, 0x7528, 0x6237)
$workspaceName = New-UnicodeString @(0x4E2D, 0x6587, 0x5DE5, 0x4F5C, 0x7A7A, 0x95F4)
$workspaceDescription = New-UnicodeString @(0x4E2D, 0x6587, 0x63CF, 0x8FF0)
$appName = New-UnicodeString @(0x5F85, 0x529E, 0x6E05, 0x5355, 0x9875, 0x9762)
$appDescription = New-UnicodeString @(0x5305, 0x542B, 0x4E2D, 0x6587, 0x5B57, 0x6BB5)
$appType = "Web " + (New-UnicodeString @(0x5E94, 0x7528))

$registerResponse = Invoke-Utf8JsonRequest `
    -Method Post `
    -Uri "$BaseUrl/auth/register" `
    -BodyObject @{
        account = $account
        password = $password
        confirmPassword = $password
        displayName = $displayName
        email = $email
    }

Write-Host "REGISTER_DISPLAY_NAME: $($registerResponse.data.user.displayName)" -ForegroundColor Green

$loginResponse = Invoke-Utf8JsonRequest `
    -Method Post `
    -Uri "$BaseUrl/auth/login" `
    -BodyObject @{
        account = $account
        password = $password
    }

$accessToken = $loginResponse.data.accessToken

$workspaceResponse = Invoke-Utf8JsonRequest `
    -Method Post `
    -Uri "$BaseUrl/workspaces" `
    -Headers @{ Authorization = "Bearer $accessToken" } `
    -BodyObject @{
        name = $workspaceName
        description = $workspaceDescription
    }

Write-Host "WORKSPACE_NAME: $($workspaceResponse.data.name)" -ForegroundColor Green
Write-Host "WORKSPACE_DESCRIPTION: $($workspaceResponse.data.description)" -ForegroundColor Green

$appResponse = Invoke-Utf8JsonRequest `
    -Method Post `
    -Uri "$BaseUrl/apps" `
    -Headers @{ Authorization = "Bearer $accessToken" } `
    -BodyObject @{
        workspaceId = $workspaceResponse.data.id
        name = $appName
        description = $appDescription
        appType = $appType
    }

Write-Host "APP_NAME: $($appResponse.data.name)" -ForegroundColor Green
Write-Host "APP_DESCRIPTION: $($appResponse.data.description)" -ForegroundColor Green
Write-Host "APP_TYPE: $($appResponse.data.appType)" -ForegroundColor Green
