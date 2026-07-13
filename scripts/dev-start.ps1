param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot/.."),
    [int]$BackendPort = 8150,
    [int]$FrontendPort = 5182,
    [ValidateSet('local', 'dev-h2')]
    [string]$Profile = 'local',
    [string]$EnvFile = '.env.local'
)

$ErrorActionPreference = 'Stop'
$runDir = Join-Path $ProjectRoot '.run'
$frontendRoot = Join-Path $ProjectRoot 'frontend'
$envFileModule = Join-Path $ProjectRoot 'scripts\lib\EnvFile.ps1'
. $envFileModule

function Normalize-PathText($pathText) {
    if (-not $pathText) {
        return $null
    }

    return $pathText.Replace('/', '\').TrimEnd('\').ToLowerInvariant()
}

function Ensure-RunDir() {
    if (-not (Test-Path $runDir)) {
        New-Item -ItemType Directory -Path $runDir | Out-Null
    }
}

function Get-PortListener($port) {
    return Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
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

function Read-PidValue($path) {
    if (-not (Test-Path $path)) {
        return $null
    }

    $pidText = (Get-Content $path -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($pidText -and $pidText -match '^\d+$') {
        return [int]$pidText
    }

    return $null
}

function Get-PortOccupancyInfo($port, $pidFileName) {
    $listener = Get-PortListener $port
    if (-not $listener) {
        return $null
    }

    $listenerPid = $listener.OwningProcess
    $listenerProcess = $null
    try {
        $listenerProcess = (Get-Process -Id $listenerPid -ErrorAction Stop).ProcessName
    } catch {
        $listenerProcess = '<unknown>'
    }

    $listenerCommandLine = Get-CommandLine $listenerPid
    $possibleWorktree = Get-WorktreePathFromCommandLine $listenerCommandLine
    $currentRoot = Normalize-PathText $ProjectRoot
    $savedPid = Read-PidValue (Join-Path $runDir $pidFileName)
    $savedPidMatchesListener = $false
    if ($savedPid -and ($savedPid -eq $listenerPid)) {
        $savedPidMatchesListener = $true
    }

    $ownership = 'UNKNOWN'
    $normalizedPossibleRoot = Normalize-PathText $possibleWorktree
    if ($normalizedPossibleRoot -and $currentRoot -and ($normalizedPossibleRoot -eq $currentRoot)) {
        $ownership = 'CURRENT_WORKTREE'
    } elseif ($possibleWorktree) {
        $ownership = 'FOREIGN_WORKTREE'
    } elseif ($savedPidMatchesListener) {
        $ownership = 'CURRENT_WORKTREE'
    }

    return [pscustomobject]@{
        Port = $port
        ListenerPid = $listenerPid
        ListenerProcess = $listenerProcess
        ListenerCommandLine = $listenerCommandLine
        PossibleWorktree = $possibleWorktree
        SavedPid = $savedPid
        SavedPidMatchesListener = $savedPidMatchesListener
        Ownership = $ownership
    }
}

function Write-PortOccupancyError($label, $info) {
    Write-Host "$label port $($info.Port) is already occupied." -ForegroundColor Red
    Write-Host "Listener PID: $($info.ListenerPid)"
    Write-Host "Listener Process: $($info.ListenerProcess)"
    Write-Host "Port Ownership: $($info.Ownership)"
    if ($info.PossibleWorktree) {
        Write-Host "Possible Worktree: $($info.PossibleWorktree)"
    }
    if ($info.ListenerCommandLine) {
        Write-Host "Listener Command: $($info.ListenerCommandLine)"
    }
    if ($info.SavedPid) {
        Write-Host "Saved PID: $($info.SavedPid)"
        Write-Host "Saved PID Matches Listener: $(if ($info.SavedPidMatchesListener) { 'YES' } else { 'NO' })"
    } else {
        Write-Host "Saved PID: <none>"
    }
    Write-Host "Port is occupied by another process. Please run scripts/dev-stop.ps1 in the corresponding worktree, or close the corresponding terminal manually." -ForegroundColor Yellow
}

function Wait-ForListener($port, $timeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    do {
        $listener = Get-PortListener $port
        if ($listener) {
            return $listener
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    return $null
}

function Write-PortFile($name, $port) {
    Set-Content -Path (Join-Path $runDir $name) -Value $port -Encoding ASCII
}

function Write-PidFile($name, $processId) {
    Set-Content -Path (Join-Path $runDir $name) -Value $processId -Encoding ASCII
    Write-Host "Recorded PID file: $name -> $processId" -ForegroundColor DarkGray
}

function Remove-IfExists($path) {
    if (Test-Path $path) {
        Remove-Item $path -Force -ErrorAction SilentlyContinue
    }
}

function Ensure-FrontendDependencies() {
    $viteCmd = Join-Path $frontendRoot 'node_modules\.bin\vite.cmd'
    if (Test-Path $viteCmd) {
        return
    }

    Write-Host "Frontend dependencies not found. Running npm install..." -ForegroundColor Yellow
    Push-Location $frontendRoot
    try {
        & cmd.exe /c "npm.cmd install" | Out-Host
    } finally {
        Pop-Location
    }
}

function Get-BackendStartCommand($backendPort, $runtimeProfile) {
    if ($runtimeProfile -eq 'dev-h2') {
        return "set SERVER_PORT=$backendPort && .\mvnw.cmd -Dspring-boot.run.main-class=com.codeforge.ai.CodeForgeAiApplication -Dspring-boot.run.profiles=dev -Dspring-boot.run.useTestClasspath=true -Dspring-boot.run.additional-classpath-elements=src/test/resources -Dspring-boot.run.arguments=--server.servlet.context-path=/api spring-boot:start"
    }
    return "set SERVER_PORT=$backendPort && .\mvnw.cmd -Dspring-boot.run.main-class=com.codeforge.ai.CodeForgeAiApplication -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.servlet.context-path=/api spring-boot:start"
}

function Start-Backend() {
    $occupancy = Get-PortOccupancyInfo -port $BackendPort -pidFileName 'backend.pid'
    if ($occupancy) {
        if ($occupancy.Ownership -eq 'CURRENT_WORKTREE') {
            Write-Host "Backend port $BackendPort is already listening for the current worktree. PID=$($occupancy.ListenerPid)" -ForegroundColor Yellow
            Write-PidFile 'backend.pid' $occupancy.ListenerPid
            Write-PortFile 'backend.port' $BackendPort
            return
        }

        Write-PortOccupancyError -label 'Backend' -info $occupancy
        throw "Backend port $BackendPort is occupied."
    }

    $buildStdout = Join-Path $runDir 'backend.build.out.log'
    $buildStderr = Join-Path $runDir 'backend.build.err.log'
    $stdout = Join-Path $runDir 'backend.out.log'
    $stderr = Join-Path $runDir 'backend.err.log'
    Remove-IfExists $buildStdout
    Remove-IfExists $buildStderr
    Remove-IfExists $stdout
    Remove-IfExists $stderr

    $buildCommand = ".\mvnw.cmd -DskipTests compile 1>`"$buildStdout`" 2>`"$buildStderr`""
    $command = "$(Get-BackendStartCommand -backendPort $BackendPort -runtimeProfile $Profile) 1>`"$stdout`" 2>`"$stderr`""
    Push-Location $ProjectRoot
    try {
        & cmd.exe /c $buildCommand
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Backend compile failed. Check $buildStdout" -ForegroundColor Red
            return
        }
        & cmd.exe /c $command
    } finally {
        Pop-Location
    }

    $listener = Wait-ForListener -port $BackendPort -timeoutSeconds 20
    if (-not $listener) {
        Write-Host "Backend did not open port $BackendPort. Check $stdout" -ForegroundColor Red
        Remove-IfExists (Join-Path $runDir 'backend.pid')
        Remove-IfExists (Join-Path $runDir 'backend.port')
        return
    }

    Write-PidFile 'backend.pid' $listener.OwningProcess
    Write-PortFile 'backend.port' $BackendPort
    Write-Host "Backend is listening on $BackendPort. PID=$($listener.OwningProcess)" -ForegroundColor Green
}

function Start-Frontend() {
    $occupancy = Get-PortOccupancyInfo -port $FrontendPort -pidFileName 'frontend.pid'
    if ($occupancy) {
        if ($occupancy.Ownership -eq 'CURRENT_WORKTREE') {
            Write-Host "Frontend port $FrontendPort is already listening for the current worktree. PID=$($occupancy.ListenerPid)" -ForegroundColor Yellow
            Write-PidFile 'frontend.pid' $occupancy.ListenerPid
            Write-PortFile 'frontend.port' $FrontendPort
            return
        }

        Write-PortOccupancyError -label 'Frontend' -info $occupancy
        throw "Frontend port $FrontendPort is occupied."
    }

    $stdout = Join-Path $runDir 'frontend.out.log'
    $stderr = Join-Path $runDir 'frontend.err.log'
    Remove-IfExists $stdout
    Remove-IfExists $stderr

    Ensure-FrontendDependencies

    $apiBaseUrl = "http://127.0.0.1:$BackendPort/api/v1"
    $appBaseUrl = "http://127.0.0.1:$BackendPort/api"
    $apiProxyTarget = "http://127.0.0.1:$BackendPort"
    $command = "/c set ""VITE_API_BASE_URL=$apiBaseUrl"" && set ""VITE_APP_BASE_URL=$appBaseUrl"" && set ""VITE_API_PROXY_TARGET=$apiProxyTarget"" && npm.cmd run dev -- --host 127.0.0.1 --port $FrontendPort"
    $proc = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList $command `
        -WorkingDirectory $frontendRoot `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru

    Write-Host "Frontend start requested. WrapperPID=$($proc.Id), port=$FrontendPort" -ForegroundColor Green

    $listener = Wait-ForListener -port $FrontendPort -timeoutSeconds 20
    if ($listener) {
        Write-PidFile 'frontend.pid' $listener.OwningProcess
        Write-PortFile 'frontend.port' $FrontendPort
        Write-Host "Frontend is listening on $FrontendPort. PID=$($listener.OwningProcess)" -ForegroundColor Green
        return
    }

    if (-not (Get-Process -Id $proc.Id -ErrorAction SilentlyContinue)) {
        Write-Host "Frontend process exited before opening port $FrontendPort. Check $stdout" -ForegroundColor Red
        Remove-IfExists (Join-Path $runDir 'frontend.pid')
        Remove-IfExists (Join-Path $runDir 'frontend.port')
        return
    }

    Write-PidFile 'frontend.pid' $proc.Id
    Write-PortFile 'frontend.port' $FrontendPort
    Write-Host "Frontend wrapper is still running, but port $FrontendPort is not listening yet. Check $stdout" -ForegroundColor Yellow
}

$envSnapshot = $null
try {
    $envPath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $ProjectRoot $EnvFile }
    $envSnapshot = Import-CodeForgeEnvFile -Path $envPath
    Ensure-RunDir

    Write-Host "=== Dev Process Start ===" -ForegroundColor Cyan
    Write-Host "ProjectRoot: $ProjectRoot"
    Write-Host "Runtime Profile: $Profile"
    Write-Host ""
    $startFailed = $false

    try {
        Start-Backend
    } catch {
        Write-Host "Backend start failed: $($_.Exception.Message)" -ForegroundColor Red
        $startFailed = $true
    }

    Start-Sleep -Seconds 2

    try {
        Start-Frontend
    } catch {
        Write-Host "Frontend start failed: $($_.Exception.Message)" -ForegroundColor Red
        $startFailed = $true
    }

    Start-Sleep -Seconds 2

    Write-Host ""
    & (Join-Path $PSScriptRoot 'dev-status.ps1') -ProjectRoot $ProjectRoot -BackendPort $BackendPort -FrontendPort $FrontendPort

    if ($startFailed) {
        exit 1
    }
}
finally {
    if ($null -ne $envSnapshot) {
        Restore-CodeForgeEnvironment -Snapshot $envSnapshot
    }
}
