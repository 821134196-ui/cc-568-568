package com.elevator.rescue.service;

import com.elevator.rescue.dto.Dtos.*;
import com.elevator.rescue.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class RescueService {

    @ConfigProperty(name = "rescue.maintenance-sla-min", defaultValue = "30")
    int slaMin;

    /** CDI 自注入代理，用于让 @Transactional 方法经代理调用生效（提交后再抛业务异常） */
    @Inject
    RescueService self;

    /** 事务方法的返回：正常返回事件；被规则拦截时 error 携带原因（时间线已提交留痕） */
    private record Outcome(RescueEvent event, String error) {}

    static final String SYS = "系统";

    // ------------------------------------------------------------------
    // 报警接入（含重复报警合并）
    // ------------------------------------------------------------------

    @Transactional
    public RescueEvent receiveAlarm(AlarmRequest req, String source) {
        if (req.elevatorId() == null || req.elevatorId().isBlank()) {
            throw new BusinessException("缺少电梯编号");
        }
        int count = req.trappedCount() == null ? 1 : Math.max(1, req.trappedCount());
        Enums.CallStatus call = req.callStatus() == null ? Enums.CallStatus.CONNECTED : req.callStatus();
        Instant now = Instant.now();

        // 合并规则：同一电梯存在未关闭事件 → 关联到同一次救援，绝不重复派出队伍
        RescueEvent open = findOpenEvent(req.elevatorId());
        if (open != null) {
            Duration gap = Duration.between(open.lastAlarmAt, now);
            open.alarmCount += 1;
            open.lastAlarmAt = now;
            if (count > open.trappedCount) {
                open.trappedCount = count;
            }
            // 重复报警伴随通话中断 → 升级
            if (call == Enums.CallStatus.INTERRUPTED && open.callStatus != Enums.CallStatus.INTERRUPTED) {
                open.callStatus = Enums.CallStatus.INTERRUPTED;
                addTimeline(open, Enums.TimelineType.CALL_CHANGED, source, "重复报警上报通话中断");
                escalate(open, Enums.EscalationLevel.L1, "通话中断（重复报警上报）", SYS);
            }
            addTimeline(open, Enums.TimelineType.ALARM_MERGED, source,
                    String.format("第 %d 次报警，距上次 %d 秒；该电梯救援事件尚未关闭，并入同一次救援，不重复派队",
                            open.alarmCount, gap.getSeconds()));
            return open;
        }

        RescueEvent e = new RescueEvent();
        e.eventNo = "EV-" + req.elevatorId() + "-" + now.toEpochMilli();
        e.elevatorId = req.elevatorId();
        e.location = req.location();
        e.trappedCount = count;
        e.callStatus = call;
        e.firstAlarmAt = now;
        e.lastAlarmAt = now;
        e.status = Enums.EventStatus.ALARMED;
        e.equipmentStatus = Enums.EquipmentStatus.RUNNING;
        RescueEvent.persist(e);

        addTimeline(e, Enums.TimelineType.ALARM, source,
                String.format("接到困人报警：%s，被困 %d 人，通话%s",
                        e.location == null ? e.elevatorId : e.location,
                        e.trappedCount,
                        call == Enums.CallStatus.CONNECTED ? "正常" : "中断"));
        if (call == Enums.CallStatus.INTERRUPTED) {
            escalate(e, Enums.EscalationLevel.L1, "报警时通话已中断", SYS);
        }
        return e;
    }

    private RescueEvent findOpenEvent(String elevatorId) {
        return RescueEvent.find("elevatorId = ?1 and status <> ?2 order by lastAlarmAt desc",
                elevatorId, Enums.EventStatus.CLOSED).firstResult();
    }

    // ------------------------------------------------------------------
    // 分派
    // ------------------------------------------------------------------

    @Transactional
    public RescueEvent dispatch(Long eventId, DispatchRequest req) {
        RescueEvent e = mustGet(eventId);
        if (e.dispatch != null) {
            throw new BusinessException("该事件已分派（编号 " + e.eventNo + "），重复报警已合并，无需重复派队");
        }
        if (e.status != Enums.EventStatus.ALARMED) {
            throw new BusinessException("当前状态 " + e.status + " 不允许分派");
        }
        if (req.maintenanceTeam() == null || req.maintenanceTeam().isBlank()
                || req.guardName() == null || req.guardName().isBlank()) {
            throw new BusinessException("必须同时指定维保单位和现场保安");
        }
        int eta = req.etaMinutes() == null || req.etaMinutes() <= 0 ? slaMin : req.etaMinutes();

        Dispatch d = new Dispatch();
        d.event = e;
        d.maintenanceTeam = req.maintenanceTeam();
        d.guardName = req.guardName();
        d.etaMinutes = eta;
        d.dispatchedAt = Instant.now();
        Dispatch.persist(d);
        e.dispatch = d;
        e.status = Enums.EventStatus.DISPATCHED;
        e.slaDueAt = d.dispatchedAt.plus(Duration.ofMinutes(eta));

        addTimeline(e, Enums.TimelineType.DISPATCHED, "值班员",
                String.format("已分派：维保「%s」、现场保安「%s」，承诺到场 %d 分钟（SLA 截止 %s）",
                        d.maintenanceTeam, d.guardName, eta, e.slaDueAt));
        return e;
    }

    /** 模拟维保单位回执 */
    @Transactional
    public RescueEvent maintenanceAck(Long eventId) {
        RescueEvent e = mustGet(eventId);
        Dispatch d = mustDispatch(e);
        if (d.maintenanceAckAt == null) {
            d.maintenanceAckAt = Instant.now();
            addTimeline(e, Enums.TimelineType.ACK, d.maintenanceTeam, "维保单位已接单回执");
        }
        return e;
    }

    @Transactional
    public RescueEvent roleAck(Long eventId, Enums.PartyRole role) {
        RescueEvent e = mustGet(eventId);
        Dispatch d = mustDispatch(e);
        Instant now = Instant.now();
        if (role == Enums.PartyRole.MAINTENANCE) {
            if (d.maintenanceAckAt == null) {
                d.maintenanceAckAt = now;
                addTimeline(e, Enums.TimelineType.ACK, d.maintenanceTeam, "维保已回执");
            }
        } else {
            if (d.guardAckAt == null) {
                d.guardAckAt = now;
                addTimeline(e, Enums.TimelineType.ACK, "保安·" + d.guardName, "现场保安已回执");
            }
        }
        return e;
    }

    @Transactional
    public RescueEvent arrived(Long eventId, Enums.PartyRole role) {
        RescueEvent e = mustGet(eventId);
        Dispatch d = mustDispatch(e);
        Instant now = Instant.now();

        if (role == Enums.PartyRole.MAINTENANCE) {
            if (d.maintenanceArrivedAt != null) {
                return e;
            }
            d.maintenanceArrivedAt = now;
            boolean late = e.slaDueAt != null && now.isAfter(e.slaDueAt);
            addTimeline(e, Enums.TimelineType.ARRIVED, d.maintenanceTeam,
                    "维保到场" + (late ? String.format("，迟到 %d 分钟（SLA %s）",
                            Duration.between(e.slaDueAt, now).toMinutes(), e.slaDueAt) : "，符合 SLA"));
            if (late) {
                e.maintenanceLate = true;
                escalate(e, Enums.EscalationLevel.L1, "维保未在承诺时间内到场", SYS);
            }
        } else {
            if (d.guardArrivedAt == null) {
                d.guardArrivedAt = now;
                addTimeline(e, Enums.TimelineType.ARRIVED, "保安·" + d.guardName, "现场保安到场");
            }
        }

        if (d.maintenanceArrivedAt != null && e.status == Enums.EventStatus.DISPATCHED) {
            e.status = Enums.EventStatus.ON_SCENE;
        }
        return e;
    }

    // ------------------------------------------------------------------
    // 通话
    // ------------------------------------------------------------------

    @Transactional
    public RescueEvent setCallStatus(Long eventId, CallStatusRequest req) {
        RescueEvent e = mustGet(eventId);
        if (req.callStatus() == null) {
            throw new BusinessException("缺少通话状态");
        }
        if (e.callStatus == req.callStatus()) {
            return e;
        }
        Enums.CallStatus prev = e.callStatus;
        e.callStatus = req.callStatus();
        String text = switch (req.callStatus()) {
            case CONNECTED -> "通话接通";
            case INTERRUPTED -> "通话中断";
            case RESTORED -> "通话恢复";
        };
        addTimeline(e, Enums.TimelineType.CALL_CHANGED, "值班员", text + "（原状态 " + prev + "）");
        if (req.callStatus() == Enums.CallStatus.INTERRUPTED) {
            escalate(e, Enums.EscalationLevel.L1, "五方通话中断", SYS);
        }
        return e;
    }

    // ------------------------------------------------------------------
    // 交接核对（维保 / 保安 各自三项，缺一不可）
    // ------------------------------------------------------------------

    @Transactional
    public RescueEvent confirmHandover(Long eventId, Enums.PartyRole role, HandoverRequest req) {
        RescueEvent e = mustGet(eventId);
        if (e.status != Enums.EventStatus.ON_SCENE && e.status != Enums.EventStatus.RESCUED) {
            throw new BusinessException("维保到场后才能进行救援交接核对（当前状态 " + e.status + "）");
        }
        if (req.confirmer() == null || req.confirmer().isBlank()) {
            throw new BusinessException("核对人必须签名");
        }
        // 不允许带缺项提交：三项必须逐项确认
        if (!Boolean.TRUE.equals(req.doorOpened())
                || !Boolean.TRUE.equals(req.personsExited())
                || !Boolean.TRUE.equals(req.equipmentStopped())) {
            throw new BusinessException("交接核对存在缺项：开门、人员离梯、设备停用三项均须确认，缺项不能跳过");
        }

        Handover h = e.handover;
        if (h == null) {
            h = new Handover();
            h.event = e;
            Handover.persist(h);
            e.handover = h;
        }
        Instant now = Instant.now();
        String partyLabel = role == Enums.PartyRole.MAINTENANCE ? "维保" : "现场保安";

        if (role == Enums.PartyRole.MAINTENANCE) {
            h.maintenanceConfirmer = req.confirmer();
            h.maintenanceDoorOpened = true;
            h.maintenancePersonsExited = true;
            h.maintenanceEquipmentStopped = true;
            h.maintenanceConfirmedAt = now;
        } else {
            h.guardConfirmer = req.confirmer();
            h.guardDoorOpened = true;
            h.guardPersonsExited = true;
            h.guardEquipmentStopped = true;
            h.guardConfirmedAt = now;
        }
        addTimeline(e, Enums.TimelineType.RESCUE_UPDATE, partyLabel + "·" + req.confirmer(),
                partyLabel + "核对完成：已开门、人员全部离梯、设备已停用");

        // 人员获救：维保确认人员离梯即视为获救，电梯保持停用等待复检
        if (h.maintenancePersonsExited && e.status != Enums.EventStatus.RESCUED) {
            e.status = Enums.EventStatus.RESCUED;
            e.rescuedAt = now;
        }
        if (h.maintenanceEquipmentStopped || h.guardEquipmentStopped) {
            if (e.equipmentStatus == Enums.EquipmentStatus.RUNNING) {
                e.equipmentStatus = Enums.EquipmentStatus.LOCKED_OUT;
                addTimeline(e, Enums.TimelineType.LOCKOUT, SYS,
                        "救援结束，电梯已停用挂牌；未完成复检前不得恢复载客");
            }
        }
        return e;
    }

    @Transactional
    public RescueEvent reportUnwell(Long eventId, UnwellRequest req) {
        RescueEvent e = mustGet(eventId);
        Handover h = e.handover;
        if (h == null) {
            h = new Handover();
            h.event = e;
            Handover.persist(h);
            e.handover = h;
        }
        h.unwellAfterRescue = Boolean.TRUE.equals(req.unwell());
        h.unwellNote = req.note();
        h.unwellAt = Instant.now();
        addTimeline(e, Enums.TimelineType.UNWELL, "现场",
                "获救人员身体不适：" + (req.note() == null ? "" : req.note()));
        if (h.unwellAfterRescue) {
            escalate(e, Enums.EscalationLevel.L2, "获救人员出现身体不适，需医疗关注", SYS);
        }
        return e;
    }

    // ------------------------------------------------------------------
    // 复检与恢复载客（复检是硬门槛）
    // ------------------------------------------------------------------

    @Transactional
    public RescueEvent reinspect(Long eventId, ReinspectRequest req) {
        RescueEvent e = mustGet(eventId);
        if (e.equipmentStatus != Enums.EquipmentStatus.LOCKED_OUT) {
            throw new BusinessException("仅停用中的电梯需要复检（当前设备状态 " + e.equipmentStatus + "）");
        }
        if (req.inspector() == null || req.inspector().isBlank()) {
            throw new BusinessException("复检必须记录检验人员");
        }
        if (!Boolean.TRUE.equals(req.passed())) {
            addTimeline(e, Enums.TimelineType.REINSPECTED, "检验员·" + req.inspector(),
                    "复检不合格，电梯继续停用");
            return e;
        }
        Handover h = e.handover;
        if (h == null) {
            h = new Handover();
            h.event = e;
            Handover.persist(h);
            e.handover = h;
        }
        h.reinspectionPassed = true;
        h.inspector = req.inspector();
        h.reinspectedAt = Instant.now();
        e.equipmentStatus = Enums.EquipmentStatus.INSPECTED;
        addTimeline(e, Enums.TimelineType.REINSPECTED, "检验员·" + req.inspector(),
                "复检合格，具备恢复载客条件");
        return e;
    }

    // 注意：外层方法不带 @Transactional —— 由 self 代理开启独立事务并提交，
    // 这样被规则拦截时「阻止恢复/阻止关闭」的时间线留痕不会随异常回滚。
    public RescueEvent resumeService(Long eventId) {
        Outcome o = self.resumeServiceTx(eventId);
        if (o.error() != null) {
            throw new BusinessException(o.error());
        }
        return o.event();
    }

    @Transactional
    public Outcome resumeServiceTx(Long eventId) {
        RescueEvent e = mustGet(eventId);
        Handover h = e.handover;

        // 规则 1：救援结束 ≠ 设备可重新运行
        if (e.status != Enums.EventStatus.RESCUED && e.status != Enums.EventStatus.CLOSED) {
            return new Outcome(null, "救援尚未完成，不能恢复载客");
        }
        // 规则 2：未完成复检，必须保持停用
        if (h == null || !h.reinspectionPassed) {
            addTimeline(e, Enums.TimelineType.RESUME_BLOCKED, SYS,
                    "阻止恢复载客：电梯尚未完成复检，必须保持停用");
            return new Outcome(null, "电梯尚未完成复检，禁止恢复载客，设备保持停用状态");
        }
        if (e.equipmentStatus == Enums.EquipmentStatus.RESUMED) {
            return new Outcome(null, "设备已恢复载客");
        }
        e.equipmentStatus = Enums.EquipmentStatus.RESUMED;
        h.serviceResumedAt = Instant.now();
        addTimeline(e, Enums.TimelineType.RESUMED, "检验员·" + h.inspector,
                "复检合格后恢复载客运行");
        return new Outcome(e, null);
    }

    // ------------------------------------------------------------------
    // 关闭事件（交接缺项不能跳过）
    // ------------------------------------------------------------------

    public RescueEvent close(Long eventId) {
        Outcome o = self.closeTx(eventId);
        if (o.error() != null) {
            throw new BusinessException(o.error());
        }
        return o.event();
    }

    @Transactional
    public Outcome closeTx(Long eventId) {
        RescueEvent e = mustGet(eventId);
        if (e.status == Enums.EventStatus.CLOSED) {
            return new Outcome(null, "事件已关闭");
        }
        if (e.status != Enums.EventStatus.RESCUED) {
            return new Outcome(null, "人员尚未确认获救，不能关闭事件（当前状态 " + e.status + "）");
        }
        Handover h = e.handover;
        if (h == null || !h.isComplete()) {
            String missing = describeMissing(h);
            addTimeline(e, Enums.TimelineType.CLOSE_BLOCKED, SYS,
                    "阻止关闭事件：交接缺项 —— " + missing);
            return new Outcome(null, "交接内容不齐全，不能关闭事件。缺项：" + missing);
        }
        e.status = Enums.EventStatus.CLOSED;
        e.closedAt = Instant.now();
        String lockNote = e.equipmentStatus == Enums.EquipmentStatus.LOCKED_OUT
                ? "；注意：电梯尚未复检，仍保持停用"
                : "";
        addTimeline(e, Enums.TimelineType.CLOSED, "值班员",
                "交接齐全，事件关闭" + lockNote);
        return new Outcome(e, null);
    }

    private String describeMissing(Handover h) {
        if (h == null) {
            return "维保核对、保安核对均未完成";
        }
        java.util.ArrayList<String> m = new java.util.ArrayList<>();
        if (h.maintenanceConfirmer == null || h.maintenanceConfirmer.isBlank()) m.add("维保签名");
        if (!h.maintenanceDoorOpened) m.add("维保-开门确认");
        if (!h.maintenancePersonsExited) m.add("维保-人员离梯确认");
        if (!h.maintenanceEquipmentStopped) m.add("维保-设备停用确认");
        if (h.guardConfirmer == null || h.guardConfirmer.isBlank()) m.add("保安签名");
        if (!h.guardDoorOpened) m.add("保安-开门确认");
        if (!h.guardPersonsExited) m.add("保安-人员离梯确认");
        if (!h.guardEquipmentStopped) m.add("保安-设备停用确认");
        return String.join("、", m);
    }

    // ------------------------------------------------------------------
    // 升级
    // ------------------------------------------------------------------

    @Transactional
    public void escalate(RescueEvent e, Enums.EscalationLevel target, String reason, String actor) {
        if (e.escalationLevel.ordinal() < target.ordinal()) {
            e.escalationLevel = target;
        }
        addTimeline(e, Enums.TimelineType.UPGRADED, actor,
                "升级至 " + target + "：" + reason);
    }

    /** 定时检查：已分派但超过 SLA 未到场 → 升级 */
    @Transactional
    public void checkLateMaintenance() {
        Instant now = Instant.now();
        List<RescueEvent> pending = RescueEvent.find(
                "status in (?1, ?2)", Enums.EventStatus.DISPATCHED, Enums.EventStatus.ALARMED).list();
        for (RescueEvent e : pending) {
            Dispatch d = e.dispatch;
            if (d != null && d.maintenanceArrivedAt == null
                    && e.slaDueAt != null && now.isAfter(e.slaDueAt) && !e.maintenanceLate) {
                e.maintenanceLate = true;
                addTimeline(e, Enums.TimelineType.UPGRADED, SYS,
                        "维保超过承诺到场时间（SLA " + e.slaDueAt + "）仍未到场");
                escalate(e, Enums.EscalationLevel.L1, "维保迟到，未在 SLA 内到场", SYS);
            }
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Transactional
    public List<RescueEvent> listEvents() {
        return RescueEvent.findAll().list();
    }

    @Transactional
    public RescueEvent get(Long id) {
        return mustGet(id);
    }

    private RescueEvent mustGet(Long id) {
        RescueEvent e = RescueEvent.findById(id);
        if (e == null) {
            throw new BusinessException("事件不存在: " + id);
        }
        return e;
    }

    private Dispatch mustDispatch(RescueEvent e) {
        if (e.dispatch == null) {
            throw new BusinessException("事件尚未分派");
        }
        return e.dispatch;
    }

    private void addTimeline(RescueEvent e, Enums.TimelineType type, String actor, String detail) {
        TimelineEntry t = new TimelineEntry();
        t.event = e;
        t.type = type;
        t.actor = actor;
        t.detail = detail;
        t.at = Instant.now();
        TimelineEntry.persist(t);
        e.timeline.add(t);
    }
}
