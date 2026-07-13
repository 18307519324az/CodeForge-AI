param(
    [switch]$Apply,
    [string]$ConfirmationToken,
    [switch]$InitializeProbe,
    [string]$ProbeDatabaseName,
    [string]$ExpectedGitHead,
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }),
    [int]$DbPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'codeforge_ai' }),
    [string]$DbUser = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'codeforge_ai_user' }),
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$SpringProfile = $(if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'local' })
)

$ErrorActionPreference = 'Stop'

$script:CatchUpVersions = @('28', '28.1', '29', '30', '30.1', '32', '33')
$script:ForbiddenDbTokens = @('prod', 'production', 'staging', 'stage', 'online')
$script:AllowedHosts = @('localhost', '127.0.0.1', '::1')
$script:ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:ProbeArtifactDir = Join-Path $script:ProjectRoot '.local-data\flyway-probe'
$script:AuthoritativeHistoryFile = Join-Path $script:ProbeArtifactDir 'authoritative-history-v28-v33.tsv'
$script:ProbeSchemaDir = Join-Path $script:ProbeArtifactDir 'schema-snapshots'
$script:MigrationHashFile = Join-Path $script:ProbeArtifactDir 'migration-file-hashes.json'

. (Join-Path $PSScriptRoot 'check-local-schema.ps1')

function Write-CatchUpStatus {
    param([string]$Message)
    Write-Output $Message
}

function Test-EnvPresent {
    param([string]$Name)
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        return 'ABSENT'
    }
    return 'PRESENT'
}

function Test-LocalDatabaseSafety {
    if ($SpringProfile -ne 'local') {
        throw 'NON_LOCAL_PROFILE'
    }

    $normalizedHost = $DbHost.Trim().ToLowerInvariant()
    if ($script:AllowedHosts -notcontains $normalizedHost) {
        throw 'NON_LOCAL_HOST'
    }

    $normalizedDb = $DbName.Trim().ToLowerInvariant()
    foreach ($token in $script:ForbiddenDbTokens) {
        if ($normalizedDb -like "*$token*") {
            throw 'FORBIDDEN_DATABASE_NAME'
        }
    }

    if ([string]::IsNullOrWhiteSpace($DbPassword)) {
        throw 'DB_PASSWORD_REQUIRED'
    }
}

function Get-CurrentGitHead {
    Push-Location $script:ProjectRoot
    try {
        return (git rev-parse HEAD).Trim()
    }
    finally {
        Pop-Location
    }
}

function Test-ExpectedGitHead {
    if ([string]::IsNullOrWhiteSpace($ExpectedGitHead)) {
        $script:ExpectedGitHead = Get-CurrentGitHead
    }

    $head = Get-CurrentGitHead
    if ($head -ne $ExpectedGitHead) {
        throw "UNEXPECTED_GIT_HEAD:$head"
    }

    Push-Location $script:ProjectRoot
    try {
        git diff --quiet
        if ($LASTEXITCODE -ne 0) {
            throw 'GIT_TRACKED_DIFF_PRESENT'
        }
    }
    finally {
        Pop-Location
    }
}

