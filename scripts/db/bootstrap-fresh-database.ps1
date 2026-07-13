param(
    [string]$EnvFile = '.env.local',
    [switch]$ConfirmCreate,
    [switch]$CreateDatabase,
    [switch]$ValidateOnly,
    [string]$DbHost,
    [int]$DbPort,
    [string]$DbName,
    [string]$DbUsername,
    [string]$DbPassword
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFileModule = Join-Path $projectRoot 'scripts\lib\EnvFile.ps1'
$checkScript = Join-Path $projectRoot 'scripts\db\check-local-schema.ps1'
. $envFileModule
. $checkScript

function Resolve-DbSetting {
    param(
        [string]$ExplicitValue,
        [string]$EnvironmentName,
        [string]$DefaultValue = $null
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitValue)) {
        return $ExplicitValue
    }
    $envValue = [Environment]::GetEnvironmentVariable($EnvironmentName, 'Process')
    if (-not [string]::IsNullOrWhiteSpace($envValue)) {
        return $envValue
    }
    return $DefaultValue
}

function Test-LocalBootstrapHost {
    param([string]$HostName)

    $allowed = @('localhost', '127.0.0.1', '::1', 'host.docker.internal')
    if ($allowed -notcontains $HostName) {
        throw 'NON_LOCAL_HOST'
    }
}

function Test-BootstrapDatabaseName {
    param([string]$DatabaseName)

    if ([string]::IsNullOrWhiteSpace($DatabaseName)) {
        throw 'DB_NAME_REQUIRED'
    }
    if ($DatabaseName -match '(?i)prod|production|staging|stage|online|live') {
        throw 'PRODUCTION_LIKE_DATABASE_NAME'
    }
}

function Invoke-BootstrapMysqlScalar {
    param(
        [string]$Sql,
        [string]$DatabaseName = ''
    )

    $mysqlExe = Get-MysqlExecutable
    $args = @('-h', $script:BootstrapDbHost, '-P', "$script:BootstrapDbPort", '-u', $script:BootstrapDbUsername, '-N', '-B')
    if (-not [string]::IsNullOrWhiteSpace($DatabaseName)) {
        $args += @('-D', $DatabaseName)
    }
    $args += @('-e', $Sql)

    $previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    try {
        $env:MYSQL_PWD = $script:BootstrapDbPassword
        $output = & $mysqlExe @args 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        if ($null -eq $previousMysqlPwd) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        }
        else {
            $env:MYSQL_PWD = $previousMysqlPwd
        }
    }

    if ($exitCode -ne 0) {
        $safeOutput = ($output | Out-String).Trim()
        if ($safeOutput -match '(?i)access denied') {
            throw 'MYSQL_ACCESS_DENIED'
        }
        if ($safeOutput -match '(?i)can''t connect|connection refused|unknown host') {
            throw 'MYSQL_CONNECTION_FAILED'
        }
        throw 'MYSQL_QUERY_FAILED'
    }

    $firstLine = $output | Select-Object -First 1
    if ($null -eq $firstLine) {
        return '0'
    }
    return [string]$firstLine
}

function Test-DatabaseExists {
    $escaped = $script:BootstrapDbName.Replace("'", "''")
    $sql = "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$escaped'"
    return [int](Invoke-BootstrapMysqlScalar -Sql $sql) -gt 0
}

function Test-DatabaseExistsAsAdmin {
    $escaped = $script:BootstrapDbName.Replace("'", "''")
    $sql = "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$escaped'"
    return [int](Invoke-BootstrapMysqlScalarAsAdmin -Sql $sql) -gt 0
}

function Get-UserTableCount {
    $escaped = $script:BootstrapDbName.Replace("'", "''")
    $sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$escaped' AND TABLE_TYPE = 'BASE TABLE'"
    return [int](Invoke-BootstrapMysqlScalar -Sql $sql)
}

