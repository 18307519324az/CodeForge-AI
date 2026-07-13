param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot/.."),
    [int]$BackendPort = 8150,
    [int]$FrontendPort = 5182
)

$ErrorActionPreference = 'Stop'
$runDir = Join-Path $ProjectRoot '.run'
$runtimeIdentityPath = Join-Path $runDir 'runtime-identity.json'

function Normalize-PathText($pathText) {
    if (-not $pathText) {
        return $null
    }

    return $pathText.Replace('/', '\').TrimEnd('\').ToLowerInvariant()
}

function Read-PidValue($pidPath) {
    if (-not (Test-Path $pidPath)) {
        return $null
    }

    $pidText = (Get-Content $pidPath -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($pidText -and $pidText -match '^\d+$') {
        return [int]$pidText
    }

    return $null
}

function Get-CommandLine($processId) {
    try {
        return (Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $processId" -ErrorAction Stop).CommandLine
    } catch {
        return $null
    }
}

function Get-WorktreePathFromCommandLine($commandLine) {
    if (-not $commandLine) {
        return $null
    }

    $patterns = @(
        '(?<path>[A-Za-z]:\\[^"]+?)\\frontend\\node_modules',
        '(?<path>[A-Za-z]:\\[^"]+?)\\frontend\\',
        '(?<path>[A-Za-z]:\\[^"]+?)\\target\\',
        '(?<path>[A-Za-z]:\\[^"]+?)\\mvnw\.cmd',
        '(?<path>[A-Za-z]:\\[^"]+?)\\pom\.xml'
    )

    foreach ($pattern in $patterns) {
        $match = [regex]::Match($commandLine, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if ($match.Success) {
            return $match.Groups['path'].Value
        }
    }

    return $null
}

function Get-OwnershipInfo($commandLine, $savedPid, $listenerPid) {
    $currentRoot = Normalize-PathText $ProjectRoot
    $possibleRoot = Get-WorktreePathFromCommandLine $commandLine
    $normalizedPossibleRoot = Normalize-PathText $possibleRoot
    $savedPidMatchesListener = $false

    if ($savedPid -and $listenerPid -and ($savedPid -eq $listenerPid)) {
        $savedPidMatchesListener = $true
    }

    if ($normalizedPossibleRoot -and $currentRoot -and ($normalizedPossibleRoot -eq $currentRoot)) {
        return [pscustomobject]@{
            Ownership = 'CURRENT_WORKTREE'
            PossibleWorktree = $possibleRoot
            SavedPidMatchesListener = $savedPidMatchesListener
        }
    }

    if ($possibleRoot) {
        return [pscustomobject]@{
            Ownership = 'FOREIGN_WORKTREE'
            PossibleWorktree = $possibleRoot
            SavedPidMatchesListener = $savedPidMatchesListener
        }
    }

    if ($savedPidMatchesListener) {
        return [pscustomobject]@{
            Ownership = 'CURRENT_WORKTREE'
            PossibleWorktree = $null
            SavedPidMatchesListener = $true
        }
    }

    return [pscustomobject]@{
        Ownership = 'UNKNOWN'
        PossibleWorktree = $null
        SavedPidMatchesListener = $false
    }
}

function Get-PortListener($port) {
    return Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

function Get-ServiceStatus($label, $port, $pidFile) {
    $pidPath = Join-Path $runDir $pidFile
    $savedPid = Read-PidValue $pidPath
    $savedPidAlive = $false
    $savedPidName = $null

    if ($savedPid) {
        try {
            $savedProc = Get-Process -Id $savedPid -ErrorAction Stop
            $savedPidAlive = $true
            $savedPidName = $savedProc.ProcessName
        } catch {
            $savedPidAlive = $false
        }
    }

    $listener = Get-PortListener $port
    $listenerPid = $null
    $listenerName = $null
    $listenerCmd = $null

    if ($listener) {
        $listenerPid = $listener.OwningProcess
        try {
            $listenerProc = Get-Process -Id $listenerPid -ErrorAction Stop
            $listenerName = $listenerProc.ProcessName
        } catch {
            $listenerName = '<unknown>'
        }
        $listenerCmd = Get-CommandLine $listenerPid
    }

    $ownership = Get-OwnershipInfo -commandLine $listenerCmd -savedPid $savedPid -listenerPid $listenerPid

    [pscustomobject]@{
        Label = $label
        Port = $port
        PortListening = [bool]$listener
        ListenerPid = $listenerPid
        ListenerProcess = $listenerName
        ListenerCommandLine = $listenerCmd
        PidFile = $pidPath
        SavedPid = $savedPid
        SavedPidAlive = $savedPidAlive
        SavedPidProcess = $savedPidName
        Ownership = $ownership.Ownership
        PossibleWorktree = $ownership.PossibleWorktree
        SavedPidMatchesListener = $ownership.SavedPidMatchesListener
    }
}

Write-Host "=== Dev Process Status ===" -ForegroundColor Cyan
Write-Host "ProjectRoot: $ProjectRoot"
Write-Host ""

if (Test-Path $runtimeIdentityPath) {
    try {
        $runtimeIdentity = Get-Content $runtimeIdentityPath -Raw | ConvertFrom-Json
        Write-Host "[Runtime Database]" -ForegroundColor Yellow
        Write-Host ("Backend Profile: {0}" -f $runtimeIdentity.profile)
        Write-Host ("Database Type: {0}" -f $runtimeIdentity.databaseType)
        if ($runtimeIdentity.databaseType -eq 'MYSQL') {
            Write-Host ("Database Host: {0}" -f $runtimeIdentity.databaseHost)
            Write-Host ("Database Name: {0}" -f $runtimeIdentity.databaseName)
        } elseif ($runtimeIdentity.databaseType -eq 'H2_FILE') {
            Write-Host ("Database Path: {0}" -f $runtimeIdentity.databasePath)
        }
        Write-Host ("JDBC URL: {0}" -f $runtimeIdentity.jdbcUrlSanitized)
        Write-Host ""
    } catch {
        Write-Host "[Runtime Database] Unable to parse $runtimeIdentityPath" -ForegroundColor Yellow
        Write-Host ""
    }
} else {
    Write-Host "[Runtime Database] identity file not found at $runtimeIdentityPath" -ForegroundColor Yellow
    Write-Host ""
}

$backendStatus = Get-ServiceStatus -label 'Backend' -port $BackendPort -pidFile 'backend.pid'
$frontendStatus = Get-ServiceStatus -label 'Frontend' -port $FrontendPort -pidFile 'frontend.pid'

foreach ($item in @($backendStatus, $frontendStatus)) {
    Write-Host ("[{0}]" -f $item.Label) -ForegroundColor Yellow
    Write-Host ("Port {0}: {1}" -f $item.Port, ($(if ($item.PortListening) { 'LISTENING' } else { 'NOT LISTENING' })))
    if ($item.ListenerPid) {
        Write-Host ("Listener PID: {0}" -f $item.ListenerPid)
        Write-Host ("Listener Process: {0}" -f $item.ListenerProcess)
        if ($item.ListenerCommandLine) {
            Write-Host ("Listener Command: {0}" -f $item.ListenerCommandLine)
        }
        Write-Host ("Port Ownership: {0}" -f $item.Ownership)
        if ($item.PossibleWorktree) {
            Write-Host ("Possible Worktree: {0}" -f $item.PossibleWorktree)
        }
    }

    Write-Host ("PID File: {0}" -f $item.PidFile)
    if ($item.SavedPid) {
        Write-Host ("Saved PID: {0}" -f $item.SavedPid)
        Write-Host ("Saved PID Alive: {0}" -f ($(if ($item.SavedPidAlive) { 'YES' } else { 'NO' })))
        Write-Host ("Saved PID Matches Listener: {0}" -f ($(if ($item.SavedPidMatchesListener) { 'YES' } else { 'NO' })))
        if ($item.SavedPidProcess) {
            Write-Host ("Saved PID Process: {0}" -f $item.SavedPidProcess)
        }
    } else {
        Write-Host "Saved PID: <none>"
    }
    Write-Host ""
}

Write-Host "=== Dev status complete ===" -ForegroundColor Cyan