function Get-MigrationFilesForCatchUp {
    $files = @(
        'sql\migrations\V28__app_publication.sql',
        'sql\mysql-local\V28_1__mysql_model_call_log_prompt_trace.sql',
        'sql\mysql-local\V29__repair_prompt_template_version_utf8.sql',
        'sql\migrations\V30__publication_view_dedupe.sql',
        'sql\mysql-local\V30_1__repair_prompt_template_metadata_utf8.sql',
        'sql\mysql-local\V32__provider_configuration_center_mysql.sql',
        'sql\mysql-local\V33__generation_task_prompt_template_binding_mysql.sql'
    )

    return $files | ForEach-Object {
        $fullPath = Join-Path $script:ProjectRoot $_
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            throw "MIGRATION_FILE_MISSING:$_"
        }
        [pscustomobject]@{
            RelativePath = $_
            FullPath     = $fullPath
            Hash         = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
}

function Save-MigrationHashSnapshot {
    $items = Get-MigrationFilesForCatchUp
    $payload = @{
        capturedAt = (Get-Date).ToString('o')
        gitHead    = Get-CurrentGitHead
        files      = @($items | ForEach-Object {
                @{
                    relativePath = $_.RelativePath
                    sha256       = $_.Hash
                }
            })
    }
    New-Item -ItemType Directory -Path $script:ProbeArtifactDir -Force | Out-Null
    $payload | ConvertTo-Json -Depth 6 | Set-Content -Path $script:MigrationHashFile -Encoding utf8
    return $items
}

function Test-MigrationHashesUnchanged {
    if (-not (Test-Path -LiteralPath $script:MigrationHashFile -PathType Leaf)) {
        throw 'MIGRATION_HASH_SNAPSHOT_MISSING'
    }

    $saved = Get-Content -LiteralPath $script:MigrationHashFile -Raw | ConvertFrom-Json
    if ($saved.gitHead -ne (Get-CurrentGitHead)) {
        throw 'MIGRATION_HASH_SNAPSHOT_HEAD_MISMATCH'
    }

    $current = Get-MigrationFilesForCatchUp
    foreach ($item in $current) {
        $savedItem = $saved.files | Where-Object { $_.relativePath -eq $item.RelativePath } | Select-Object -First 1
        if (-not $savedItem -or $savedItem.sha256 -ne $item.Hash) {
            throw "MIGRATION_HASH_CHANGED:$($item.RelativePath)"
        }
    }
}

function Invoke-MysqlTabularQuery {
    param(
        [string]$Sql,
        [string]$DatabaseName = $DbName,
        [string]$UserName = $DbUser,
        [string]$Password = $DbPassword,
        [string]$HostName = $DbHost,
        [int]$Port = $DbPort
    )

    $mysqlExe = Get-MysqlExecutable
    $mysqlArgs = New-MysqlArgumentList -HostName $HostName -Port $Port -UserName $UserName -DatabaseName $DatabaseName -Sql $Sql
    $previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')

    try {
        $env:MYSQL_PWD = $Password
        $output = & $mysqlExe @mysqlArgs 2>&1
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

    return @($output | ForEach-Object { [string]$_ })
}

function Get-FlywayHistoryRows {
    param([string]$DatabaseName = $DbName)

    $lines = Invoke-MysqlTabularQuery -DatabaseName $DatabaseName -Sql @'
SELECT installed_rank, IFNULL(version, ''), description, type, script, IFNULL(checksum, ''), installed_by, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank
'@

    return $lines | ForEach-Object {
        $parts = $_ -split "`t", 9
        [pscustomobject]@{
            installed_rank = [int]$parts[0]
            version        = $parts[1]
            description    = $parts[2]
            type           = $parts[3]
            script         = $parts[4]
            checksum       = if ([string]::IsNullOrWhiteSpace($parts[5])) { $null } else { [int]$parts[5] }
            installed_by   = $parts[6]
            execution_time = [int]$parts[7]
            success        = [int]$parts[8]
        }
    }
}

function Get-HighestSuccessfulHistoryVersion {
    param([object[]]$Rows)

    $numeric = $Rows | Where-Object { $_.success -eq 1 -and $_.version -match '^\d+$' } | ForEach-Object { [int]$_.version }
    if (-not $numeric) {
        return 0
    }
    return ($numeric | Measure-Object -Maximum).Maximum
}

function Get-PlannedCatchUpVersions {
    if (Test-Path -LiteralPath $script:AuthoritativeHistoryFile -PathType Leaf) {
        $lines = Get-Content -LiteralPath $script:AuthoritativeHistoryFile -Encoding utf8
        return ($lines | ForEach-Object {
                ($_ -split "`t", 2)[1]
            } | Where-Object { $_ -in $script:CatchUpVersions })
    }
    return $script:CatchUpVersions
}

function Test-MissingCatchUpVersions {
    param([object[]]$Rows)

    $targetVersions = Get-PlannedCatchUpVersions
    $existing = @{}
    foreach ($row in $Rows) {
        if ($row.success -eq 1 -and $row.version -in $targetVersions) {
            $existing[$row.version] = $true
        }
    }

    $missing = @()
    foreach ($version in $targetVersions) {
        if (-not $existing.ContainsKey($version)) {
            $missing += $version
        }
    }
    return $missing
}

function Test-NoPartialCatchUpHistory {
    param([object[]]$Rows)

    $targetVersions = Get-PlannedCatchUpVersions
    $partial = $Rows | Where-Object {
        $_.version -in $targetVersions -and ($_.success -ne 1)
    }
    if ($partial) {
        throw 'PARTIAL_CATCHUP_HISTORY_PRESENT'
    }

    $present = $Rows | Where-Object { $_.version -in $targetVersions -and $_.success -eq 1 }
    if ($present -and ($present.Count -ne 0) -and ($present.Count -ne $targetVersions.Count)) {
        throw 'PARTIAL_CATCHUP_HISTORY_PRESENT'
    }
}

function Get-AuthoritativeCatchUpRows {
    if (-not (Test-Path -LiteralPath $script:AuthoritativeHistoryFile -PathType Leaf)) {
        throw 'AUTHORITATIVE_HISTORY_MISSING'
    }

    $lines = Get-Content -LiteralPath $script:AuthoritativeHistoryFile -Encoding utf8
    return $lines | ForEach-Object {
        $parts = $_ -split "`t", 9
        [pscustomobject]@{
            installed_rank = [int]$parts[0]
            version        = $parts[1]
            description    = $parts[2]
            type           = $parts[3]
            script         = $parts[4]
            checksum       = [int]$parts[5]
            installed_by   = $parts[6]
            execution_time = [int]$parts[7]
            success        = [int]$parts[8]
        }
    } | Where-Object { $_.version -in $script:CatchUpVersions }
}

function Export-InformationSchemaSnapshot {
    param(
        [string]$DatabaseName,
        [string]$OutputFile,
        [string]$UserName = $DbUser,
        [string]$Password = $DbPassword,
        [string]$HostName = $DbHost,
        [int]$Port = $DbPort
    )

    $sql = @"
SELECT CONCAT(TABLE_NAME,'|',COLUMN_NAME,'|',COLUMN_TYPE,'|',IS_NULLABLE,'|',IFNULL(COLUMN_DEFAULT,''),'|',IFNULL(EXTRA,''))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = '$DatabaseName'
  AND (
    TABLE_NAME IN ('app_publication','publication_view_dedupe','model_provider_credential','ai_routing_config')
    OR (TABLE_NAME = 'generation_task' AND COLUMN_NAME IN ('prompt_template_id','prompt_template_version_id'))
    OR (TABLE_NAME = 'model_call_log' AND COLUMN_NAME IN ('prompt_template_version_id','prompt_template_code','prompt_template_version_no','system_prompt_sha256','user_prompt_sha256','combined_prompt_fingerprint'))
    OR (TABLE_NAME = 'model_provider' AND COLUMN_NAME = 'credential_source')
  )
ORDER BY TABLE_NAME, ORDINAL_POSITION;
"@
    $lines = Invoke-MysqlTabularQuery -DatabaseName $DatabaseName -Sql $sql -UserName $UserName -Password $Password -HostName $HostName -Port $Port
    New-Item -ItemType Directory -Path (Split-Path $OutputFile -Parent) -Force | Out-Null
    $lines | Set-Content -Path $OutputFile -Encoding utf8
}

function Test-SchemaEquivalenceAgainstProbe {
    $localSnapshot = Join-Path $script:ProbeSchemaDir 'local-columns.tsv'
    $probeSnapshot = Join-Path $script:ProbeSchemaDir 'probe-columns.tsv'

    if (-not (Test-Path -LiteralPath $probeSnapshot -PathType Leaf)) {
        throw 'PROBE_SCHEMA_SNAPSHOT_MISSING'
    }

    if (-not (Test-Path -LiteralPath $localSnapshot -PathType Leaf)) {
        Export-InformationSchemaSnapshot -DatabaseName $DbName -OutputFile $localSnapshot
    }

    $local = Get-Content -LiteralPath $localSnapshot -Encoding utf8 | Sort-Object
    $probe = Get-Content -LiteralPath $probeSnapshot -Encoding utf8 | Sort-Object

    if (($local -join "`n") -ne ($probe -join "`n")) {
        throw 'SCHEMA_EQUIVALENCE_MISMATCH'
    }
}

function Test-DataPostconditions {
    $lines = Invoke-MysqlTabularQuery -Sql @"
SELECT 'ai_routing_config_rows' AS k, COUNT(*) AS v FROM ai_routing_config
UNION ALL SELECT 'model_provider_auto_disabled', COUNT(*) FROM model_provider WHERE provider_code='auto' AND is_deleted=1
UNION ALL SELECT 'model_provider_rule_credential_source', COUNT(*) FROM model_provider WHERE provider_code='rule' AND credential_source='NONE' AND is_deleted=0
UNION ALL SELECT 'prompt_template_v29_candidates', COUNT(*) FROM prompt_template_version WHERE template_id=1 AND version_no=1 AND is_deleted=0 AND (user_prompt LIKE '%?%' OR system_prompt LIKE '%?%' OR system_prompt LIKE '%浣%')
UNION ALL SELECT 'prompt_template_v30_hex_match', COUNT(*) FROM prompt_template WHERE id IN (1,2) AND is_deleted=0 AND HEX(template_name) IN ('56756520E6A4A4E59CADE6B4B0E990A2E786B8E59E9A','41504920E98EBAE383A5E5BD9BE990A2E786B8E59E9A')
"@

    $map = @{}
    foreach ($line in $lines) {
        $parts = $line -split "`t", 2
        $map[$parts[0]] = [int]$parts[1]
    }

    if ($map['ai_routing_config_rows'] -lt 1) { throw 'DATA_POSTCONDITION_FAILED:ai_routing_config_rows' }
    if ($map['model_provider_auto_disabled'] -lt 1) { throw 'DATA_POSTCONDITION_FAILED:model_provider_auto_disabled' }
    if ($map['model_provider_rule_credential_source'] -lt 1) { throw 'DATA_POSTCONDITION_FAILED:model_provider_rule_credential_source' }
    if ($map['prompt_template_v29_candidates'] -gt 0) { throw 'DATA_POSTCONDITION_FAILED:prompt_template_v29_candidates' }
    if ($map['prompt_template_v30_hex_match'] -gt 0) { throw 'DATA_POSTCONDITION_FAILED:prompt_template_v30_hex_match' }
}

function New-ProbeDatabaseName {
    if (-not [string]::IsNullOrWhiteSpace($ProbeDatabaseName)) {
        if ($ProbeDatabaseName -notmatch '^codeforge_flyway_probe_[0-9]{8}_[0-9]{6}$') {
            throw 'INVALID_PROBE_DATABASE_NAME'
        }
        return $ProbeDatabaseName
    }
    return 'codeforge_flyway_probe_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
}

function Initialize-ProbeDatabase {
    $probeDb = New-ProbeDatabaseName
    $adminUser = $env:MYSQL_PROBE_ADMIN_USER
    $adminPassword = $env:MYSQL_PROBE_ADMIN_PASSWORD
    if ([string]::IsNullOrWhiteSpace($adminUser) -or [string]::IsNullOrWhiteSpace($adminPassword)) {
        throw 'MYSQL_PROBE_ADMIN_CREDENTIALS_REQUIRED'
    }

    $createSql = "CREATE DATABASE IF NOT EXISTS $probeDb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    Invoke-MysqlTabularQuery -DatabaseName 'mysql' -UserName $adminUser -Password $adminPassword -Sql $createSql | Out-Null

    $classpathFile = Join-Path $script:ProbeArtifactDir 'classpath.txt'
    $javaFile = Join-Path $script:ProbeArtifactDir 'ProbeFlywayMigrate.java'
    New-Item -ItemType Directory -Path $script:ProbeArtifactDir -Force | Out-Null

    @'
import org.flywaydb.core.Flyway;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ProbeFlywayMigrate {
    public static void main(String[] args) throws Exception {
        String jdbcUrl = args[0];
        String username = args[1];
        String password = args[2];
        String locations = args[3];
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations(locations.split(","))
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .validateOnMigrate(false)
                .ignoreMigrationPatterns("*:missing")
                .cleanDisabled(true)
                .load();
        var result = flyway.migrate();
        System.out.println("MIGRATIONS_EXECUTED=" + result.migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT installed_rank, version, description, type, script, checksum, installed_by, execution_time, success FROM flyway_schema_history WHERE version IN ('28','28.1','29','30','30.1','32','33') ORDER BY installed_rank")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    System.out.println(String.join("\t",
                            rs.getString("installed_rank"),
                            rs.getString("version"),
                            rs.getString("description"),
                            rs.getString("type"),
                            rs.getString("script"),
                            String.valueOf(rs.getObject("checksum")),
                            rs.getString("installed_by"),
                            rs.getString("execution_time"),
                            rs.getString("success")));
                }
            }
        }
    }
}
'@ | Set-Content -Path $javaFile -Encoding utf8

    Push-Location $script:ProjectRoot
    try {
        mvn -q dependency:build-classpath "-Dmdep.outputFile=$classpathFile" | Out-Null
    }
    finally {
        Pop-Location
    }

    $cp = Get-Content -LiteralPath $classpathFile -Raw
    $classDir = Join-Path $script:ProbeArtifactDir 'classes'
    New-Item -ItemType Directory -Path $classDir -Force | Out-Null
    javac -cp $cp -d $classDir $javaFile
    if ($LASTEXITCODE -ne 0) {
        throw 'PROBE_JAVA_COMPILE_FAILED'
    }

    $jdbcUrl = "jdbc:mysql://${DbHost}:${DbPort}/${probeDb}?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Shanghai"
    $locations = 'filesystem:sql/migrations,filesystem:sql/mysql-local'
    $probeOutput = java -cp "$classDir;$cp" ProbeFlywayMigrate $jdbcUrl $adminUser $adminPassword $locations 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw 'PROBE_FLYWAY_MIGRATE_FAILED'
    }

    $historyLines = $probeOutput | Where-Object { $_ -match '^\d+\t' }
    if (-not $historyLines) {
        throw 'PROBE_HISTORY_EMPTY'
    }

    $historyLines | Set-Content -Path $script:AuthoritativeHistoryFile -Encoding utf8
    Save-MigrationHashSnapshot | Out-Null
    Export-InformationSchemaSnapshot -DatabaseName $probeDb -OutputFile (Join-Path $script:ProbeSchemaDir 'probe-columns.tsv') -UserName $adminUser -Password $adminPassword
    $probeDb | Set-Content -Path (Join-Path $script:ProbeArtifactDir 'probe-database-name.txt') -Encoding utf8
    Write-CatchUpStatus "PROBE_READY:$probeDb"
}

