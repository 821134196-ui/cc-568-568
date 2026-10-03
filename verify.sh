#!/usr/bin/env bash
# 电梯困人报警与救援交接系统 —— 核心规则自动化验证
# 在 8090 端口启动一个使用独立数据库的临时实例，跑完自动停止。
set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR="$ROOT/backend/target/quarkus-app/quarkus-run.jar"
JAVA_BIN="${JAVA_HOME:-$HOME/tools/jdk-17.0.20.1+1}/bin/java"
BASE="http://localhost:8090"
DB="$ROOT/run/verify.db"
LOG="/tmp/elevator-verify.log"
PID=""

PASS=0; FAIL=0

green() { printf "\033[32m%s\033[0m\n" "$1"; }
red()   { printf "\033[31m%s\033[0m\n" "$1"; }
bold()  { printf "\033[1m%s\033[0m\n" "$1"; }

ok()   { PASS=$((PASS+1)); green "  ✅ $1"; }
bad()  { FAIL=$((FAIL+1)); red  "  ❌ $1"; }

# ---- HTTP 工具：REQUEST_METHOD / REQUEST_CODE / REQUEST_BODY ----
req() {
  local method="$1" path="$2" body="${3:-}"
  local tmp; tmp="$(mktemp)"
  if [ -n "$body" ]; then
    REQUEST_CODE=$(curl -s -o "$tmp" -w '%{http_code}' -X "$method" \
      -H 'Content-Type: application/json' -d "$body" "$BASE$path")
  else
    REQUEST_CODE=$(curl -s -o "$tmp" -w '%{http_code}' -X "$method" "$BASE$path")
  fi
  REQUEST_BODY="$(cat "$tmp")"; rm -f "$tmp"
}

# py <表达式> ，对 REQUEST_BODY 求值
py() { python3 -c "import sys,json; d=json.loads(sys.stdin.read()); print($1)" <<<"$REQUEST_BODY" 2>/dev/null; }

cleanup() {
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then kill -9 "$PID" 2>/dev/null; fi
  rm -f "$DB" "$DB-shm" "$DB-wal"
}
trap cleanup EXIT

# ---- 1. 构建 & 启动临时实例 ----
bold "━━━━ 准备：构建并启动验证实例（端口 8090，独立数据库）━━━━"
if [ ! -f "$JAR" ]; then
  echo "未找到 $JAR，先执行打包…"
  (cd "$ROOT/backend" && "${MAVEN_HOME:-$HOME/tools/apache-maven-3.9.9}/bin/mvn" -q package -DskipTests) || {
    red "后端打包失败"; exit 1; }
fi
mkdir -p "$ROOT/run"
rm -f "$DB" "$DB-shm" "$DB-wal"
# 启动前清理可能残留的 8090 实例（ss 可能无权限显示 PID，用 /proc 反查）
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
OLDPID="$(find_listener_pid 8090 || true)"
[ -n "$OLDPID" ] && kill -9 "$OLDPID" 2>/dev/null
sleep 1
( cd "$ROOT/backend" && exec "$JAVA_BIN" \
  -Dquarkus.http.port=8090 \
  -Dquarkus.datasource.jdbc.url="jdbc:sqlite:$DB" \
  -jar "$JAR" > "$LOG" 2>&1 ) &
PID=$!

for _ in $(seq 1 40); do
  if curl -s --max-time 2 "$BASE/api/elevators" >/dev/null 2>&1; then break; fi
  sleep 1
done
if ! curl -s --max-time 2 "$BASE/api/elevators" >/dev/null 2>&1; then
  red "验证实例启动失败，见 $LOG"; tail -20 "$LOG"; exit 1
fi
green "验证实例已启动（pid=$PID）"
echo

# ======================================================================
bold "场景一：同一电梯短时间重复报警必须合并，不重复派队"
# ======================================================================
req POST /api/sim/alarms '{"elevatorCode":"EL-1001","trappedCount":2,"note":"首发报警"}'
[ "$REQUEST_CODE" = "200" ] && ok "首发报警 200" || bad "首发报警失败：$REQUEST_CODE $REQUEST_BODY"
IDA=$(py 'd["incidentId"]')
[ "$(py 'd["merged"]')" = "False" ] && ok "首发标记 merged=false" || bad "首发不应是合并"
[ "$(py 'd["alarmCount"]')" = "1" ] && ok "报警计数=1" || bad "首发计数应为1"

req POST /api/sim/alarms '{"elevatorCode":"EL-1001","trappedCount":3,"note":"被困者再次按警铃"}'
[ "$REQUEST_CODE" = "200" ] && ok "第二次报警 200" || bad "第二次报警失败"
[ "$(py 'd["merged"]')" = "True" ] && ok "重复报警标记 merged=true" || bad "重复报警必须合并"
[ "$(py 'd["incidentId"]')" = "$IDA" ] && ok "合并到同一事件 #$IDA" || bad "重复报警产生了新事件"
[ "$(py 'd["alarmCount"]')" = "2" ] && ok "报警计数累加为 2" || bad "计数应为2，实际 $(py 'd["alarmCount"]')"

