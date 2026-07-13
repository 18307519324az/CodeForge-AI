$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$catchUpScript = Join-Path $scriptRoot 'catch-up-local-flyway-history.ps1'

$passed = 0
$failed = 0

function Assert-True {
    param([bool]$Condition, [string]$Name)
    if ($Condition) { Write-Host "PASS: $Name"; $script:passed++ } else { Write-Host "FAIL: $Name"; $script:failed++ }
}

function Assert-Equals {
    param($Expected, $Actual, [string]$Name)
    if ($Expected -eq $Actual) { Write-Host "PASS: $Name"; $script:passed++ } else { Write-Host "FAIL: $Name (expected '$Expected', actual '$Actual')"; $script:failed++ }
}

. $catchUpScript

$scriptContent = Get-Content -LiteralPath $catchUpScript -Raw
Assert-True (-not ($scriptContent -match 'Invoke-Expression')) 'ScriptDoesNotUseInvokeExpressionTest'
Assert-True (-not ($scriptContent -match '--password=|-p\$DbPassword')) 'CatchUpDoesNotPrintPasswordTest'

try {
    $DbHost = 'prod-db.example.com'
    Test-LocalDatabaseSafety
    Assert-True $false 'CatchUpRejectsNonLocalDatabaseTest'
}
catch {
    Assert-Equals 'NON_LOCAL_HOST' $_.Exception.Message 'CatchUpRejectsNonLocalDatabaseTest'
}

$DbHost = 'localhost'

$ExpectedGitHead = '0000000000000000000000000000000000000000'
try {
    Test-ExpectedGitHead
    Assert-True $false 'CatchUpRejectsUnexpectedGitHeadTest'
}
catch {
    Assert-True ($_.Exception.Message -like 'UNEXPECTED_GIT_HEAD:*') 'CatchUpRejectsUnexpectedGitHeadTest'
}
$ExpectedGitHead = $null

$rows = @(
    [pscustomobject]@{ installed_rank = 1; version = '27'; description = 'x'; type = 'SQL'; script = 'V27.sql'; checksum = 1; installed_by = 'u'; execution_time = 0; success = 1 }
    [pscustomobject]@{ installed_rank = 2; version = '28'; description = 'x'; type = 'SQL'; script = 'V28.sql'; checksum = 1; installed_by = 'u'; execution_time = 0; success = 1 }
)
try {
    Test-NoPartialCatchUpHistory -Rows $rows
    Assert-True $false 'CatchUpRejectsExistingPartialHistoryTest'
}
catch {
    Assert-Equals 'PARTIAL_CATCHUP_HISTORY_PRESENT' $_.Exception.Message 'CatchUpRejectsExistingPartialHistoryTest'
}

$probeDir = Join-Path (Resolve-Path (Join-Path $scriptRoot '..\..')).Path '.local-data\flyway-probe-test'
if (Test-Path $probeDir) { Remove-Item -Recurse -Force $probeDir }
New-Item -ItemType Directory -Path $probeDir -Force | Out-Null
$script:ProbeArtifactDir = $probeDir
$script:AuthoritativeHistoryFile = Join-Path $probeDir 'authoritative-history-v28-v33.tsv'
$script:ProbeSchemaDir = Join-Path $probeDir 'schema-snapshots'
$script:MigrationHashFile = Join-Path $probeDir 'migration-file-hashes.json'

Save-MigrationHashSnapshot | Out-Null
'1	28	app publication	SQL	V28__app_publication.sql	12345	flyway	10	1' | Set-Content -Path $script:AuthoritativeHistoryFile -Encoding utf8

$authoritative = Get-AuthoritativeCatchUpRows
Assert-Equals 12345 $authoritative[0].checksum 'CatchUpUsesProbeChecksumsTest'

$saved = Get-Content -LiteralPath $script:MigrationHashFile -Raw | ConvertFrom-Json
$saved.gitHead = 'deadbeef'
$saved | ConvertTo-Json -Depth 6 | Set-Content -Path $script:MigrationHashFile -Encoding utf8
try {
    Test-MigrationHashesUnchanged
    Assert-True $false 'CatchUpRejectsChangedMigrationHashTest'
}
catch {
    Assert-Equals 'MIGRATION_HASH_SNAPSHOT_HEAD_MISMATCH' $_.Exception.Message 'CatchUpRejectsChangedMigrationHashTest'
}

