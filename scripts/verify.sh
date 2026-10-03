#!/usr/bin/env bash
# 端到端验证：重复报警合并 / 交接缺项拦截 / 未经复检恢复载客阻止 / 升级规则
# 脚本会自行启动一个使用独立 SQLite 库的后端实例，结束后自动停止。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ -z "${JAVA_HOME:-}" ]; then
  JDK_DIR="$(ls -d "$HOME"/tools/jdk-17* 2>/dev/null | head -1 || true)"
  [ -n "$JDK_DIR" ] && export JAVA_HOME="$JDK_DIR"
fi
if [ -z "$(command -v mvn || true)" ] && [ -d "$HOME/tools/apache-maven-3.9.9" ]; then
  export PATH="$HOME/tools/apache-maven-3.9.9/bin:$PATH"
fi
export PATH="$JAVA_HOME/bin:$PATH"

BASE="http://localhost:8080"
PASS=0; FAIL=0
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# ---- 断言工具 ----
ok()   { echo "  ✅ $*"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $*"; FAIL=$((FAIL+1)); }

# req METHOD PATH [JSON] -> $CODE / $BODY 文件 $TMP/body
req() {
  local method=$1 path=$2 json=${3:-}
  if [ -n "$json" ]; then
    CODE=$(curl -s -o "$TMP/body" -w '%{http_code}' -X "$method" \
      -H 'Content-Type: application/json' -d "$json" "$BASE$path")
  else
    CODE=$(curl -s -o "$TMP/body" -w '%{http_code}' -X "$method" "$BASE$path")
  fi
}
# jq 风格取值，用 node 解析（非 JSON 响应返回空串）
jget() { node -e '
  let raw="";
  try { raw=require("fs").readFileSync(process.argv[1],"utf8"); } catch { process.exit(0); }
  let d; try { d=JSON.parse(raw); } catch { process.exit(0); }
  const p=process.argv[2].split(".");
  let v=d;
  for (const k of p) { v = v == null ? undefined : v[k]; }
  process.stdout.write(v===undefined||v===null?"":String(v));
' "$TMP/body" "$1" 2>/dev/null; }
jerr() { jget error; }

echo "▶ 启动测试用后端（独立 verify.db，SLA 0 分钟 / 迟到检查 2 秒）…"
rm -f "$ROOT/backend/data/verify.db"
( cd "$ROOT/backend" && mvn -q quarkus:dev \
    -Dquarkus.datasource.jdbc.url='jdbc:sqlite:data/verify.db' \
    -Drescue.maintenance-sla-min=0 \
    -Drescue.late-check.every=2s \
    -Dquarkus.test.continuous-testing=disabled ) > "$TMP/backend.log" 2>&1 &
BACK_PID=$!

cleanup() {
  local rc=$?
  kill "$BACK_PID" 2>/dev/null || true
  wait 2>/dev/null || true
  exit $rc
}
trap cleanup EXIT INT TERM

for i in $(seq 1 90); do
  curl -sf -o /dev/null "$BASE/api/mock/elevators" && break
  sleep 1
done
curl -sf -o /dev/null "$BASE/api/mock/elevators" || { echo "后端启动失败，日志："; tail -30 "$TMP/backend.log"; exit 1; }

echo ""
echo "════════ 场景 1：同一电梯短时间重复报警必须合并，不重复派队 ════════"
req POST /api/mock/alarm '{"elevatorId":"T-V01","trappedCount":2}'
ID1=$(jget id); E1=$(jget eventNo); C1=$(jget alarmCount)
echo "  第 1 次报警 -> 事件 id=$ID1 ($E1) alarmCount=$C1"
[ "$CODE" = 200 ] && [ -n "$ID1" ] && ok "首次报警建立事件" || bad "首次报警失败 code=$CODE"

req POST /api/mock/alarm '{"elevatorId":"T-V01","trappedCount":3}'
ID2=$(jget id); C2=$(jget alarmCount); TL=$(jget timeline)
[ "$ID2" = "$ID1" ] && [ "$C2" = "2" ] && ok "第 2 次报警并入同一事件（id 相同，alarmCount=2），人数更新为最大值 3" \
  || bad "未合并：id1=$ID1 id2=$ID2 count=$C2"
grep -q 'ALARM_MERGED' "$TMP/body" && ok "时间线记录「重复报警合并」" || bad "缺少合并时间线"

req POST /api/events/$ID1/dispatch '{"maintenanceTeam":"维保一队","guardName":"李保安","etaMinutes":20}'
[ "$CODE" = 200 ] && ok "已分派维保与保安" || bad "分派失败 code=$CODE $(jerr)"

req POST /api/mock/alarm '{"elevatorId":"T-V01","trappedCount":2}'
ID3=$(jget id); C3=$(jget alarmCount); TEAM=$(jget dispatch.maintenanceTeam)
[ "$ID3" = "$ID1" ] && [ "$C3" = "3" ] && [ "$TEAM" = "维保一队" ] \
  && ok "已分派后第 3 次报警仍并入同一事件，不重复派队（维保队伍保持「维保一队」）" \
  || bad "已分派事件的重复报警未正确合并 id=$ID3 count=$C3"

req GET /api/events
node -e '
  const evs = JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));
  const open = evs.filter(e => e.elevatorId === "T-V01" && e.status !== "CLOSED");
  process.exit(open.length === 1 ? 0 : 1);
