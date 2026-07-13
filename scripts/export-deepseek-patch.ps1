param(
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $desktopPath = [Environment]::GetFolderPath("Desktop")
    $OutputPath = Join-Path $desktopPath "deepseek.patch"
}

$patchDir = Split-Path -Parent $OutputPath
if (-not (Test-Path $patchDir)) {
    New-Item -ItemType Directory -Path $patchDir | Out-Null
}

git add -A
if ($LASTEXITCODE -ne 0) {
    throw "git add -A failed"
}

$outputPathForCmd = $OutputPath.Replace('"', '""')
cmd /c "git -c core.safecrlf=false -c core.autocrlf=false -c core.quotepath=false diff --cached --binary > ""$outputPathForCmd"""
if ($LASTEXITCODE -ne 0) {
    throw "git diff --cached export failed: $OutputPath"
}

$patchContent = Get-Content -Raw -Encoding UTF8 $OutputPath
if ([string]::IsNullOrWhiteSpace($patchContent)) {
    Write-Host "No changes in worktree. Empty patch exported: $OutputPath"
} else {
    Write-Host "Patch exported: $OutputPath"
}
