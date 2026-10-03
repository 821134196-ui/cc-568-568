# 电梯困人报警与救援交接系统

楼宇值班室接到困人报警后，快速联系维保与现场保安、跟进到场时间；救援结束后由维保和现场保安
**分别独立核对**开门、人员离梯、设备停用，交接齐全方可关闭事件。设备在完成复检前必须保持停用，
未经复检的电梯禁止恢复载客。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Quarkus 3.40 · Hibernate ORM (Panache) · REST |
| 前端 | Svelte 4 · Vite 5 |
| 存储 | SQLite（`backend/data/rescue.db`，Hibernate 自动建表） |
| 模拟 | 五方对讲/告警平台报警、维保/保安回执均由本地模拟接口提供 |

## 一条命令启动

```bash
./start.sh
```

- 值班室页面：http://localhost:5173/
- 后端 API：http://localhost:8080/api/events
- 首次启动自动下载依赖。脚本会自动发现 `~/tools` 下的本地 JDK/Maven。

场景验证（自动起停独立数据库的后端实例）：

```bash
./scripts/verify.sh
```

## 核心业务规则

1. **重复报警合并**：同一电梯存在未关闭事件时，新报警并入同一次救援（被困人数取历次最大值、
   `alarmCount++`、时间线记录 `ALARM_MERGED`），**绝不重复派出队伍**。救援进行中的同电梯报警
   必然属于同一起困人，故按"事件未关闭"合并而非固定时间窗，避免长救援期间误判为新事件。
2. **双岗交接，缺项拦截**：维保与现场保安各自确认「开门 / 人员离梯 / 设备停用」三项并签名，
   任何缺项提交都会被 409 拒绝且不写入；双方交接齐全才能关闭事件（409 + 时间线留痕）。
3. **救援结束 ≠ 设备可运行**：维保确认后设备即转为「停用挂牌」；复检不合格继续停用，
   未复检直接恢复载客返回 409（时间线记录 `RESUME_BLOCKED`）。
4. **自动升级**：
   - 通话中断（报警时或处置中）→ L1
   - 维保超过承诺到场 SLA 未到场（调度器每 15 秒检查）→ L1
   - 获救人员身体不适 → L2
5. **事件时间线**：报警、重复合并、分派、回执、到场、通话变化、升级、交接、停用、
   阻止留痕、复检、恢复、关闭，按时间完整可追溯。

## 事件状态机

```
ALARMED ──分派──▶ DISPATCHED ──维保到场──▶ ON_SCENE ──维保确认人员离梯──▶ RESCUED ──双岗交接齐全──▶ CLOSED
                                                                          │
设备：RUNNING ─救援结束─▶ LOCKED_OUT ─复检合格─▶ INSPECTED ─恢复─▶ RESUMED
                         （未复检尝试恢复 → 409 拦截，保持 LOCKED_OUT；事件可先关闭，设备仍停用）
```

## 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/mock/alarm` | 模拟五方对讲推送困人报警（含合并） |
| GET  | `/api/mock/elevators` | 模拟电梯档案 |
| POST | `/api/mock/maintenance-callback/{id}/{ack\|arrived}` | 模拟维保回执/到场 |
| POST | `/api/mock/guard-callback/{id}/{ack\|arrived}` | 模拟保安回执/到场 |
| POST | `/api/alarms` | 值班台手工录入报警 |
| GET  | `/api/events` / `/api/events/{id}` | 事件列表/详情（含时间线） |
| POST | `/api/events/{id}/dispatch` | 分派维保+保安（承诺到场分钟数） |
| POST | `/api/events/{id}/ack` `/arrived` | 回执 / 到场 `{role: MAINTENANCE|GUARD}` |
| PUT  | `/api/events/{id}/call-status` | 通话状态变更（中断触发升级） |
| POST | `/api/events/{id}/handover/{MAINTENANCE|GUARD}` | 双岗三项核对+签名 |
| POST | `/api/events/{id}/unwell` | 上报获救人员不适（L2） |
| POST | `/api/events/{id}/reinspect` | 复检（不合格继续停用） |
| POST | `/api/events/{id}/resume` | 恢复载客（未复检 409） |
| POST | `/api/events/{id}/close` | 关闭事件（交接缺项 409） |

## 前端页面

- 顶部报警栏：模拟报警接入（电梯/位置/被困人数/通话状态）。
- 左侧事件列表：**优先显示位置、被困人数、通话状态**，升级与合并次数高亮。
- 右侧详情：分派与到场 SLA 倒计时 → 维保/保安各自三项交接核对 → 复检与恢复（未复检按钮锁定）
  → 缺项时关闭按钮禁用（后端亦强校验）→ 完整时间线。
