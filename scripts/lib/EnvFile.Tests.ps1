$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptRoot '..\..')).Path
$envModule = Join-Path $projectRoot 'scripts\lib\EnvFile.ps1'
. $envModule

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

function New-TempEnvFile {
    param([string[]]$Lines)
    $path = Join-Path ([System.IO.Path]::GetTempPath()) "codeforge-envfile-$([Guid]::NewGuid()).env"
    $Lines | Set-Content -LiteralPath $path -Encoding ASCII
    return $path
}

function Read-SinglePassword {
    param([string]$Line)
    $path = New-TempEnvFile -Lines @($Line)
    try {
        $values = Read-CodeForgeEnvFile -Path $path
        return [string]$values['DB_PASSWORD']
    }
    finally {
        Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
    }
}

Assert-Equals 'a&b' (Read-SinglePassword 'DB_PASSWORD="a&b"') 'PasswordContainingAmpersandTest'
Assert-Equals 'a|b' (Read-SinglePassword 'DB_PASSWORD="a|b"') 'PasswordContainingPipeTest'
Assert-Equals 'a{b}' (Read-SinglePassword 'DB_PASSWORD="a{b}"') 'PasswordContainingBracesTest'
Assert-Equals 'a$b' (Read-SinglePassword 'DB_PASSWORD="a$b"') 'PasswordContainingDollarTest'
Assert-Equals 'alpha beta gamma' (Read-SinglePassword 'DB_PASSWORD="alpha beta gamma"') 'QuotedPasswordWithSpacesTest'

$malformed = New-TempEnvFile -Lines @('DB_PASSWORD="unterminated')
try {
    Read-CodeForgeEnvFile -Path $malformed | Out-Null
    Assert-True $false 'RejectsMalformedQuotedValueTest'
}
catch {
    Assert-True ($_.Exception.Message -like 'ENV_FILE_MALFORMED_QUOTED_VALUE:*') 'RejectsMalformedQuotedValueTest'
}
finally {
    Remove-Item -LiteralPath $malformed -Force -ErrorAction SilentlyContinue
}

$expression = New-TempEnvFile -Lines @('DB_PASSWORD=$(Get-Date)')
try {
    Read-CodeForgeEnvFile -Path $expression | Out-Null
    Assert-True $false 'NeverEvaluatesValueTest'
}
catch {
    Assert-True ($_.Exception.Message -like 'ENV_FILE_UNSUPPORTED_EXPRESSION:*') 'NeverEvaluatesValueTest'
}
finally {
    Remove-Item -LiteralPath $expression -Force -ErrorAction SilentlyContinue
}

$secretValue = 'secret-with-&-|-{braces}-$'
$secretFile = New-TempEnvFile -Lines @("DB_PASSWORD=`"$secretValue`"")
try {
    $script:secretSnapshot = $null
    $output = & {
        $script:secretSnapshot = Import-CodeForgeEnvFile -Path $secretFile -Override
    } 6>&1 | Out-String
    Assert-True (-not $output.Contains($secretValue)) 'DoesNotPrintValueTest'
}
finally {
    if ($null -ne $script:secretSnapshot) {
        Restore-CodeForgeEnvironment -Snapshot $script:secretSnapshot
    }
    else {
        [Environment]::SetEnvironmentVariable('DB_PASSWORD', $null, 'Process')
    }
    Remove-Item -LiteralPath $secretFile -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "EnvFile tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
