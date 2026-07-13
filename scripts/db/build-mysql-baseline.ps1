param(
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }),
    [int]$DbPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'codeforge_ai' }),
    [string]$DbUser = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'codeforge_ai_user' }),
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$OutputFile = $(Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path 'sql\mysql-baseline\B33__codeforge_mysql_schema.sql')
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'check-local-schema.ps1')

$CanonicalTables = @(
    'user', 'user_role', 'user_quota', 'quota_usage_log',
    'workspace', 'workspace_member',
    'ai_app', 'app_version', 'artifact_snapshot', 'generated_file', 'export_package',
    'app_publication', 'app_like', 'publication_view_dedupe',
    'generation_task', 'generation_task_event', 'generation_record', 'generation_message',
    'model_call_log', 'model_provider', 'model_provider_credential', 'ai_routing_config',
    'prompt_template', 'prompt_template_version',
    'deployment_job', 'deployment_log', 'audit_log', 'metric_daily_agg'
)

function Invoke-MysqlRawQuery {
    param(
        [string]$Sql,
        [switch]$Vertical
    )

    $mysqlExe = Get-MysqlExecutable
    $mysqlArgs = @(
        '-h', $DbHost,
        '-P', "$DbPort",
        '-u', $DbUser,
        '-D', $DbName,
        '-e', $Sql
    )
    if ($Vertical) {
        $mysqlArgs = @('-h', $DbHost, '-P', "$DbPort", '-u', $DbUser, '-D', $DbName, '-e', $Sql) + @()
    }
    $previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    try {
        $env:MYSQL_PWD = $DbPassword
        if ($Vertical) {
            $output = & $mysqlExe -h $DbHost -P $DbPort -u $DbUser -D $DbName -e $Sql 2>&1
        }
        else {
            $output = & $mysqlExe @mysqlArgs 2>&1
        }
        if ($LASTEXITCODE -ne 0) {
            throw 'MYSQL_QUERY_FAILED'
        }
        return @($output | ForEach-Object { [string]$_ })
    }
    finally {
        if ($null -eq $previousMysqlPwd) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        }
        else {
            $env:MYSQL_PWD = $previousMysqlPwd
        }
    }
}

function Get-ShowCreateTable {
    param([string]$TableName)

    $lines = Invoke-MysqlRawQuery -Sql "SHOW CREATE TABLE ``$TableName``"
    foreach ($line in $lines) {
        if ($line -match "^$TableName`t(.+)$") {
            return $matches[1]
        }
        if ($line -match "^CREATE TABLE") {
            return $line
        }
    }
    throw "SHOW_CREATE_TABLE_FAILED:$TableName"
}

function ConvertTo-BaselineDdl {
    param([string]$CreateSql)

    $ddl = $CreateSql -replace '\\n', "`n"
    $ddl = $ddl -replace ' AUTO_INCREMENT=\d+', ''
    $ddl = $ddl -replace ' DEFINER=`[^`]+`@`[^`]+`', ''
    $ddl = $ddl -replace 'CREATE TABLE `', 'CREATE TABLE IF NOT EXISTS `'
    return ($ddl.Trim() + ';')
}

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    throw 'DB_PASSWORD_REQUIRED'
}

$builder = New-Object System.Collections.Generic.List[string]
$builder.Add('-- CodeForge MySQL V33 baseline schema')
$builder.Add('-- Fresh environments apply this baseline migration instead of V1..V33 versioned migrations.')
$builder.Add('SET NAMES utf8mb4;')
$builder.Add('SET FOREIGN_KEY_CHECKS = 0;')

foreach ($table in $CanonicalTables) {
    $create = Get-ShowCreateTable -TableName $table
    $baselineDdl = ConvertTo-BaselineDdl -CreateSql $create

    if ($table -eq 'generated_file') {
        $baselineDdl = $baselineDdl -replace '`file_path` varchar\(765\)', '`file_path` varchar(1024)'
        $baselineDdl = $baselineDdl -replace 'KEY `idx_generated_file_version_path` \(`app_version_id`,`file_path`\)', 'KEY `idx_generated_file_version_path` (`app_version_id`,`file_path`(255))'
    }

    $builder.Add('')
    $builder.Add($baselineDdl)
}

$builder.Add('')
$builder.Add('SET FOREIGN_KEY_CHECKS = 1;')
$builder.Add('')
$builder.Add('-- Baseline seed allowlist: routing configuration required by V32 contract')
$builder.Add("INSERT INTO ai_routing_config (id, routing_mode, pinned_provider_code, updated_by, is_deleted)")
$builder.Add("SELECT 1, 'AUTO', NULL, NULL, 0")
$builder.Add('WHERE NOT EXISTS (SELECT 1 FROM ai_routing_config WHERE id = 1);')
$builder.Add('')
$builder.Add('-- Baseline seed allowlist: rule provider contract (credential_source=NONE, active)')
$builder.Add("INSERT INTO model_provider (provider_code, provider_name, base_url, auth_mode, api_protocol, api_key_env, default_model, priority, status, credential_source, created_by, updated_by, is_deleted)")
$builder.Add("SELECT 'rule', 'Rule Generator', NULL, 'NONE', 'RULE_BASED', NULL, 'rule-based', 999, 'ACTIVE', 'NONE', 0, 0, 0")
$builder.Add("WHERE NOT EXISTS (SELECT 1 FROM model_provider WHERE provider_code = 'rule' AND is_deleted = 0);")
$builder.Add('')
$builder.Add('-- Baseline seed allowlist: auto provider disabled placeholder required by V32 contract')
$builder.Add("INSERT INTO model_provider (provider_code, provider_name, base_url, auth_mode, api_protocol, api_key_env, default_model, priority, status, credential_source, created_by, updated_by, is_deleted)")
$builder.Add("SELECT 'auto', 'Auto Provider', NULL, 'NONE', 'RULE_BASED', NULL, 'auto', 1000, 'DISABLED', 'ENV', 0, 0, 1")
$builder.Add("WHERE NOT EXISTS (SELECT 1 FROM model_provider WHERE provider_code = 'auto');")

New-Item -ItemType Directory -Path (Split-Path $OutputFile -Parent) -Force | Out-Null
$builder | Set-Content -Path $OutputFile -Encoding utf8
Write-Output "BASELINE_WRITTEN:$OutputFile"
