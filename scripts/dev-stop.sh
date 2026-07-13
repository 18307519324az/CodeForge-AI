#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$PROJECT_ROOT/.run"

stop_pid() {
  local file="$1"
  local label="$2"
  local expected_root="$3"
  local path="$RUN_DIR/$file"
  [[ -f "$path" ]] || { echo "$label: no PID file."; return 0; }
  local pid
  pid="$(head -n 1 "$path")"
  [[ "$pid" =~ ^[0-9]+$ ]] || { rm -f "$path"; echo "$label: invalid PID file removed."; return 0; }
  if [[ ! -d "/proc/$pid" ]]; then
    rm -f "$path"
    echo "$label: already stopped."
    return 0
  fi
  local cwd
  cwd="$(readlink "/proc/$pid/cwd" 2>/dev/null || true)"
  if [[ "$cwd" != "$expected_root"* ]]; then
    echo "$label: PID $pid does not belong to this worktree; skipping." >&2
    return 1
  fi
  kill "$pid" 2>/dev/null || true
  sleep 2
  if [[ -d "/proc/$pid" ]]; then
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$path"
  echo "$label: stopped."
}

stop_pid backend.pid Backend "$PROJECT_ROOT"
stop_pid frontend.pid Frontend "$PROJECT_ROOT/frontend"
rm -f "$RUN_DIR/backend.port" "$RUN_DIR/frontend.port"
echo "Dev stop complete."
