#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${BACKEND_PORT:-8150}"
FRONTEND_PORT="${FRONTEND_PORT:-5182}"
PROFILE="${PROFILE:-local}"
ENV_FILE="${ENV_FILE:-.env.local}"
RUN_DIR="$PROJECT_ROOT/.run"

load_env_file() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  local line name value
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" || "${line:0:1}" == "#" ]] && continue
    [[ "$line" == *=* ]] || { echo "ENV_FILE_INVALID_LINE" >&2; exit 1; }
    name="${line%%=*}"
    value="${line#*=}"
    name="${name//[[:space:]]/}"
    [[ "$name" =~ ^[A-Z][A-Z0-9_]*$ ]] || { echo "ENV_FILE_INVALID_NAME:$name" >&2; exit 1; }
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ "$value" == \"*\" && "$value" == *\" ]]; then
      value="${value:1:${#value}-2}"
      value="${value//\\\"/\"}"
      value="${value//\\\\/\\}"
    elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
      value="${value:1:${#value}-2}"
      value="${value//\'\'/\'}"
    elif [[ "$value" == *'$('* || "$value" == *'`'* ]]; then
      echo "ENV_FILE_UNSUPPORTED_EXPRESSION:$name" >&2
      exit 1
    fi
    if [[ -z "${!name+x}" ]]; then
      export "$name=$value"
      echo "EnvFile: $name PRESENT"
    else
      echo "EnvFile: $name PRESENT"
    fi
  done < "$file"
}

port_open() {
  python - "$1" <<'PY'
import socket, sys
port = int(sys.argv[1])
with socket.socket() as s:
    sys.exit(0 if s.connect_ex(("127.0.0.1", port)) == 0 else 1)
PY
}

wait_for_port() {
  local port="$1"
  local deadline=$((SECONDS + 30))
  while (( SECONDS < deadline )); do
    if port_open "$port"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

mkdir -p "$RUN_DIR"
load_env_file "$PROJECT_ROOT/$ENV_FILE"

if port_open "$BACKEND_PORT"; then
  echo "Backend port $BACKEND_PORT is already occupied." >&2
  exit 1
fi
if port_open "$FRONTEND_PORT"; then
  echo "Frontend port $FRONTEND_PORT is already occupied." >&2
  exit 1
fi

(
  cd "$PROJECT_ROOT"
  SERVER_PORT="$BACKEND_PORT" nohup ./mvnw \
    -Dspring-boot.run.main-class=com.codeforge.ai.CodeForgeAiApplication \
    "-Dspring-boot.run.profiles=$PROFILE" \
    -Dspring-boot.run.arguments=--server.servlet.context-path=/api \
    spring-boot:run > "$RUN_DIR/backend.out.log" 2> "$RUN_DIR/backend.err.log" &
  echo $! > "$RUN_DIR/backend.pid"
)
echo "$BACKEND_PORT" > "$RUN_DIR/backend.port"
wait_for_port "$BACKEND_PORT" || { echo "Backend did not open port $BACKEND_PORT." >&2; exit 1; }

(
  cd "$PROJECT_ROOT/frontend"
  VITE_API_BASE_URL="http://127.0.0.1:$BACKEND_PORT/api/v1" \
  VITE_APP_BASE_URL="http://127.0.0.1:$BACKEND_PORT/api" \
  VITE_API_PROXY_TARGET="http://127.0.0.1:$BACKEND_PORT" \
  nohup npm run dev -- --host 127.0.0.1 --port "$FRONTEND_PORT" > "$RUN_DIR/frontend.out.log" 2> "$RUN_DIR/frontend.err.log" &
  echo $! > "$RUN_DIR/frontend.pid"
)
echo "$FRONTEND_PORT" > "$RUN_DIR/frontend.port"
wait_for_port "$FRONTEND_PORT" || { echo "Frontend did not open port $FRONTEND_PORT." >&2; exit 1; }

"$PROJECT_ROOT/scripts/dev-status.sh"
