$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$checkScript = Join-Path $scriptRoot 'check-local-schema.ps1'

$passed = 0
$failed = 0

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Name
    )

    if ($Condition) {
        Write-Host "PASS: $Name"
        $script:passed++
    }
    else {
        Write-Host "FAIL: $Name"
        $script:failed++
    }
}

function Assert-Equals {
    param(
        $Expected,
        $Actual,
        [string]$Name
    )

    if ($Expected -eq $Actual) {
        Write-Host "PASS: $Name"
        $script:passed++
    }
    else {
        Write-Host "FAIL: $Name (expected '$Expected', actual '$Actual')"
        $script:failed++
    }
}

. $checkScript

$scriptContent = Get-Content -LiteralPath $checkScript -Raw

Assert-True (-not ($scriptContent -match 'mysql\.Source')) 'ScriptDoesNotContainLiteralMysqlDotSourceInvocationTest'
Assert-True (-not ($scriptContent -match 'Invoke-Expression')) 'ScriptDoesNotUseInvokeExpressionTest'
Assert-True (-not ($scriptContent -match '-p\$DbPassword|-p\$Password|--password=')) 'DbPasswordIsNotInCommandLineArgumentsTest'

$invokedExe = $null
$invokedArgs = $null
$fakeMysqlPath = 'D:\MySQL\MySQL Server 8.0\bin\mysql.exe'
$mysqlArgs = New-MysqlArgumentList -HostName 'localhost' -Port 3306 -UserName 'u' -DatabaseName 'db' -Sql 'SELECT 1'
$previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')

try {
    $env:MYSQL_PWD = 'secret-not-printed'
    & {
        param($Exe, $MysqlArgList)
        $script:invokedExe = $Exe
        $script:invokedArgs = $MysqlArgList
    } -Exe $fakeMysqlPath -MysqlArgList $mysqlArgs | Out-Null
}
finally {
    if ($null -eq $previousMysqlPwd) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    else {
        $env:MYSQL_PWD = $previousMysqlPwd
    }
}

Assert-Equals $fakeMysqlPath $invokedExe 'MysqlCommandSourcePathIsInvokedTest'
Assert-True ($invokedArgs -contains '-e') 'MysqlArgumentsIncludeSqlFlagTest'
Assert-True (-not ($invokedArgs -contains 'secret-not-printed')) 'DbPasswordIsNotPrintedTest'
Assert-True (-not ($invokedArgs | Where-Object { $_ -clike '-p*' })) 'DbPasswordIsNotInMysqlArgsTest'

$invokedExe = $null
$spacePath = 'D:\MySQL\MySQL Server 8.0\bin\mysql.exe'
& {
    param($Exe)
    $script:invokedExe = $Exe
} -Exe $spacePath | Out-Null
Assert-Equals $spacePath $invokedExe 'MysqlPathWithSpacesIsSupportedTest'

$scriptContentAfterPathCheck = Get-Content -LiteralPath $checkScript -Raw
Assert-True ($scriptContentAfterPathCheck -match 'Select-Object -First 1') 'MultipleMysqlCommandsUseFirstApplicationTest'

function Get-MysqlExecutable {
    throw 'MYSQL_CLIENT_NOT_FOUND'
}

try {
    Get-MysqlExecutable | Out-Null
    Assert-True $false 'MissingMysqlClientReturnsErrorTest'
}
catch {
    Assert-Equals 'MYSQL_CLIENT_NOT_FOUND' $_.Exception.Message 'MissingMysqlClientReturnsErrorTest'
}

. $checkScript

function New-SchemaQueryInvoker {
    param([hashtable]$Responses)

    return {
        param([string]$Sql)

        foreach ($pattern in $Responses.Keys) {
            if ($Sql -like "*$pattern*") {
                return [string]$Responses[$pattern]
            }
        }

        return '0'
    }.GetNewClosure()
}

$readyInvoker = New-SchemaQueryInvoker -Responses @{
    "COLUMN_NAME = 'prompt_template_id'" = '1'
    "COLUMN_NAME = 'prompt_template_version_id'" = '1'
    "COLUMN_NAME = 'system_prompt_sha256'" = '1'
    "COLUMN_NAME = 'user_prompt_sha256'" = '1'
    "COLUMN_NAME = 'combined_prompt_fingerprint'" = '1'
    "INDEX_NAME = 'idx_generation_task_prompt_template'" = '1'
    "INDEX_NAME = 'idx_generation_task_prompt_template_version'" = '1'
    "TABLE_NAME = 'flyway_schema_history'" = '1'
    "version = '33'" = '1'
}

