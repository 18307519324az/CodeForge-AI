$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptRoot '..\..')).Path
$envModule = Join-Path $projectRoot 'scripts\lib\EnvFile.ps1'
$bootstrapScript = Join-Path $scriptRoot 'bootstrap-fresh-database.ps1'

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

. $envModule

$bootstrapContent = Get-Content -LiteralPath $bootstrapScript -Raw
Assert-True ($bootstrapContent -match 'B33_BASELINE_APPLIED') 'EmptyLocalDatabaseUsesB33Test'
Assert-True ($bootstrapContent -match 'V1_EXECUTED=false') 'V1IsNotExecutedForFreshDatabaseTest'
Assert-True ($bootstrapContent -match 'FLYWAY_VALIDATE_PASS') 'FlywayValidatePassesTest'
Assert-True ($bootstrapContent -match 'SCHEMA_STATUS=READY') 'SchemaCheckerReturnsReadyTest'
Assert-True ($bootstrapContent -match 'ALREADY_READY') 'SecondRunIsIdempotentTest'
Assert-True ($bootstrapContent -match 'NON_LOCAL_HOST') 'RejectsNonLocalHostTest'
Assert-True ($bootstrapContent -match 'PRODUCTION_LIKE_DATABASE_NAME') 'RejectsProductionLikeDatabaseNameTest'
Assert-True ($bootstrapContent -match 'NON_EMPTY_UNMANAGED_DATABASE') 'RejectsNonEmptyDatabaseTest'
Assert-True ($bootstrapContent -match 'DB_PASSWORD_REQUIRED') 'RejectsMissingPasswordTest'
Assert-True (-not ($bootstrapContent -match '--password=|-p\$DbPassword|-Dflyway\.password|FLYWAY_PASSWORD=.*\$script:BootstrapDbPassword')) 'DoesNotPutPasswordInCommandLineTest'

$tempEnv = Join-Path $env:TEMP "codeforge-envfile-$([Guid]::NewGuid()).env"
try {
    @(
        'DB_HOST=127.0.0.1'
        'DB_PASSWORD=secret'
        'AI_PROVIDER="rule"'
    ) | Set-Content -LiteralPath $tempEnv -Encoding ASCII

    $snapshot = Import-CodeForgeEnvFile -Path $tempEnv
    Assert-Equals '127.0.0.1' $env:DB_HOST 'EnvFileLoadsValuesTest'
    Assert-Equals 'secret' $env:DB_PASSWORD 'DoesNotPrintPasswordTest'
    Restore-CodeForgeEnvironment -Snapshot $snapshot
    Assert-True ([Environment]::GetEnvironmentVariable('DB_PASSWORD', 'Process') -ne 'secret') 'RestoresEnvironmentVariablesTest'
}
finally {
    if (Test-Path -LiteralPath $tempEnv) {
        Remove-Item -LiteralPath $tempEnv -Force
    }
}

$tempEnv = Join-Path $env:TEMP "codeforge-envfile-$([Guid]::NewGuid()).env"
try {
    'DB_PASSWORD=$(Get-Content secret.txt)' | Set-Content -LiteralPath $tempEnv -Encoding ASCII
    try {
        Read-CodeForgeEnvFile -Path $tempEnv | Out-Null
        Assert-True $false 'EnvFileRejectsPowerShellExpressionTest'
    }
    catch {
        Assert-True ($_.Exception.Message -like 'ENV_FILE_UNSUPPORTED_EXPRESSION:*') 'EnvFileRejectsPowerShellExpressionTest'
    }
}
finally {
    if (Test-Path -LiteralPath $tempEnv) {
        Remove-Item -LiteralPath $tempEnv -Force
    }
}

$tempEnv = Join-Path $env:TEMP "codeforge-envfile-$([Guid]::NewGuid()).env"
try {
    @('DB_PASSWORD=a', 'DB_PASSWORD=b') | Set-Content -LiteralPath $tempEnv -Encoding ASCII
    try {
        Read-CodeForgeEnvFile -Path $tempEnv | Out-Null
        Assert-True $false 'EnvFileRejectsDuplicateVariableTest'
    }
    catch {
        Assert-True ($_.Exception.Message -like 'ENV_FILE_DUPLICATE_VARIABLE:*') 'EnvFileRejectsDuplicateVariableTest'
    }
}
finally {
    if (Test-Path -LiteralPath $tempEnv) {
        Remove-Item -LiteralPath $tempEnv -Force
    }
}

$tempEnv = Join-Path $env:TEMP "codeforge-envfile-$([Guid]::NewGuid()).env"
$previous = [Environment]::GetEnvironmentVariable('DB_HOST', 'Process')
try {
    $env:DB_HOST = 'process-value'
    'DB_HOST=file-value' | Set-Content -LiteralPath $tempEnv -Encoding ASCII
    $snapshot = Import-CodeForgeEnvFile -Path $tempEnv
    Assert-Equals 'process-value' $env:DB_HOST 'EnvFileDoesNotOverrideProcessEnvironmentByDefaultTest'
    Restore-CodeForgeEnvironment -Snapshot $snapshot
}
finally {
    if ($null -eq $previous) { Remove-Item Env:DB_HOST -ErrorAction SilentlyContinue } else { $env:DB_HOST = $previous }
    if (Test-Path -LiteralPath $tempEnv) {
        Remove-Item -LiteralPath $tempEnv -Force
    }
}

Write-Host ''
Write-Host "Bootstrap tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
