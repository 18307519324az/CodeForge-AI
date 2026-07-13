param(
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }),
    [int]$DbPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'codeforge_ai' }),
    [string]$DbUser = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'codeforge_ai_user' }),
    [string]$DbPassword = $env:DB_PASSWORD
)

$ErrorActionPreference = 'Stop'

$script:RequiredColumns = @(
    @{ Table = 'generation_task'; Column = 'prompt_template_id' },
    @{ Table = 'generation_task'; Column = 'prompt_template_version_id' },
    @{ Table = 'model_call_log'; Column = 'system_prompt_sha256' },
    @{ Table = 'model_call_log'; Column = 'user_prompt_sha256' },
    @{ Table = 'model_call_log'; Column = 'combined_prompt_fingerprint' }
)

$script:RequiredIndexes = @(
    @{ Table = 'generation_task'; Index = 'idx_generation_task_prompt_template' },
    @{ Table = 'generation_task'; Index = 'idx_generation_task_prompt_template_version' }
)

function Get-MysqlExecutable {
    $mysqlCommand = Get-Command mysql -CommandType Application -ErrorAction SilentlyContinue
    if (-not $mysqlCommand) {
        throw 'MYSQL_CLIENT_NOT_FOUND'
    }

    $mysqlExe = $mysqlCommand.Source
    if ([string]::IsNullOrWhiteSpace($mysqlExe) -or -not (Test-Path -LiteralPath $mysqlExe -PathType Leaf)) {
        throw 'MYSQL_CLIENT_NOT_FOUND'
    }

    return $mysqlExe
}

function New-MysqlArgumentList {
    param(
        [string]$HostName,
        [int]$Port,
        [string]$UserName,
        [string]$DatabaseName,
        [string]$Sql
    )

    return @(
        '-h', $HostName,
        '-P', "$Port",
        '-u', $UserName,
        '-N', '-B',
        '-D', $DatabaseName,
        '-e', $Sql
    )
}

function Invoke-MysqlScalarQuery {
    param(
        [string]$Sql,
        [string]$HostName = $DbHost,
        [int]$Port = $DbPort,
        [string]$DatabaseName = $DbName,
        [string]$UserName = $DbUser,
        [string]$Password = $DbPassword,
        [string]$MysqlExecutable = $(Get-MysqlExecutable)
    )

    if ([string]::IsNullOrWhiteSpace($Password)) {
        throw 'DB_PASSWORD_REQUIRED'
    }

    $mysqlArgs = New-MysqlArgumentList -HostName $HostName -Port $Port -UserName $UserName -DatabaseName $DatabaseName -Sql $Sql
    $previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')

    try {
        $env:MYSQL_PWD = $Password
        $mysqlOutput = & $MysqlExecutable @mysqlArgs 2>&1
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
        $safeOutput = ($mysqlOutput | Out-String).Trim()
        if ($safeOutput -match '(?i)access denied') {
            throw 'MYSQL_ACCESS_DENIED'
        }
        if ($safeOutput -match '(?i)can''t connect|connection refused|unknown host') {
            throw 'MYSQL_CONNECTION_FAILED'
        }
        throw 'MYSQL_QUERY_FAILED'
    }

    $firstLine = ($mysqlOutput | Select-Object -First 1)
    if ($null -eq $firstLine) {
        return '0'
    }

    return [string]$firstLine
}

function Test-ColumnExists {
    param(
        [string]$Table,
        [string]$Column,
        [scriptblock]$QueryInvoker = ${function:Invoke-MysqlScalarQuery}
    )

    $sql = @"
SELECT COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = '$DbName' AND TABLE_NAME = '$Table' AND COLUMN_NAME = '$Column'
"@
    return [int](& $QueryInvoker -Sql $sql) -gt 0
}

function Test-IndexExists {
    param(
        [string]$Table,
        [string]$IndexName,
        [scriptblock]$QueryInvoker = ${function:Invoke-MysqlScalarQuery}
    )

    $sql = @"
SELECT COUNT(*) FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = '$DbName' AND TABLE_NAME = '$Table' AND INDEX_NAME = '$IndexName'
"@
    return [int](& $QueryInvoker -Sql $sql) -gt 0
}

function Test-BaselineFlywayHistoryPresent {
    param(
        [scriptblock]$QueryInvoker = ${function:Invoke-MysqlScalarQuery}
    )

    $baselineSql = @"
SELECT COUNT(*) FROM flyway_schema_history
WHERE version = '33'
  AND type IN ('BASELINE', 'SQL_BASELINE')
  AND script LIKE '%B33__codeforge_mysql_schema.sql%'
  AND success = 1
"@
  return [int](& $QueryInvoker -Sql $baselineSql) -gt 0
}

function Test-LegacyVersionedFlywayHistoryPresent {
    param(
        [scriptblock]$QueryInvoker = ${function:Invoke-MysqlScalarQuery}
    )

    $versionSql = @"
SELECT COUNT(*) FROM flyway_schema_history
WHERE version = '33' OR script LIKE '%V33__generation_task_prompt_template_binding%'
"@
    return [int](& $QueryInvoker -Sql $versionSql) -gt 0
}

function Test-FlywayHistoryPresent {
    param(
        [scriptblock]$QueryInvoker = ${function:Invoke-MysqlScalarQuery}
    )

    $historyTableSql = @"
SELECT COUNT(*) FROM information_schema.TABLES
WHERE TABLE_SCHEMA = '$DbName' AND TABLE_NAME = 'flyway_schema_history'
"@
    if ([int](& $QueryInvoker -Sql $historyTableSql) -eq 0) {
        return $false
    }

    return (Test-BaselineFlywayHistoryPresent -QueryInvoker $QueryInvoker) `
        -or (Test-LegacyVersionedFlywayHistoryPresent -QueryInvoker $QueryInvoker)
}

function Get-LocalSchemaGateStatus {
    param(
        [scriptblock]$QueryInvoker = ${function:Invoke-MysqlScalarQuery}
    )

    $missing = @()
    foreach ($item in $script:RequiredColumns) {
        if (-not (Test-ColumnExists -Table $item.Table -Column $item.Column -QueryInvoker $QueryInvoker)) {
            $missing += "$($item.Table).$($item.Column)"
        }
    }
    foreach ($item in $script:RequiredIndexes) {
        if (-not (Test-IndexExists -Table $item.Table -IndexName $item.Index -QueryInvoker $QueryInvoker)) {
            $missing += "$($item.Table).$($item.Index)"
        }
    }

    $historyPresent = Test-FlywayHistoryPresent -QueryInvoker $QueryInvoker
    $schemaPresent = $missing.Count -eq 0

    if ($schemaPresent -and $historyPresent) {
        return 'READY'
    }
    if (-not $schemaPresent -and -not $historyPresent) {
        return 'MISSING'
    }
    if ($schemaPresent -and -not $historyPresent) {
        return 'HISTORY_MISMATCH'
    }
    if (-not $schemaPresent -and $historyPresent) {
        return 'HISTORY_MISMATCH'
    }

    return 'PRESENT'
}

function Get-SchemaGateExitCode {
    param([string]$Status)

    switch ($Status) {
        'READY' { return 0 }
        'MISSING' { return 1 }
        'HISTORY_MISMATCH' { return 2 }
        'PRESENT' { return 3 }
        default { return 4 }
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    try {
        $status = Get-LocalSchemaGateStatus
        Write-Output $status
        exit (Get-SchemaGateExitCode -Status $status)
    }
    catch {
        Write-Output 'ERROR'
        exit 4
    }
}