function Invoke-CatchUpDryRun {
    Test-LocalDatabaseSafety
    Test-ExpectedGitHead
    Test-MigrationHashesUnchanged

    $history = Get-FlywayHistoryRows
    $highest = Get-HighestSuccessfulHistoryVersion -Rows $history
    if ($highest -ne 27) {
        throw "UNEXPECTED_HIGHEST_VERSION:$highest"
    }

    $missing = Test-MissingCatchUpVersions -Rows $history
    $planned = Get-PlannedCatchUpVersions
    if ($missing.Count -ne $planned.Count) {
        throw 'UNEXPECTED_MISSING_VERSION_SET'
    }

    Test-NoPartialCatchUpHistory -Rows $history
    $gateStatus = Get-LocalSchemaGateStatus
    if ($gateStatus -ne 'HISTORY_MISMATCH') {
        throw "UNEXPECTED_SCHEMA_GATE:$gateStatus"
    }

    Test-SchemaEquivalenceAgainstProbe
    Test-DataPostconditions

    $authoritative = Get-AuthoritativeCatchUpRows
    $nextRank = ($history | Measure-Object -Property installed_rank -Maximum).Maximum + 1

    Write-CatchUpStatus 'DRY_RUN'
    Write-CatchUpStatus "CURRENT_HIGHEST_VERSION=$highest"
    Write-CatchUpStatus ("MISSING_VERSIONS=" + ($missing -join ','))
    foreach ($row in $authoritative) {
        Write-CatchUpStatus ("PLAN_INSERT rank=$nextRank version=$($row.version) script=$($row.script) checksum=$($row.checksum)")
        $nextRank++
    }
    Write-CatchUpStatus 'SCHEMA_EQUIVALENCE=MATCH'
    Write-CatchUpStatus 'AUTHORIZATION_REQUIRED'
}

