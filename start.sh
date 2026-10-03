#!/usr/bin/env bash
# 一条命令启动电梯困人报警与救援交接系统
#   后端 Quarkus :8080 （SQLite 存于 backend/data/rescue.db）
#   前端 Svelte  :5173
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---- 自动发现本地 JDK / Maven（无 root 环境） ----
if [ -z "${JAVA_HOME:-}" ]; then
  JDK_DIR="$(ls -d "$HOME"/tools/jdk-17* 2>/dev/null | head -1 || true)"
  [ -n "$JDK_DIR" ] && export JAVA_HOME="$JDK_DIR"
fi
MVN_BIN="$(command -v mvn || true)"
if [ -z "$MVN_BIN" ] && [ -d "$HOME/tools/apache-maven-3.9.9" ]; then
  export PATH="$HOME/tools/apache-maven-3.9.9/bin:$PATH"
fi
export PATH="$JAVA_HOME/bin:$PATH"

echo "JAVA: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
echo "MVN : $(mvn -version 2>&1 | head -1)"

mkdir -p "$ROOT/backend/data" "$ROOT/backend/logs"

# ---- 启动后端 ----
echo "▶ 启动 Quarkus 后端 (http://localhost:8080) …"
( cd "$ROOT/backend" && mvn -q quarkus:dev -Dquarkus.http.host=0.0.0.0 ) \
  > "$ROOT/backend/logs/backend.log" 2>&1 &
BACK_PID=$!

# ---- 启动前端 ----
if [ ! -d "$ROOT/frontend/node_modules" ]; then
  echo "▶ 安装前端依赖…"
  ( cd "$ROOT/frontend" && npm install --no-audit --no-fund )
fi
echo "▶ 启动 Svelte 前端 (http://localhost:5173) …"
( cd "$ROOT/frontend" && npm run dev ) > "$ROOT/frontend/dev-server.log" 2>&1 &
FRONT_PID=$!

cleanup() {
  echo ""
  echo "■ 停止服务…"
  kill "$FRONT_PID" "$BACK_PID" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

wait_http() { # url name seconds
  local url=$1 name=$2 secs=${3:-90} i
  for ((i=1; i<=secs; i++)); do
    if curl -sf -o /dev/null --max-time 2 "$url"; then
      echo "✔ $name 已就绪: $url"; return 0
    fi
    sleep 1
  done
  echo "✘ $name 启动超时，请查看日志"; return 1
}

wait_http "http://localhost:8080/api/mock/elevators" "后端"
wait_http "http://localhost:5173/" "前端"

echo ""
echo "================================================"
echo " 系统已启动："
echo "   值班室页面 : http://localhost:5173/"
echo "   后端 API   : http://localhost:8080/api/events"
echo "   按 Ctrl+C 停止全部服务"
echo "================================================"

wait