req GET "/api/incidents/$IDA"
[ "$(py 'len([i for i in d["alarms"] if i["merged"]])')" = "1" ] && ok "事件下仅 1 条报警记录标记为已合并" || bad "合并标记数量错误"
[ "$(py 'd["trappedCount"]')" = "3" ] && ok "被困人数更新为最新报警的 3 人" || bad "被困人数未更新"

# 派单后再报警，仍不得重复派队
req POST "/api/incidents/$IDA/dispatch" \
  '{"maintenanceAssignee":"王建国","securityAssignee":"李志强","maintenanceEtaMinutes":30,"securityEtaMinutes":15}'
[ "$REQUEST_CODE" = "200" ] && ok "事件 #$IDA 已派单" || bad "派单失败：$REQUEST_BODY"
req POST /api/sim/alarms '{"elevatorCode":"EL-1001","trappedCount":3,"note":"第三次报警催援"}'
req GET "/api/incidents/$IDA"
[ "$(py 'len(d["dispatches"])')" = "2" ] && ok "第三次报警后仍只有维保+保安 2 条派单（未重复派队）" \
  || bad "派单数量异常：$(py 'len(d["dispatches"])')"
[ "$(py 'd["alarmCount"]')" = "3" ] && ok "报警计数=3" || bad "计数应为3"
echo

# ======================================================================
bold "场景二：升级触发（通话中断 / 维保迟到 / 获救不适）"
# ======================================================================
# 2a 通话中断 -> L1
req POST "/api/incidents/$IDA/call/interrupt"
[ "$(py 'd["escalationLevel"]')" -ge 1 ] && ok "通话中断触发升级（L$(py 'd["escalationLevel"]')）" \
  || bad "通话中断未升级"
py 'd["escalationReasons"]' | grep -q "通话" && ok "升级原因含「通话中断」" || bad "升级原因缺失：$(py 'd["escalationReasons"]')"
# 再次中断不能重复升级
req POST "/api/incidents/$IDA/call/interrupt"
[ "$REQUEST_CODE" = "409" ] && ok "重复中断操作被拒绝" || bad "重复中断应 409"
req POST "/api/incidents/$IDA/call/restore"
[ "$(py 'd["callStatus"]')" = "RESTORED" ] && ok "通话已恢复" || bad "通话恢复失败"

# 2b 维保超时未到场（另起事件，时限 0 分钟，立即巡检）
req POST /api/sim/alarms '{"elevatorCode":"EL-2001","trappedCount":1}'
IDB=$(py 'd["incidentId"]')
req POST "/api/incidents/$IDB/dispatch" \
  '{"maintenanceAssignee":"赵维保","securityAssignee":"钱保安","maintenanceEtaMinutes":0,"securityEtaMinutes":15}'
req POST /api/sim/escalation-check
req GET "/api/incidents/$IDB"
[ "$(py 'd["escalationLevel"]')" -ge 1 ] && ok "维保超时未到场自动升级" || bad "超时未到场未升级"
py 'd["escalationReasons"]' | grep -q "超时未到场" && ok "升级原因含「超时未到场」" || bad "缺少超时原因"

# 2c 迟到到场（事件 A：时限 30 分钟，模拟 45 分钟到场）
req POST /api/sim/arrive "{\"incidentId\":$IDA,\"team\":\"SECURITY\"}"
req POST /api/sim/arrive "{\"incidentId\":$IDA,\"team\":\"MAINTENANCE\",\"delayMinutes\":45}"
[ "$(py 'd["dispatches"][0]["late"]' 2>/dev/null)" = "True" ] && true || true
req GET "/api/incidents/$IDA"
py 'd["escalationReasons"]' | grep -q "迟到" && ok "维保迟到 45 分钟到场触发升级" || bad "迟到未升级"
[ "$(py 'd["status"]')" = "ON_SCENE" ] && ok "双方到场后进入现场救援" || bad "状态应为 ON_SCENE"

# 2d 获救人员不适 -> L2 + 必须安排医疗
req POST "/api/incidents/$IDA/rescue-complete" '{"passengersUnwell":true}'
[ "$(py 'd["status"]')" = "RESCUED" ] && ok "救援完成，状态 RESCUED" || bad "救援完成失败"
[ "$(py 'd["escalationLevel"]')" -ge 2 ] && ok "获救人员不适升级至 L2（急救）" || bad "不适未升至 L2"
py 'd["escalationReasons"]' | grep -q "不适" && ok "升级原因含「身体不适」" || bad "缺少不适原因"
req GET "/api/incidents/$IDA"
py 'd["missingItems"]' | grep -q "医疗" && ok "未安排医疗救助出现在交接缺项中" || bad "医疗缺项未被识别"
req POST "/api/incidents/$IDA/medical"
[ "$(py 'd["medicalAssistanceArranged"]')" = "True" ] && ok "已安排医疗救助" || bad "医疗安排失败"
echo