function Invoke-CatchUpApply {
    param([string]$Token)

    if (-not $Apply) {
        throw 'APPLY_SWITCH_REQUIRED'
    }
    if ([string]::IsNullOrWhiteSpace($Token)) {
        throw 'CONFIRMATION_TOKEN_REQUIRED'
    }

    $expectedToken = Get-Content -Path (Join-Path $script:ProbeArtifactDir 'confirmation-token.txt') -ErrorAction SilentlyContinue
    if (-not $expectedToken -or $Token -ne $expectedToken.Trim()) {
        throw 'CONFIRMATION_TOKEN_INVALID'
    }

    Invoke-CatchUpDryRun | Out-Null

    $authoritative = Get-AuthoritativeCatchUpRows
    $history = Get-FlywayHistoryRows
    $nextRank = ($history | Measure-Object -Property installed_rank -Maximum).Maximum + 1
    $installedBy = 'local-flyway-catchup'

    $statements = New-Object System.Collections.Generic.List[string]
    $statements.Add('START TRANSACTION')
    $statements.Add('SELECT installed_rank FROM flyway_schema_history ORDER BY installed_rank FOR UPDATE')

    foreach ($row in $authoritative) {
        $checksumValue = if ($null -eq $row.checksum) { 'NULL' } else { [string]$row.checksum }
        $escapedDescription = $row.description.Replace("'", "''")
        $sql = @"
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES ($nextRank, '$($row.version)', '$escapedDescription', '$($row.type)', '$($row.script)', $checksumValue, '$installedBy', CURRENT_TIMESTAMP, 0, 1)
"@
        $statements.Add($sql)
        $nextRank++
    }
    $statements.Add('COMMIT')

    $batchSql = ($statements -join ';')
    Invoke-MysqlTabularQuery -Sql $batchSql | Out-Null

    $finalStatus = Get-LocalSchemaGateStatus
    if ($finalStatus -ne 'READY') {
        throw "POST_APPLY_SCHEMA_GATE:$finalStatus"
    }

    Write-CatchUpStatus 'APPLY_SUCCESS'
    Write-CatchUpStatus 'SCHEMA_STATUS=READY'
}

