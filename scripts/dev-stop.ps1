param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot/..")
)

$ErrorActionPreference = 'Stop'
$runDir = "$ProjectRoot/.run"

Write-Host "=== Dev Process Stop ===" -ForegroundColor Cyan
Write-Host ""

function Get-CommandLine($processId) {
    try {
        return (Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $processId" -ErrorAction Stop).CommandLine
    } catch { return $null }
}

function Stop-SavedPid($pidFile, $label, $allowedPatterns) {
    $pidPath = "$runDir/$pidFile"
    if (-not (Test-Path $pidPath)) {
        Write-Host ("${label}: no PID file at ${pidPath}, skipping.") -ForegroundColor Yellow
        return $false
    }

    $pidStr = Get-Content $pidPath -ErrorAction SilentlyContinue
    if (-not $pidStr -or -not ($pidStr -match '^\d+$')) {
        Write-Host ("${label}: invalid PID in ${pidPath}, removing.") -ForegroundColor Yellow
        Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
        return $false
    }

    $targetPid = [int]$pidStr
    try {
        $proc = Get-Process -Id $targetPid -ErrorAction Stop
        $cmdLine = Get-CommandLine -processId $targetPid

        if (-not $cmdLine) {
            Write-Host "${label}: PID ${targetPid} exists but cannot read command line. Skipping (manual review needed)." -ForegroundColor Red
            return $false
        }

        # Verify: command line must match at least one allowed pattern for our project
        $matched = $false
        foreach ($pattern in $allowedPatterns) {
            if ($cmdLine -match $pattern) {
                $matched = $true
                break
            }
        }

        if (-not $matched) {
            Write-Host "${label}: PID ${targetPid} does not match any allowed pattern. SKIPPING." -ForegroundColor Red
            Write-Host "  Command: $cmdLine" -ForegroundColor Gray
            Write-Host "  Allowed patterns: $($allowedPatterns -join ', ')" -ForegroundColor Gray
            return $false
        }

        Write-Host ("${label}: stopping PID ${targetPid}...") -ForegroundColor Yellow
        Stop-Process -Id $targetPid -Force -ErrorAction Stop
        Write-Host ("${label}: stopped.") -ForegroundColor Green

    } catch {
        Write-Host "${label}: PID ${targetPid} not found (already stopped)." -ForegroundColor Gray
    }

    Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
    return $true
}

Stop-SavedPid -pidFile "backend.pid" -label "Backend" -allowedPatterns @("codeforge", "codeforge-ai")
Stop-SavedPid -pidFile "frontend.pid" -label "Frontend" -allowedPatterns @("vite", "node.*dev", "npm(.cmd)?\\s+run\\s+dev")

# Clean up port files
@("backend.port", "frontend.port") | ForEach-Object {
    $p = "$runDir/$_"
    if (Test-Path $p) { Remove-Item $p -Force -ErrorAction SilentlyContinue }
}

Write-Host ""
Write-Host "=== Dev stop complete ===" -ForegroundColor Cyan