New-Item -ItemType Directory -Path $script:ProbeSchemaDir -Force | Out-Null
'generation_task|prompt_template_id|bigint|YES||' | Set-Content -Path (Join-Path $script:ProbeSchemaDir 'probe-columns.tsv') -Encoding utf8
'generation_task|prompt_template_id|bigint|NO||' | Set-Content -Path (Join-Path $script:ProbeSchemaDir 'local-columns.tsv') -Encoding utf8
try {
    Test-SchemaEquivalenceAgainstProbe
    Assert-True $false 'CatchUpRejectsSchemaDifferenceTest'
}
catch {
    Assert-Equals 'SCHEMA_EQUIVALENCE_MISMATCH' $_.Exception.Message 'CatchUpRejectsSchemaDifferenceTest'
}

try {
    Invoke-CatchUpApply -Token 'missing'
    Assert-True $false 'CatchUpRequiresExplicitApplyTest'
}
catch {
    Assert-Equals 'APPLY_SWITCH_REQUIRED' $_.Exception.Message 'CatchUpRequiresExplicitApplyTest'
}

$Apply = $true
'expected-token' | Set-Content -Path (Join-Path $probeDir 'confirmation-token.txt') -Encoding utf8
try {
    Invoke-CatchUpApply -Token 'wrong-token'
    Assert-True $false 'CatchUpRequiresConfirmationTokenTest'
}
catch {
    Assert-Equals 'CONFIRMATION_TOKEN_INVALID' $_.Exception.Message 'CatchUpRequiresConfirmationTokenTest'
}
$Apply = $false

$output = & powershell -NoProfile -File $catchUpScript 2>&1 | Out-String
Assert-True ($scriptContent -match 'START TRANSACTION') 'CatchUpTransactionRollsBackOnPartialFailureTest'
Assert-True ($scriptContent -match 'COMMIT') 'CatchUpUsesTransactionCommitTest'

$completeRows = @(
    [pscustomobject]@{ installed_rank = 1; version = '27'; description = 'x'; type = 'SQL'; script = 'V27.sql'; checksum = 1; installed_by = 'u'; execution_time = 0; success = 1 }
    [pscustomobject]@{ installed_rank = 2; version = '28'; description = 'x'; type = 'SQL'; script = 'V28.sql'; checksum = 1; installed_by = 'u'; execution_time = 0; success = 1 }
    [pscustomobject]@{ installed_rank = 3; version = '28.1'; description = 'x'; type = 'SQL'; script = 'V28_1.sql'; checksum = 1; installed_by = 'u'; execution_time = 0; success = 1 }
    [pscustomobject]@{ installed_rank = 4; version = '29'; description = 'x'; type = 'SQL'; script = 'V29.sql'; checksum = 1; installed_by = 'u'; execution_time = 0; success = 1 }
    [pscustomobject]@{ installed_rank = 5; version = '30'; description = 'x'; type = 'SQL'; script = 'V30.sql'; checksum = 1; installed_by = 'u'; execution_time = 0; success = 1 }
    [pscustomobject]@{ installed_rank = 6; version = '30.1'; description = 'x'; type = 'SQL'; script = 'V30_1.sql'; checksum = 1; installed_by = 'u'; execution_time = 0; success = 1 }
    [pscustomobject]@{ installed_rank = 7; version = '32'; description = 'x'; type = 'SQL'; script = 'V32.sql'; checksum = 1; installed_by = 'u'; execution_time = 0; success = 1 }
    [pscustomobject]@{ installed_rank = 8; version = '33'; description = 'x'; type = 'SQL'; script = 'V33.sql'; checksum = 1; installed_by = 'u'; execution_time = 0; success = 1 }
)
$missingComplete = Test-MissingCatchUpVersions -Rows $completeRows
Assert-Equals 0 $missingComplete.Count 'CatchUpSecondRunMakesNoChangesTest'

Assert-True ($output -match 'DRY_RUN_BLOCKED|AUTHORIZATION_REQUIRED|DRY_RUN') 'CatchUpDefaultsToDryRunTest'

if (Test-Path $probeDir) { Remove-Item -Recurse -Force $probeDir }

Write-Host ''
Write-Host "Catch-up tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
