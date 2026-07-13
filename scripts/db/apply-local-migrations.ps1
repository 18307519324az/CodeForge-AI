param(
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }),
    [int]$DbPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'codeforge_ai' }),
    [string]$DbUser = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'codeforge_ai_user' }),
    [string]$DbPassword = $env:DB_PASSWORD
)

$ErrorActionPreference = 'Stop'

$checkScript = Join-Path $PSScriptRoot 'check-local-schema.ps1'
. $checkScript

$status = & powershell -NoProfile -File $checkScript `
    -DbHost $DbHost -DbPort $DbPort -DbName $DbName -DbUser $DbUser -DbPassword $DbPassword 2>&1 |
    Select-Object -Last 1

Write-Host "Schema check status: $status"

if ($status -eq 'READY') {
    Write-Host 'No migration apply required.'
    exit 0
}

if ($status -eq 'HISTORY_MISMATCH') {
    Write-Error 'Schema/history drift detected. Resolve manually before apply. Flyway repair and DB reset are not automated here.'
    exit 2
}

if ($status -ne 'MISSING') {
    Write-Error "Unexpected schema status: $status"
    exit 3
}

if (-not $DbPassword) {
    throw 'DB_PASSWORD environment variable is required'
}

function Invoke-MysqlStatement {
    param([string]$Sql)

    $mysqlExe = Get-MysqlExecutable
    $mysqlArgs = New-MysqlArgumentList -HostName $DbHost -Port $DbPort -UserName $DbUser -DatabaseName $DbName -Sql $Sql
    $previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')

    try {
        $env:MYSQL_PWD = $DbPassword
        $mysqlOutput = & $mysqlExe @mysqlArgs 2>&1
        $mysqlExitCode = $LASTEXITCODE
    }
    finally {
        if ($null -eq $previousMysqlPwd) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        }
        else {
            $env:MYSQL_PWD = $previousMysqlPwd
        }
    }

    if ($mysqlExitCode -ne 0) {
        throw 'MYSQL_QUERY_FAILED'
    }

    if ($mysqlOutput) {
        $mysqlOutput | Out-Null
    }
}

function Get-Count {
    param([string]$Sql)
    return [int](Invoke-MysqlScalarQuery -Sql $Sql -HostName $DbHost -Port $DbPort -DatabaseName $DbName -UserName $DbUser -Password $DbPassword)
}

$statements = @(
    @{
        Check = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '$DbName' AND TABLE_NAME = 'generation_task' AND COLUMN_NAME = 'prompt_template_id'"
        Sql   = 'ALTER TABLE generation_task ADD COLUMN prompt_template_id BIGINT NULL'
    },
    @{
        Check = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '$DbName' AND TABLE_NAME = 'generation_task' AND COLUMN_NAME = 'prompt_template_version_id'"
        Sql   = 'ALTER TABLE generation_task ADD COLUMN prompt_template_version_id BIGINT NULL'
    },
    @{
        Check = "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '$DbName' AND TABLE_NAME = 'generation_task' AND INDEX_NAME = 'idx_generation_task_prompt_template'"
        Sql   = 'CREATE INDEX idx_generation_task_prompt_template ON generation_task (prompt_template_id)'
    },
    @{
        Check = "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '$DbName' AND TABLE_NAME = 'generation_task' AND INDEX_NAME = 'idx_generation_task_prompt_template_version'"
        Sql   = 'CREATE INDEX idx_generation_task_prompt_template_version ON generation_task (prompt_template_version_id)'
    },
    @{
        Check = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '$DbName' AND TABLE_NAME = 'model_call_log' AND COLUMN_NAME = 'system_prompt_sha256'"
        Sql   = 'ALTER TABLE model_call_log ADD COLUMN system_prompt_sha256 VARCHAR(64) NULL'
    },
    @{
        Check = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '$DbName' AND TABLE_NAME = 'model_call_log' AND COLUMN_NAME = 'user_prompt_sha256'"
        Sql   = 'ALTER TABLE model_call_log ADD COLUMN user_prompt_sha256 VARCHAR(64) NULL'
    },
    @{
        Check = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '$DbName' AND TABLE_NAME = 'model_call_log' AND COLUMN_NAME = 'combined_prompt_fingerprint'"
        Sql   = 'ALTER TABLE model_call_log ADD COLUMN combined_prompt_fingerprint VARCHAR(64) NULL'
    }
)

foreach ($item in $statements) {
    if ((Get-Count $item.Check) -eq 0) {
        Write-Host "Applying: $($item.Sql)"
        Invoke-MysqlStatement -Sql $item.Sql
    }
}

$finalStatus = & powershell -NoProfile -File $checkScript `
    -DbHost $DbHost -DbPort $DbPort -DbName $DbName -DbUser $DbUser -DbPassword $DbPassword 2>&1 |
    Select-Object -Last 1

Write-Host "Post-apply status: $finalStatus"

if ($finalStatus -in @('READY', 'PRESENT')) {
    Write-Host 'V33 schema objects are present. If flyway history is missing, run catch-up SQL separately; do not modify committed migration checksums.'
    exit 0
}

Write-Error "Apply completed but status is $finalStatus"
exit 1