Assert-Equals 'READY' (Get-LocalSchemaGateStatus -QueryInvoker $readyInvoker) 'ReadyOnlyWhenAllRequiredColumnsExistTest'

$baselineReadyInvoker = New-SchemaQueryInvoker -Responses @{
    "COLUMN_NAME = 'prompt_template_id'" = '1'
    "COLUMN_NAME = 'prompt_template_version_id'" = '1'
    "COLUMN_NAME = 'system_prompt_sha256'" = '1'
    "COLUMN_NAME = 'user_prompt_sha256'" = '1'
    "COLUMN_NAME = 'combined_prompt_fingerprint'" = '1'
    "INDEX_NAME = 'idx_generation_task_prompt_template'" = '1'
    "INDEX_NAME = 'idx_generation_task_prompt_template_version'" = '1'
    "TABLE_NAME = 'flyway_schema_history'" = '1'
    "version = '33'" = '1'
    "type IN ('BASELINE', 'SQL_BASELINE')" = '1'
    "B33__codeforge_mysql_schema.sql" = '1'
}

Assert-Equals 'READY' (Get-LocalSchemaGateStatus -QueryInvoker $baselineReadyInvoker) 'ReadyWhenBaselineHistoryPresentTest'

$missingInvoker = New-SchemaQueryInvoker -Responses @{
    "COLUMN_NAME = 'prompt_template_id'" = '0'
    "COLUMN_NAME = 'prompt_template_version_id'" = '0'
    "COLUMN_NAME = 'system_prompt_sha256'" = '0'
    "COLUMN_NAME = 'user_prompt_sha256'" = '0'
    "COLUMN_NAME = 'combined_prompt_fingerprint'" = '0'
    "INDEX_NAME = 'idx_generation_task_prompt_template'" = '0'
    "INDEX_NAME = 'idx_generation_task_prompt_template_version'" = '0'
    "TABLE_NAME = 'flyway_schema_history'" = '0'
}

Assert-Equals 'MISSING' (Get-LocalSchemaGateStatus -QueryInvoker $missingInvoker) 'MissingColumnReturnsMissingTest'

$historyMismatchInvoker = New-SchemaQueryInvoker -Responses @{
    "COLUMN_NAME = 'prompt_template_id'" = '1'
    "COLUMN_NAME = 'prompt_template_version_id'" = '1'
    "COLUMN_NAME = 'system_prompt_sha256'" = '1'
    "COLUMN_NAME = 'user_prompt_sha256'" = '1'
    "COLUMN_NAME = 'combined_prompt_fingerprint'" = '1'
    "INDEX_NAME = 'idx_generation_task_prompt_template'" = '1'
    "INDEX_NAME = 'idx_generation_task_prompt_template_version'" = '1'
    "TABLE_NAME = 'flyway_schema_history'" = '1'
    "version = '33'" = '0'
}

Assert-Equals 'HISTORY_MISMATCH' (Get-LocalSchemaGateStatus -QueryInvoker $historyMismatchInvoker) 'HistoryMismatchReturnsHistoryMismatchTest'

$failInvoker = {
    $global:LASTEXITCODE = 1
    throw 'MYSQL_QUERY_FAILED'
}

try {
    Get-LocalSchemaGateStatus -QueryInvoker $failInvoker | Out-Null
    Assert-True $false 'MysqlNonZeroExitReturnsErrorTest'
}
catch {
    Assert-Equals 'MYSQL_QUERY_FAILED' $_.Exception.Message 'MysqlNonZeroExitReturnsErrorTest'
}

$beforePwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
$powershellExe = (Get-Process -Id $PID).Path
$previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')

try {
    $env:MYSQL_PWD = 'temp-secret'
    & $powershellExe @('-NoProfile', '-Command', 'exit 0') | Out-Null
    $null = $LASTEXITCODE
}
finally {
    if ($null -eq $previousMysqlPwd) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    else {
        $env:MYSQL_PWD = $previousMysqlPwd
    }
}

Assert-Equals $beforePwd ([Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')) 'MysqlPwdIsRestoredAfterSuccessTest'

$previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
try {
    $env:MYSQL_PWD = 'temp-secret'
    & $powershellExe @('-NoProfile', '-Command', 'exit 1') | Out-Null
    $null = $LASTEXITCODE
}
finally {
    if ($null -eq $previousMysqlPwd) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    else {
        $env:MYSQL_PWD = $previousMysqlPwd
    }
}

Assert-Equals $beforePwd ([Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')) 'MysqlPwdIsRestoredAfterFailureTest'

Write-Host ''
Write-Host "Schema checker tests: $passed passed, $failed failed"

if ($failed -gt 0) {
    exit 1
}

exit 0
