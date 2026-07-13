$ErrorActionPreference = 'Stop'

function Convert-CodeForgeEnvValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [string]$Value
    )

    if ($Value.StartsWith("'")) {
        if (-not $Value.EndsWith("'") -or $Value.Length -lt 2) {
            throw "ENV_FILE_MALFORMED_QUOTED_VALUE:$Name"
        }
        return $Value.Substring(1, $Value.Length - 2).Replace("''", "'")
    }

    if ($Value.StartsWith('"')) {
        if (-not $Value.EndsWith('"') -or $Value.Length -lt 2) {
            throw "ENV_FILE_MALFORMED_QUOTED_VALUE:$Name"
        }
        $inner = $Value.Substring(1, $Value.Length - 2)
        $builder = [System.Text.StringBuilder]::new()
        for ($i = 0; $i -lt $inner.Length; $i++) {
            $ch = $inner[$i]
            if ($ch -ne '\') {
                [void]$builder.Append($ch)
                continue
            }
            if ($i -eq ($inner.Length - 1)) {
                throw "ENV_FILE_MALFORMED_QUOTED_VALUE:$Name"
            }
            $i++
            $next = $inner[$i]
            switch ($next) {
                '"' { [void]$builder.Append('"') }
                '\' { [void]$builder.Append('\') }
                'n' { [void]$builder.Append("`n") }
                'r' { [void]$builder.Append("`r") }
                't' { [void]$builder.Append("`t") }
                default { [void]$builder.Append($next) }
            }
        }
        return $builder.ToString()
    }

    if ($Value.EndsWith("'") -or $Value.EndsWith('"')) {
        throw "ENV_FILE_MALFORMED_QUOTED_VALUE:$Name"
    }
    if ($Value -match '\$\(|`') {
        throw "ENV_FILE_UNSUPPORTED_EXPRESSION:$Name"
    }
    return $Value
}

function Read-CodeForgeEnvFile {
    param(
        [string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return @{}
    }

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if (-not $resolved) {
        return @{}
    }

    $values = @{}
    $lineNumber = 0
    foreach ($rawLine in Get-Content -LiteralPath $resolved.Path) {
        $lineNumber++
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
            continue
        }

        $equals = $line.IndexOf('=')
        if ($equals -le 0) {
            throw "ENV_FILE_INVALID_LINE:$lineNumber"
        }

        $name = $line.Substring(0, $equals).Trim()
        $value = $line.Substring($equals + 1).Trim()

        if ($name -notmatch '^[A-Z][A-Z0-9_]*$') {
            throw "ENV_FILE_INVALID_NAME:$name"
        }
        if ($values.ContainsKey($name)) {
            throw "ENV_FILE_DUPLICATE_VARIABLE:$name"
        }
        $values[$name] = Convert-CodeForgeEnvValue -Name $name -Value $value
    }

    return $values
}

function Import-CodeForgeEnvFile {
    param(
        [string]$Path,
        [switch]$Override
    )

    $values = Read-CodeForgeEnvFile -Path $Path
    $previous = @{}

    foreach ($name in $values.Keys) {
        $existing = [Environment]::GetEnvironmentVariable($name, 'Process')
        $previous[$name] = $existing

        if ($null -ne $existing -and -not $Override) {
            Write-Host "EnvFile: $name PRESENT"
            continue
        }

        [Environment]::SetEnvironmentVariable($name, [string]$values[$name], 'Process')
        Write-Host "EnvFile: $name PRESENT"
    }

    return [pscustomobject]@{
        Path = $Path
        Values = $values
        Previous = $previous
    }
}

function Restore-CodeForgeEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        $Snapshot
    )

    foreach ($name in $Snapshot.Values.Keys) {
        if ($Snapshot.Previous.ContainsKey($name) -and $null -ne $Snapshot.Previous[$name]) {
            [Environment]::SetEnvironmentVariable($name, [string]$Snapshot.Previous[$name], 'Process')
        }
        else {
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
    }
}