function Remove-ProbeDatabase {
    $nameFile = Join-Path $script:ProbeArtifactDir 'probe-database-name.txt'
    if (-not (Test-Path -LiteralPath $nameFile -PathType Leaf)) {
        throw 'PROBE_DATABASE_NAME_FILE_MISSING'
    }
    $probeDb = (Get-Content -LiteralPath $nameFile -Raw).Trim()
    if ($probeDb -notmatch '^codeforge_flyway_probe_[0-9]{8}_[0-9]{6}$') {
        throw 'INVALID_PROBE_DATABASE_NAME'
    }

    $adminUser = $env:MYSQL_PROBE_ADMIN_USER
    $adminPassword = $env:MYSQL_PROBE_ADMIN_PASSWORD
    if ([string]::IsNullOrWhiteSpace($adminUser) -or [string]::IsNullOrWhiteSpace($adminPassword)) {
        throw 'MYSQL_PROBE_ADMIN_CREDENTIALS_REQUIRED'
    }

    Invoke-MysqlTabularQuery -DatabaseName 'mysql' -UserName $adminUser -Password $adminPassword -Sql "DROP DATABASE IF EXISTS $probeDb" | Out-Null
    Write-CatchUpStatus "PROBE_CLEANED:$probeDb"
}

if ($MyInvocation.InvocationName -ne '.') {
    if (Test-Path -LiteralPath $script:MigrationHashFile -PathType Leaf) {
        try {
            $saved = Get-Content -LiteralPath $script:MigrationHashFile -Raw | ConvertFrom-Json
            $currentPaths = @(Get-MigrationFilesForCatchUp | ForEach-Object { $_.RelativePath })
            $savedPaths = @($saved.files | ForEach-Object { $_.relativePath })
            if (($currentPaths | Sort-Object) -join '|' -ne ($savedPaths | Sort-Object) -join '|') {
                Remove-Item -LiteralPath $script:MigrationHashFile -Force
                Remove-Item -LiteralPath $script:AuthoritativeHistoryFile -Force -ErrorAction SilentlyContinue
            }
        }
        catch {
            Remove-Item -LiteralPath $script:MigrationHashFile -Force -ErrorAction SilentlyContinue
        }
    }

    if (-not (Test-Path -LiteralPath $script:MigrationHashFile -PathType Leaf)) {
        Save-MigrationHashSnapshot | Out-Null
    }

    if ($InitializeProbe) {
        Initialize-ProbeDatabase
        $token = [guid]::NewGuid().ToString('N')
        $token | Set-Content -Path (Join-Path $script:ProbeArtifactDir 'confirmation-token.txt') -Encoding utf8
        Write-CatchUpStatus 'CONFIRMATION_TOKEN_ISSUED'
        exit 0
    }

    if ($Apply) {
        try {
            Invoke-CatchUpApply -Token $ConfirmationToken
            exit 0
        }
        catch {
            Write-CatchUpStatus 'APPLY_FAILED'
            Write-CatchUpStatus $_.Exception.Message
            exit 5
        }
    }

    try {
        Invoke-CatchUpDryRun
        exit 0
    }
    catch {
        Write-CatchUpStatus 'DRY_RUN_BLOCKED'
        Write-CatchUpStatus $_.Exception.Message
        exit 4
    }
}
