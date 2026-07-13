#!/usr/bin/env bash

set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

EXIT_CODE=0

run_check() {
  local title="$1"
  local pattern="$2"
  local allow_path_pattern="${3:-}"

  echo "==== ${title} ===="
  local matches
  matches="$(git grep -n -I -E "$pattern" -- . \
    ':!scripts/check-compliance.sh' \
    ':!scripts/check-compliance.ps1' \
    ':!.m2-temp/**' \
    ':!.tools/**' \
    ':!logs/**' \
    ':!node_modules/**' \
    ':!dist/**' \
    ':!test-results/**' \
    ':!playwright-report/**' \
    ':!target/**' \
    ':!build/**' || true)"
  if [[ -n "$allow_path_pattern" && -n "$matches" ]]; then
    matches="$(printf '%s\n' "$matches" | grep -Ev "$allow_path_pattern" || true)"
  fi
  if [[ -n "$matches" ]]; then
    printf '%s\n' "$matches"
    EXIT_CODE=1
  else
    echo "No match"
  fi
  echo
}

run_check "Reference keyword check" "yupi|liyupi|yu-ai-code-mother|codefather|编程导航|程序员鱼皮|二维码|视频教程|文字教程|简历写法|面试题解" "^(src/main/java/com/codeforge/ai/application/service/BrandAssetReferenceRewriter\.java|src/test/|frontend/tests/)"
run_check "Legacy package and directory check" "com\\.yupi|com\\.liyupi|yu_ai_code_mother|yu-ai-code-mother|code-mother-frontend|code-mother-microservice"
run_check "Local path check" "(^|[^A-Za-z])([A-Za-z]:\\\\|[A-Za-z]:/)|C:\\\\Users\\\\|C:/Users/" "^(src/test/|scripts/db/check-local-schema\.Tests\.ps1:)"
run_check "Sensitive token check" "ghp_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|(^|[^A-Za-z0-9])sk-[A-Za-z0-9_-]{10,}|AKIA[0-9A-Z]{16}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----" "^(src/test/|\\.env\\.example:)"

if [[ "$EXIT_CODE" -ne 0 ]]; then
  echo "Compliance scan failed"
else
  echo "Compliance scan passed"
fi

exit "$EXIT_CODE"
