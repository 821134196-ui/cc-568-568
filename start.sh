#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# 电梯困人报警与救援交接系统 —— 一条命令启动
# 构建前端(Svelte) → 打包后端(Quarkus) → 单端口 8080 启动
# 访问 http://localhost:8080
# ─────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PORT="${PORT:-8080}"

# 用户空间工具链（若系统未装 Java/Maven）
[ -f "$HOME/tools/env.sh" ] && . "$HOME/tools/env.sh"

echo "▶ [1/4] 构建前端（Svelte + Vite）…"
( cd "$ROOT/frontend" && npm install --no-audit --no-fund && npm run build )

echo "▶ [2/4] 将前端产物装入后端静态资源（单端口提供）…"
RES="$ROOT/backend/src/main/resources/META-INF/resources"
rm -rf "$RES"
mkdir -p "$RES"
cp -r "$ROOT/frontend/dist/." "$RES/"

echo "▶ [3/4] 打包后端（Quarkus 可执行 jar）…"
( cd "$ROOT/backend" && mvn -q package -DskipTests )

# 停止占用端口的旧实例（本环境 ss 可能无权限显示 PID，用 /proc 反查 socket inode）
find_listener_pid() {
  local port_hex; port_hex=$(printf '%04X' "$1")
  local inode
  inode=$(awk -v p=":$port_hex" 'NR>1 && $2 ~ p "$" && $4=="0A" {print $10; exit}' /proc/net/tcp /proc/net/tcp6 2>/dev/null | head -1)
  [ -z "$inode" ] && return 0
  for d in /proc/[0-9]*/fd; do
    pid=${d%/fd}; pid=${pid#/proc/}
    if ls -l "$d" 2>/dev/null | grep -q "socket:\[$inode\]"; then
      echo "$pid"; return 0
    fi
  done
}

OLDPID="$(find_listener_pid "$PORT" || true)"
if [ -n "${OLDPID:-}" ]; then
  echo "▶ 停止旧实例 (pid=$OLDPID)"
  kill -9 "$OLDPID" 2>/dev/null || true
  sleep 1
fi

echo "▶ [4/4] 启动：http://localhost:$PORT  （Ctrl+C 停止）"
cd "$ROOT/backend"
exec java "-Dquarkus.http.port=$PORT" -jar target/quarkus-app/quarkus-run.jar