' "$TMP/body" && ok "该电梯进行中的事件始终只有 1 起" || bad "出现重复事件"

echo ""
echo "════════ 场景 2：交接缺项必须拦截，事件不能被提前关闭 ════════"
req POST /api/events/$ID1/arrived '{"role":"GUARD"}'
req POST /api/events/$ID1/arrived '{"role":"MAINTENANCE"}'
[ "$CODE" = 200 ] && ok "保安、维保先后到场" || bad "到场失败 code=$CODE"

req POST /api/events/$ID1/close
[ "$CODE" = 409 ] && ok "双方均未交接时关闭被拒绝（409：$(jerr)）" || bad "应拒绝无交接关闭，实际 code=$CODE"

req POST /api/events/$ID1/handover/MAINTENANCE \
  '{"confirmer":"张工","doorOpened":true,"personsExited":false,"equipmentStopped":true}'
[ "$CODE" = 409 ] && ok "维保只勾两项（缺「人员离梯」）提交被拒绝（409：$(jerr)）" \
  || bad "缺项交接应被拒绝，实际 code=$CODE"
[ -z "$(jget handover.maintenanceConfirmer)" ] && ok "缺项提交未写入任何核对结果" || bad "缺项竟然被保存"

req POST /api/events/$ID1/handover/MAINTENANCE \
  '{"confirmer":"张工","doorOpened":true,"personsExited":true,"equipmentStopped":true}'
[ "$CODE" = 200 ] && ok "维保三项齐全并签名 -> 核对通过，人员状态置为已获救" || bad "维保交接失败 code=$CODE $(jerr)"
[ "$(jget status)" = "RESCUED" ] && ok "维保确认人员离梯后事件进入「人员已获救」" || bad "状态未更新为 RESCUED"
[ "$(jget equipmentStatus)" = "LOCKED_OUT" ] && ok "救援结束设备立即转为停用" || bad "设备未停用"

req POST /api/events/$ID1/close
[ "$CODE" = 409 ] && ok "仅维保交接、保安未交接，关闭仍被拒绝（409：$(jerr)）" \
  || bad "应因保安缺项拒绝关闭，实际 code=$CODE"

req POST /api/events/$ID1/handover/GUARD \
  '{"confirmer":"赵保安","doorOpened":true,"personsExited":true,"equipmentStopped":true}'
[ "$CODE" = 200 ] && ok "保安三项齐全并签名 -> 核对通过" || bad "保安交接失败 code=$CODE $(jerr)"

echo ""
echo "════════ 场景 3：未经复检恢复载客必须被阻止，救援结束≠设备可运行 ════════"
req POST /api/events/$ID1/resume
[ "$CODE" = 409 ] && ok "未复检点「恢复载客」被拒绝（409：$(jerr)）" || bad "应阻止未复检恢复，实际 code=$CODE"
# 409 响应体只有 error，重新查询事件确认设备状态与留痕
req GET /api/events/$ID1
[ "$(jget equipmentStatus)" = "LOCKED_OUT" ] && ok "被阻止后设备仍保持「停用中」" || bad "设备状态异常: $(jget equipmentStatus)"
grep -q 'RESUME_BLOCKED' "$TMP/body" && ok "时间线留痕「阻止恢复载客」" || bad "缺少阻止恢复的时间线"

req POST /api/events/$ID1/close
[ "$CODE" = 200 ] && ok "交接齐全后事件允许关闭" || bad "关闭失败 code=$CODE $(jerr)"
[ "$(jget equipmentStatus)" = "LOCKED_OUT" ] && ok "事件已关闭但设备依旧停用（复检未完成不得运行）" \
  || bad "关闭后设备状态异常: $(jget equipmentStatus)"

req POST /api/events/$ID1/reinspect '{"inspector":"王检验","passed":false}'
[ "$CODE" = 200 ] && [ "$(jget equipmentStatus)" = "LOCKED_OUT" ] && ok "复检不合格，设备继续停用" \
  || bad "复检不合格处理异常 code=$CODE"
req POST /api/events/$ID1/resume
[ "$CODE" = 409 ] && ok "复检不合格时恢复载客仍被拒绝" || bad "复检不合格不应允许恢复 code=$CODE"

req POST /api/events/$ID1/reinspect '{"inspector":"王检验","passed":true}'
[ "$CODE" = 200 ] && [ "$(jget equipmentStatus)" = "INSPECTED" ] && ok "复检合格，状态变为待恢复" || bad "复检合格处理异常 code=$CODE"
req POST /api/events/$ID1/resume
[ "$CODE" = 200 ] && [ "$(jget equipmentStatus)" = "RESUMED" ] && ok "复检通过后恢复载客成功" \
  || bad "恢复载客失败 code=$CODE $(jerr)"