function Get-BootstrapSchemaStatus {
    $queryInvoker = {
        param([string]$Sql)
        Invoke-MysqlScalarQuery -Sql $Sql `
            -HostName $script:BootstrapDbHost `
            -Port $script:BootstrapDbPort `
            -DatabaseName $script:BootstrapDbName `
            -UserName $script:BootstrapDbUsername `
            -Password $script:BootstrapDbPassword
    }

    return Get-LocalSchemaGateStatus -QueryInvoker $queryInvoker
}

function Invoke-FlywayGoal {
    param([string]$Goal)

    $runningOnWindows = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Windows)
    $mvn = Join-Path $projectRoot $(if ($runningOnWindows) { 'mvnw.cmd' } else { 'mvnw' })
    if (-not (Test-Path -LiteralPath $mvn -PathType Leaf)) {
        $mvn = Join-Path $projectRoot 'mvnw.cmd'
    }
    if (-not (Test-Path -LiteralPath $mvn -PathType Leaf)) {
        $mvn = Join-Path $projectRoot 'mvnw'
    }
    if (-not (Test-Path -LiteralPath $mvn -PathType Leaf)) {
        throw 'MAVEN_WRAPPER_NOT_FOUND'
    }

    $previous = @{
        FLYWAY_URL = [Environment]::GetEnvironmentVariable('FLYWAY_URL', 'Process')
        FLYWAY_USER = [Environment]::GetEnvironmentVariable('FLYWAY_USER', 'Process')
        FLYWAY_PASSWORD = [Environment]::GetEnvironmentVariable('FLYWAY_PASSWORD', 'Process')
        FLYWAY_LOCATIONS = [Environment]::GetEnvironmentVariable('FLYWAY_LOCATIONS', 'Process')
        FLYWAY_BASELINE_ON_MIGRATE = [Environment]::GetEnvironmentVariable('FLYWAY_BASELINE_ON_MIGRATE', 'Process')
    }

    try {
        $env:FLYWAY_URL = "jdbc:mysql://$script:BootstrapDbHost`:$script:BootstrapDbPort/$script:BootstrapDbName`?useUnicode=true&characterEncoding=utf8&serverTimezone=$script:BootstrapDbTimezone&allowPublicKeyRetrieval=true&useSSL=false"
        $env:FLYWAY_USER = $script:BootstrapDbUsername
        $env:FLYWAY_PASSWORD = $script:BootstrapDbPassword
        $env:FLYWAY_LOCATIONS = 'filesystem:sql/migrations,filesystem:sql/mysql-local,filesystem:sql/mysql-baseline'
        $env:FLYWAY_BASELINE_ON_MIGRATE = 'true'

        Push-Location $projectRoot
        try {
            & $mvn '-q' "org.flywaydb:flyway-maven-plugin:11.7.2:$Goal" | Out-Host
            if ($LASTEXITCODE -ne 0) {
                throw "FLYWAY_$($Goal.ToUpperInvariant())_FAILED"
            }
        }
        finally {
            Pop-Location
        }
    }
    finally {
        foreach ($name in $previous.Keys) {
            if ($null -eq $previous[$name]) {
                [Environment]::SetEnvironmentVariable($name, $null, 'Process')
            }
            else {
                [Environment]::SetEnvironmentVariable($name, [string]$previous[$name], 'Process')
            }
        }
    }
}

function Invoke-BootstrapMysqlScalarAsAdmin {
    param([string]$Sql)

    $mysqlExe = Get-MysqlExecutable
    $args = @('-h', $script:BootstrapDbHost, '-P', "$script:BootstrapDbPort", '-u', $script:BootstrapDbAdminUsername, '-N', '-B')
    $previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    try {
        $env:MYSQL_PWD = $script:BootstrapDbAdminPassword
        $output = $Sql | & $mysqlExe @args 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        if ($null -eq $previousMysqlPwd) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        }
        else {
            $env:MYSQL_PWD = $previousMysqlPwd
        }
    }
    if ($exitCode -ne 0) {
        $safeOutput = ($output | Out-String).Trim()
        if ($safeOutput -match '(?i)access denied') {
            throw 'MYSQL_ADMIN_ACCESS_DENIED'
        }
        throw 'MYSQL_ADMIN_QUERY_FAILED'
    }
    $firstLine = $output | Select-Object -First 1
    if ($null -eq $firstLine) {
        return '0'
    }
    return [string]$firstLine
}

function Grant-BootstrapDatabaseAccessWithAdmin {
    $escapedDatabase = $script:BootstrapDbName.Replace('`', '``')
    $escapedUser = $script:BootstrapDbUsername.Replace("'", "''")
    $escapedPassword = $script:BootstrapDbPassword.Replace("'", "''")
    Invoke-BootstrapMysqlScalarAsAdmin -Sql "CREATE USER IF NOT EXISTS '$escapedUser'@'%' IDENTIFIED BY '$escapedPassword'" | Out-Null
    Invoke-BootstrapMysqlScalarAsAdmin -Sql "GRANT ALL PRIVILEGES ON ``$escapedDatabase``.* TO '$escapedUser'@'%'" | Out-Null
}

function New-BootstrapDatabaseWithAdmin {
    $escapedDatabase = $script:BootstrapDbName.Replace('`', '``')
    Invoke-BootstrapMysqlScalarAsAdmin -Sql "CREATE DATABASE IF NOT EXISTS ``$escapedDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" | Out-Null
    Grant-BootstrapDatabaseAccessWithAdmin
}

$envSnapshot = $null
try {
    $envFilePath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $projectRoot $EnvFile }
    $envSnapshot = Import-CodeForgeEnvFile -Path $envFilePath

    $script:BootstrapDbHost = Resolve-DbSetting -ExplicitValue $DbHost -EnvironmentName 'DB_HOST' -DefaultValue 'localhost'
    $script:BootstrapDbPort = [int](Resolve-DbSetting -ExplicitValue $(if ($PSBoundParameters.ContainsKey('DbPort')) { "$DbPort" } else { $null }) -EnvironmentName 'DB_PORT' -DefaultValue '3306')
    $script:BootstrapDbName = Resolve-DbSetting -ExplicitValue $DbName -EnvironmentName 'DB_NAME'
    $script:BootstrapDbUsername = Resolve-DbSetting -ExplicitValue $DbUsername -EnvironmentName 'DB_USERNAME'
    $script:BootstrapDbPassword = Resolve-DbSetting -ExplicitValue $DbPassword -EnvironmentName 'DB_PASSWORD'
    $script:BootstrapDbTimezone = Resolve-DbSetting -EnvironmentName 'DB_TIMEZONE' -DefaultValue 'UTC'
    $script:BootstrapDbAdminUsername = Resolve-DbSetting -EnvironmentName 'DB_ADMIN_USERNAME'
    $script:BootstrapDbAdminPassword = Resolve-DbSetting -EnvironmentName 'DB_ADMIN_PASSWORD'

    Test-LocalBootstrapHost -HostName $script:BootstrapDbHost
    Test-BootstrapDatabaseName -DatabaseName $script:BootstrapDbName
    if ([string]::IsNullOrWhiteSpace($script:BootstrapDbUsername)) {
        throw 'DB_USERNAME_REQUIRED'
    }
    if ([string]::IsNullOrWhiteSpace($script:BootstrapDbPassword)) {
        throw 'DB_PASSWORD_REQUIRED'
    }

    if ($CreateDatabase) {
        if ([string]::IsNullOrWhiteSpace($script:BootstrapDbAdminUsername)) {
            throw 'DB_ADMIN_USERNAME_REQUIRED'
        }
        if ([string]::IsNullOrWhiteSpace($script:BootstrapDbAdminPassword)) {
            throw 'DB_ADMIN_PASSWORD_REQUIRED'
        }
        $adminDatabaseExists = Test-DatabaseExistsAsAdmin
        if (-not $ValidateOnly) {
            if (-not $adminDatabaseExists) {
                New-BootstrapDatabaseWithAdmin
            }
            else {
                Grant-BootstrapDatabaseAccessWithAdmin
            }
        }
    }

    $databaseExists = Test-DatabaseExists
    if (-not $databaseExists) {
        throw 'TARGET_DATABASE_DOES_NOT_EXIST'
    }

    if (Test-DatabaseExists) {
        $tableCount = Get-UserTableCount
        if ($tableCount -gt 0) {
            $status = Get-BootstrapSchemaStatus
            if ($status -eq 'READY') {
                Write-Host 'ALREADY_READY'
                Write-Host 'SCHEMA_STATUS=READY'
                exit 0
            }
            if ($status -eq 'HISTORY_MISMATCH') {
                throw 'HISTORY_MISMATCH'
            }
            throw 'NON_EMPTY_UNMANAGED_DATABASE'
        }
    }

    if ($ValidateOnly) {
        Write-Host 'VALIDATE_ONLY_EMPTY_DATABASE'
        exit 0
    }
    if (-not $ConfirmCreate) {
        throw 'CONFIRM_CREATE_REQUIRED'
    }

    Invoke-FlywayGoal -Goal 'info'
    Invoke-FlywayGoal -Goal 'migrate'
    Invoke-FlywayGoal -Goal 'validate'
    Write-Host 'FLYWAY_VALIDATE_PASS'

    $baselineCount = Invoke-BootstrapMysqlScalar -DatabaseName $script:BootstrapDbName -Sql @"
SELECT COUNT(*) FROM flyway_schema_history
WHERE version = '33'
  AND type = 'SQL_BASELINE'
  AND script = 'B33__codeforge_mysql_schema.sql'
  AND success = 1
"@
    if ([int]$baselineCount -le 0) {
        throw 'B33_BASELINE_NOT_APPLIED'
    }
    Write-Host 'B33_BASELINE_APPLIED'

    $v1Count = Invoke-BootstrapMysqlScalar -DatabaseName $script:BootstrapDbName -Sql "SELECT COUNT(*) FROM flyway_schema_history WHERE script LIKE 'V1__%' AND success = 1"
    if ([int]$v1Count -ne 0) {
        throw 'V1_EXECUTED_IN_FRESH_DATABASE'
    }
    Write-Host 'V1_EXECUTED=false'

    $finalStatus = Get-BootstrapSchemaStatus
    Write-Host "SCHEMA_STATUS=$finalStatus"
    if ($finalStatus -ne 'READY') {
        throw "SCHEMA_STATUS_$finalStatus"
    }
}
finally {
    if ($null -ne $envSnapshot) {
        Restore-CodeForgeEnvironment -Snapshot $envSnapshot
    }
}
