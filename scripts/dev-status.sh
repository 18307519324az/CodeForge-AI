#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$PROJECT_ROOT/.run"

print_status() {
  local file="$1"
  local port_file="$2"
  local label="$3"
  local pid_path="$RUN_DIR/$file"
  local port_path="$RUN_DIR/$port_file"
  local pid="<none>"
  local status="NOT RUNNING"
  local port="<unknown>"
  [[ -f "$pid_path" ]] && pid="$(head -n 1 "$pid_path")"
  [[ -f "$port_path" ]] && port="$(head -n 1 "$port_path")"
  if [[ "$pid" =~ ^[0-9]+$ && -d "/proc/$pid" ]]; then
    status="RUNNING"
  fi
  echo "$label: $status PID=$pid PORT=$port"
}

echo "=== Dev Process Status ==="
echo "ProjectRoot: $PROJECT_ROOT"
print_status backend.pid backend.port Backend
print_status frontend.pid frontend.port Frontend