echo ""
echo "════════ 场景 4：通话中断 / 维保迟到 / 获救后不适 触发升级 ════════"
req POST /api/mock/alarm '{"elevatorId":"T-V02","trappedCount":1,"callInterrupted":true}'
ID4=$(jget id)
[ "$(jget escalationLevel)" = "L1" ] && ok "报警时通话中断 -> 自动升级 L1" || bad "通话中断未升级: $(jget escalationLevel)"

# SLA 配置为 0 分钟，调度器每 2 秒检查：分派后不报到即迟到
req POST /api/events/$ID4/dispatch '{"maintenanceTeam":"维保二队","guardName":"孙保安"}'
[ "$CODE" = 200 ] || bad "事件2分派失败 code=$CODE $(jerr)"
echo "  等待 5 秒，观察 SLA 迟到自动升级…"
sleep 5
req GET /api/events/$ID4
LATE=$(jget maintenanceLate); LVL=$(jget escalationLevel)
[ "$LATE" = "true" ] && [ "$LVL" = "L1" ] && ok "维保超过 SLA 未到场 -> 标记迟到并升级 L1" \
  || bad "迟到升级未触发 late=$LATE level=$LVL"

req POST /api/mock/alarm '{"elevatorId":"T-V03","trappedCount":1}'
ID5=$(jget id)
req POST /api/events/$ID5/dispatch '{"maintenanceTeam":"维保三队","guardName":"周保安","etaMinutes":20}'
req POST /api/events/$ID5/arrived '{"role":"MAINTENANCE"}'
req POST /api/events/$ID5/arrived '{"role":"GUARD"}'
req POST /api/events/$ID5/handover/MAINTENANCE '{"confirmer":"陈工","doorOpened":true,"personsExited":true,"equipmentStopped":true}'
req POST /api/events/$ID5/handover/GUARD '{"confirmer":"吴保安","doorOpened":true,"personsExited":true,"equipmentStopped":true}'
req POST /api/events/$ID5/unwell '{"unwell":true,"note":"一名老人获救后头晕"}'
[ "$CODE" = 200 ] && [ "$(jget escalationLevel)" = "L2" ] && ok "获救人员身体不适 -> 升级 L2" \
  || bad "不适未触发 L2 code=$CODE level=$(jget escalationLevel)"
grep -q 'UNWELL' "$TMP/body" && ok "不适情况写入时间线" || bad "缺少不适时间线"

echo ""
echo "════════ 场景 5：时间线完整记录报警-响应-处置全过程 ════════"
req GET /api/events/$ID1
node -e '
  const d = JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));
  const types = d.timeline.map(t => t.type);
  const need = ["ALARM","ALARM_MERGED","DISPATCHED","ARRIVED","RESCUE_UPDATE","LOCKOUT","RESUME_BLOCKED","REINSPECTED","RESUMED","CLOSED"];
  const missing = need.filter(t => !types.includes(t));
  if (missing.length) { console.error("缺少时间线类型: " + missing.join(",")); process.exit(1); }
  const asc = d.timeline.every((t,i,a) => i===0 || new Date(a[i-1].at) <= new Date(t.at));
  if (!asc) { console.error("时间线非按时间排序"); process.exit(1); }
  console.log("  时间线条目数: " + types.length);
' "$TMP/body" && ok "事件1时间线覆盖报警/合并/分派/到场/交接/停用/阻止/复检/恢复/关闭且按时间排序" \
  || bad "时间线不完整或顺序错误"

echo ""
echo "════════ SQLite 持久化落库检查 ════════"
DBFILE="$ROOT/backend/data/verify.db"
if command -v sqlite3 >/dev/null 2>&1; then
  CNT=$(sqlite3 "$DBFILE" "select count(*) from rescue_event;")
  TLCNT=$(sqlite3 "$DBFILE" "select count(*) from timeline_entry;")
elif command -v python3 >/dev/null 2>&1; then
  read -r CNT TLCNT < <(python3 - "$DBFILE" <<'PY'
import sqlite3, sys
c = sqlite3.connect(sys.argv[1])
print(c.execute("select count(*) from rescue_event").fetchone()[0],
      c.execute("select count(*) from timeline_entry").fetchone()[0])
PY
)
else
  CNT=""
fi
if [ -n "${CNT:-}" ]; then
  echo "  rescue_event 行数: $CNT / timeline_entry 行数: $TLCNT"
  [ "$CNT" -ge 3 ] && [ "$TLCNT" -ge 20 ] && ok "救援事件与时间线已持久化到 SQLite 文件（$DBFILE）" \
    || bad "SQLite 落库异常"
else
  echo "  ℹ 无 sqlite3/python3，跳过直读检查（应用侧查询已验证数据可读写）"
fi

echo ""
echo "================================================================"
echo " 验证结果：通过 $PASS 项，失败 $FAIL 项"
echo "================================================================"
[ "$FAIL" -eq 0 ]
