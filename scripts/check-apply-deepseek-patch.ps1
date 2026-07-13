param(
    [string]$PatchPath = "",
    [switch]$Apply
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($PatchPath)) {
    $desktopPath = [Environment]::GetFolderPath("Desktop")
    $PatchPath = Join-Path $desktopPath "deepseek.patch"
}

if (-not (Test-Path $PatchPath)) {
    throw "Patch file not found: $PatchPath"
}

$patchContent = Get-Content -Raw -Encoding UTF8 $PatchPath
if ([string]::IsNullOrWhiteSpace($patchContent)) {
    Write-Host "Patch file is empty. Nothing to check or apply."
    exit 0
}

git apply --check -- $PatchPath
if ($LASTEXITCODE -ne 0) {
    throw "git apply --check failed: $PatchPath"
}

Write-Host "git apply --check passed: $PatchPath"

if ($Apply) {
    git apply -- $PatchPath
    if ($LASTEXITCODE -ne 0) {
        throw "git apply failed: $PatchPath"
    }
    Write-Host "Patch applied: $PatchPath"
} else {
    Write-Host "Check only. Patch not applied."
}
