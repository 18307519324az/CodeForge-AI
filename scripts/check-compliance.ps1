$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$excludes = @(
    ".git",
    ".m2-temp",
    ".tools",
    "logs",
    "node_modules",
    "dist",
    "test-results",
    "playwright-report",
    "target",
    "build"
)

$patterns = @(
    @{
        Title = "Reference keyword check"
        Regex = "yupi|liyupi|yu-ai-code-mother|codefather|\u7f16\u7a0b\u5bfc\u822a|\u7a0b\u5e8f\u5458\u9c7c\u76ae|\u4e8c\u7ef4\u7801|\u89c6\u9891\u6559\u7a0b|\u6587\u5b57\u6559\u7a0b|\u7b80\u5386\u5199\u6cd5|\u9762\u8bd5\u9898\u89e3"
        AllowPathRegex = "^src/main/java/com/codeforge/ai/application/service/BrandAssetReferenceRewriter\.java$|^src/test/|^frontend/tests/"
    },
    @{
        Title = "Legacy package and directory check"
        Regex = "com\.yupi|com\.liyupi|yu_ai_code_mother|yu-ai-code-mother|code-mother-frontend|code-mother-microservice"
    },
    @{
        Title = "Local path check"
        Regex = "(^|[^A-Za-z])([A-Za-z]:\\\\|[A-Za-z]:/)|C:\\\\Users\\\\|C:/Users/"
        AllowPathRegex = "^src/test/|^scripts/db/check-local-schema\.Tests\.ps1$"
    },
    @{
        Title = "Sensitive token check"
        Regex = "ghp_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|(^|[^A-Za-z0-9])sk-[A-Za-z0-9_-]{10,}|AKIA[0-9A-Z]{16}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"
        AllowPathRegex = "^src/test/|^\.env\.example$"
    }
)

$hasIssue = $false
$files = git ls-files | Where-Object {
    $path = $_ -replace "\\", "/"
    if ($path -eq "scripts/check-compliance.sh" -or $path -eq "scripts/check-compliance.ps1") {
        return $false
    }
    foreach ($exclude in $excludes) {
        if ($path -like "$exclude/*" -or $path -like "*/$exclude/*") {
            return $false
        }
    }
    return $true
}

foreach ($item in $patterns) {
    Write-Host "==== $($item.Title) ===="
    $matches = $files | ForEach-Object {
        $path = $_
        Select-String -Path $path -Pattern $item.Regex | ForEach-Object {
            $relativePath = $path.Replace("\", "/")
            $isAllowed = $item.ContainsKey("AllowPathRegex") -and $relativePath -match $item.AllowPathRegex
            if (-not $isAllowed) {
                $_
            }
        }
    }
    if ($matches) {
        $hasIssue = $true
        $matches | ForEach-Object {
            "{0}:{1}:{2}" -f $_.Path, $_.LineNumber, $_.Line.Trim()
        }
    }
    else {
        Write-Host "No match"
    }
    Write-Host ""
}

if ($hasIssue) {
    Write-Host "Compliance scan failed"
    exit 1
}

Write-Host "Compliance scan passed"
exit 0