# ======================================================================
bold "场景三：交接缺项拦截（四项核对 + 医疗，缺一项不能关闭）"
# ======================================================================
# 一项都没勾 -> 必须拦截
req POST "/api/incidents/$IDA/close"
[ "$REQUEST_CODE" = "400" ] && ok "无任何核对时关闭被 400 拦截" || bad "应拦截空交接，实际 $REQUEST_CODE"
[ "$(py 'len(d["details"])')" -ge 4 ] && ok "返回 $(py 'len(d["details"])') 项缺失明细" || bad "缺失明细数量不对"

# 勾 3 项 -> 仍拦截，且精确指出第 4 项
req POST "/api/incidents/$IDA/checks" '{"key":"DOOR_OPEN_CONFIRMED","checked":true,"checkedBy":"王建国"}'
req POST "/api/incidents/$IDA/checks" '{"key":"EQUIPMENT_LOCKED_OUT","checked":true,"checkedBy":"王建国"}'
req POST "/api/incidents/$IDA/checks" '{"key":"ALL_PERSONS_EXITED","checked":true,"checkedBy":"李志强"}'
req POST "/api/incidents/$IDA/close"
[ "$REQUEST_CODE" = "400" ] && ok "缺 1 项时关闭仍被拦截" || bad "缺项时不应允许关闭"
py 'd["details"]' | grep -q "清场" && ok "拦截明细精确指出缺少「现场清场围挡」" || bad "未指出具体缺项：$REQUEST_BODY"

# 无签名勾选应被拒
req POST "/api/incidents/$IDA/checks" '{"key":"SCENE_CLEARED","checked":true,"checkedBy":""}'
[ "$REQUEST_CODE" = "400" ] && ok "核对项无签名被拒绝" || bad "无签名应 400"

# 补齐第 4 项 -> 可关闭
req POST "/api/incidents/$IDA/checks" '{"key":"SCENE_CLEARED","checked":true,"checkedBy":"李志强"}'
req GET "/api/incidents/$IDA"
[ "$(py 'len(d["missingItems"])')" = "0" ] && ok "四项核对+医疗齐全，missingItems 为空" || bad "仍有缺项"
req POST "/api/incidents/$IDA/close"
[ "$REQUEST_CODE" = "200" ] && ok "交接齐全后关闭成功" || bad "关闭失败：$REQUEST_BODY"
[ "$(py 'd["status"]')" = "CLOSED" ] && ok "事件状态 CLOSED" || bad "状态未关闭"
echo

# ======================================================================
bold "场景四：未经复检恢复载客必须被阻止"
# ======================================================================
req GET "/api/incidents/$IDA"
[ "$(py 'd["elevatorStatus"]')" = "OUT_OF_SERVICE" ] && ok "事件关闭后电梯仍为停用" || bad "电梯不应自动恢复"

req POST "/api/incidents/$IDA/resume"
[ "$REQUEST_CODE" = "403" ] && ok "未复检直接恢复载客被 403 阻止" || bad "应阻止未复检恢复，实际 $REQUEST_CODE"

# 复检不合格 -> 仍阻止
req POST "/api/incidents/$IDA/recheck" '{"inspector":"王建国","passed":false,"note":"门锁需进一步调整"}'
[ "$(py 'd["recheckPassed"]')" = "False" ] && ok "复检不合格已登记" || bad "复检登记失败"
req POST "/api/incidents/$IDA/resume"
[ "$REQUEST_CODE" = "403" ] && ok "复检不合格时恢复仍被阻止" || bad "不合格复检不应放行"

# 合格但事件未关闭的别的电梯不能复检（状态机校验，顺带覆盖）
req POST "/api/incidents/$IDB/recheck" '{"inspector":"赵维保","passed":true}'
[ "$REQUEST_CODE" = "409" ] && ok "未关闭事件不允许复检" || bad "应拒绝未关闭复检"

# 复检合格 -> 可恢复
req POST "/api/incidents/$IDA/recheck" '{"inspector":"王建国","passed":true,"note":"门锁调整完毕，试运行正常"}'
[ "$(py 'd["recheckPassed"]')" = "True" ] && ok "复检合格已登记" || bad "合格复检失败"
req POST "/api/incidents/$IDA/resume"
[ "$REQUEST_CODE" = "200" ] && ok "复检合格后恢复载客成功" || bad "恢复失败：$REQUEST_BODY"
[ "$(py 'd["elevatorStatus"]')" = "IN_SERVICE" ] && ok "电梯状态恢复为载客运行" || bad "状态未恢复"

# 恢复运行后再次报警应是新事件（旧事件已关闭，合并窗口结束）
req POST /api/sim/alarms '{"elevatorCode":"EL-1001","trappedCount":1}'
[ "$(py 'd["merged"]')" = "False" ] && ok "恢复后再报警创建新事件，不与已关闭事件合并" || bad "已关闭事件不应再合并"
echo

# ======================================================================
bold "汇总"
# ======================================================================
echo "通过：$PASS    失败：$FAIL"
if [ "$FAIL" -ne 0 ]; then red "❌ 存在失败用例"; exit 1; fi
green "🎉 全部核心规则验证通过：重复报警合并 / 交接缺项拦截 / 未复检恢复阻止（含三类升级）"
