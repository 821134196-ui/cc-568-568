# 电梯困人报警与救援交接系统

楼宇值班室接到困人报警后，快速联系维保与现场保安、跟进到场时间；救援完成后由维保与保安
**分别核对**开门、人员离梯与设备停用情况，交接齐全才能关闭事件；事件关闭后电梯仍保持停用，
**维保复检合格前禁止恢复载客**。

- **后端**：Java 17 · Quarkus 3 · Hibernate ORM / Panache · SQLite（WAL）
- **前端**：Svelte 4 · Vite，构建后由 Quarkus 单端口托管
- **模拟接口**：本地提供困人报警推送、维保/保安回执与到场（含模拟迟到）

## 一条命令启动

```bash
./start.sh
# 打开 http://localhost:8080
```

脚本自动完成：前端 npm 构建 → 产物装入后端静态资源 → Maven 打包 → 启动。
数据保存在 `backend/rescue.db`（SQLite），删除该文件可重置全部数据。
可用 `PORT=9090 ./start.sh` 更换端口。

> 运行需要 JDK 17+、Maven 3.8+、Node 18+。若系统未安装，本仓库约定可使用
> `~/tools/env.sh` 指向用户空间的 JDK/Maven（`start.sh` 会自动加载）。

## 操作流程

1. **接警**：顶部「模拟接口」选择电梯、填写被困人数推送报警（报警页优先展示位置、人数、通话状态）；
   接警即自动停用电梯。
2. **分派**：事件详情中填写维保/保安接单人及到场时限并派单；可用「模拟回执 / 模拟到场」跟进。
3. **救援**：双方到场后进入现场救援；完成时登记人员是否不适。
4. **交接**：维保核对「开门到位 / 断电停用挂牌」，保安核对「全部离梯 / 清场围挡」，
   逐项签名；缺项时关闭按钮禁用且后端强制 400 拦截。
5. **复检放行**：事件关闭后由维保复检；合格前「恢复载客」一律 403 阻止，合格后方可恢复运行。

## 核心规则

| 规则 | 实现 |
| --- | --- |
| 重复报警合并 | 同一电梯存在未关闭事件时，新报警仅累加 `alarmCount` 并标记 `mergedInto`，**不重复派队**（`IncidentService.receiveAlarm`） |
| 交接缺项拦截 | 四项核对（维保 2 + 保安 2）齐全、不适人员已安排医疗才允许关闭（`IncidentService.close`，返回缺失明细） |
| 未复检禁止恢复 | 事件关闭后电梯保持 `OUT_OF_SERVICE`；`resumeService` 校验 `recheckPassed`，未过复检返回 403 |
| 通话中断升级 | 标记中断立即升级 L1（值班主管），同一原因不重复升级 |
| 维保迟到/超时升级 | 到场晚于时限触发升级；定时任务每 20 秒巡检超 ETA 未到场事件 |
| 获救人员不适 | 救援完成时登记不适 → 升级 L2（项目经理 + 120），医疗救助落实前计入交接缺项 |

## 自动化验证

```bash
./verify.sh
```

在独立端口（8090）与独立数据库上起临时实例，端到端断言 **42** 项检查：
重复报警合并（含派单后再报警不重复派队）、交接缺项逐项拦截、未复检/复检不合格恢复被阻止，
以及通话中断、维保超时、迟到、获救不适三类升级。

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/sim/alarms` | 模拟困人报警（重复报警自动合并） |
| POST | `/api/sim/ack` `/api/sim/arrive` | 模拟维保/保安回执、到场（arrive 支持 `delayMinutes` 模拟迟到） |
| POST | `/api/sim/escalation-check` | 立即执行一次迟到巡检 |
| GET | `/api/incidents?includeClosed=` | 事件列表 |
| GET | `/api/incidents/{id}` | 事件详情（时间线、派单、核对项、缺项明细） |
| POST | `/api/incidents/{id}/dispatch` | 分派维保与保安 |
| POST | `/api/incidents/{id}/call/interrupt|restore` | 通话中断/恢复 |
| POST | `/api/incidents/{id}/rescue-complete` | 完成救援（`passengersUnwell`） |
| POST | `/api/incidents/{id}/medical` | 安排医疗救助 |
| POST | `/api/incidents/{id}/checks` | 维保/保安交接核对（需签名） |
| POST | `/api/incidents/{id}/close` | 关闭事件（缺项 400） |
| POST | `/api/incidents/{id}/recheck` | 维保复检（合格/不合格） |
| POST | `/api/incidents/{id}/resume` | 恢复载客（未复检 403） |

## 项目结构

```
backend/   Quarkus 应用
  src/main/java/com/elevator/rescue/
    domain/    实体与枚举（Elevator/RescueIncident/AlarmReport/Dispatch/HandoverCheck/TimelineEvent）
    service/   IncidentService 核心状态机与规则、ApiException
    web/       REST 资源（值班操作 + 本地模拟接口）与 DTO
    bootstrap/ BootInit（单连接建表+种子台账）、EscalationScheduler（定时升级）
  src/main/resources/schema.sql
frontend/  Svelte + Vite
verify.sh  端到端规则验证
start.sh   一条命令构建并启动
```
