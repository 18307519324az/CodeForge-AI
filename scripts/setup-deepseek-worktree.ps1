param(
    [string]$WorktreePath = "",
    [string]$BranchName = "deepseek-worker"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$resolvedRepoRoot = (Resolve-Path ".").Path
if ([string]::IsNullOrWhiteSpace($WorktreePath)) {
    $driveRoot = [System.IO.Path]::GetPathRoot($resolvedRepoRoot)
    $targetPath = Join-Path $driveRoot "ai零代码-deepseek-worker"
} else {
    $normalizedWorktreePath = $WorktreePath.Replace("/", "\")
    if ([System.IO.Path]::IsPathRooted($normalizedWorktreePath)) {
        $targetPath = $normalizedWorktreePath
    } else {
        $targetPath = [System.IO.Path]::Combine($resolvedRepoRoot, $normalizedWorktreePath)
    }
}

$branchExists = git branch --list $BranchName
$worktreeExists = Test-Path $targetPath

if ($worktreeExists) {
    Write-Host "DeepSeek worktree 已存在: $targetPath"
    exit 0
}

if ($branchExists) {
    git worktree add $targetPath $BranchName
} else {
    git worktree add $targetPath -b $BranchName
}

Write-Host "DeepSeek worktree 已创建: $targetPath"
Write-Host "对应分支: $BranchName"
