package com.elevator.rescue.service;

import com.elevator.rescue.domain.AlarmReport;
import com.elevator.rescue.domain.CallStatus;
import com.elevator.rescue.domain.Dispatch;
import com.elevator.rescue.domain.Elevator;
import com.elevator.rescue.domain.ElevatorStatus;
import com.elevator.rescue.domain.HandoverCheck;
import com.elevator.rescue.domain.IncidentStatus;
import com.elevator.rescue.domain.RescueIncident;
import com.elevator.rescue.domain.TimelineEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 救援事件核心业务规则：
 * 1. 同一电梯存在未关闭事件时，新报警只计数合并，不重复派队；
 * 2. 通话中断、维保超时未到/迟到、获救后不适自动升级；
 * 3. 交接四项核对（维保2项+保安2项）齐全、不适人员医疗落实后才能关闭；
 * 4. 关闭不等于恢复运行——复检合格前电梯保持停用，禁止恢复载客。
 */
@ApplicationScoped
public class IncidentService {

    @Inject
    EntityManager em;

    public record AlarmResult(RescueIncident incident, boolean merged, int alarmCount) {}

    /** 模拟报警接口入口：接警（含重复报警合并） */
    @Transactional
    public AlarmResult receiveAlarm(String elevatorCode, int trappedCount, String note) {
        Elevator elevator = Elevator.<Elevator>find("code", elevatorCode).firstResultOptional()
                .orElseThrow(() -> ApiException.notFound("电梯不存在: " + elevatorCode));
        if (trappedCount < 0) {
            throw ApiException.badRequest("被困人数不能为负");
        }

        RescueIncident active = RescueIncident.findActiveByElevator(elevator.id);
        if (active != null) {
            // —— 重复报警合并：关联到同一次救援，绝不重复派出队伍 ——
            active.alarmCount += 1;
            active.trappedCount = trappedCount;
            if (active.callStatus == CallStatus.INTERRUPTED) {
                active.callStatus = CallStatus.RESTORED;
            }
            AlarmReport report = new AlarmReport();
            report.elevator = elevator;
            report.incident = active;
            report.mergedInto = active;
            report.trappedCount = trappedCount;
            report.callerNote = note;
            report.persist();
            timeline(active, TimelineEvent.Phase.ALARM,
                    String.format("第 %d 次报警（被困 %d 人），与当前救援合并，不重复派队",
                            active.alarmCount, trappedCount), "报警系统");
            return new AlarmResult(active, true, active.alarmCount);
        }

        RescueIncident incident = new RescueIncident();
        incident.elevator = elevator;
        incident.trappedCount = trappedCount;
        incident.status = IncidentStatus.ALARMED;
        incident.callStatus = CallStatus.CONNECTED;
        incident.persist();

        AlarmReport report = new AlarmReport();
        report.elevator = elevator;
        report.incident = incident;
        report.trappedCount = trappedCount;
        report.callerNote = note;
        report.persist();

        // 接警即停用，恢复载客必须走复检流程
        elevator.status = ElevatorStatus.OUT_OF_SERVICE;

        for (HandoverCheck.Key key : HandoverCheck.Key.values()) {
            HandoverCheck check = new HandoverCheck();
            check.incident = incident;
            check.checkKey = key;
            check.persist();
        }

        timeline(incident, TimelineEvent.Phase.ALARM,
                String.format("接到困人报警：%s，被困 %d 人，五方通话已接通", elevator.location, trappedCount),
                "报警系统");
        return new AlarmResult(incident, false, 1);
    }

    /** 值班员分派维保与保安 */
    @Transactional
    public RescueIncident dispatch(Long incidentId, String maintenanceAssignee, String securityAssignee,
                                   Integer maintenanceEtaMinutes, Integer securityEtaMinutes) {
        RescueIncident incident = mustGet(incidentId);
        if (incident.status != IncidentStatus.ALARMED) {
            throw ApiException.conflict("事件已分派，不能重复派单（当前状态: " + incident.status.label + "）");
        }
        if (isBlank(maintenanceAssignee) || isBlank(securityAssignee)) {
            throw ApiException.badRequest("维保与现场保安的接单人都必须填写");
        }
        int maintEta = maintenanceEtaMinutes != null && maintenanceEtaMinutes >= 0
                ? maintenanceEtaMinutes : 30;
        int secEta = securityEtaMinutes != null && securityEtaMinutes >= 0 ? securityEtaMinutes : 15;

        createDispatch(incident, Dispatch.Team.MAINTENANCE, maintenanceAssignee, maintEta);
        createDispatch(incident, Dispatch.Team.SECURITY, securityAssignee, secEta);
        incident.status = IncidentStatus.DISPATCHED;
        timeline(incident, TimelineEvent.Phase.RESPONSE,
                String.format("已派维保 %s（要求 %d 分钟内到场）", maintenanceAssignee, maintEta), "值班员");
        timeline(incident, TimelineEvent.Phase.RESPONSE,
                String.format("已通知现场保安 %s（要求 %d 分钟内到场）", securityAssignee, secEta), "值班员");
        return incident;
    }

    private void createDispatch(RescueIncident incident, Dispatch.Team team, String assignee, int eta) {
        Dispatch d = new Dispatch();
        d.incident = incident;
        d.team = team;
        d.assignee = assignee;
        d.etaLimitMinutes = eta;
        d.persist();
    }

    /** 模拟维保/保安回执接单 */
    @Transactional
    public Dispatch acknowledge(Long incidentId, Dispatch.Team team) {
        RescueIncident incident = mustGet(incidentId);
        Dispatch d = mustFindDispatch(incident, team);
        if (d.state != Dispatch.State.ASSIGNED) {
            throw ApiException.conflict(team.label + "已回执，请勿重复操作");
        }
        d.state = Dispatch.State.ACKNOWLEDGED;
        d.acknowledgedAt = Instant.now();
        timeline(incident, TimelineEvent.Phase.RESPONSE,
                team.label + " " + d.assignee + " 已接单回执", team.label);
        return d;
    }

    /** 模拟到场签到；delayMinutes 为模拟的到场耗时（用于迟到场景验证），null 取当前时间 */
    @Transactional
    public Dispatch arrive(Long incidentId, Dispatch.Team team, Integer delayMinutes) {
        RescueIncident incident = mustGet(incidentId);
        Dispatch d = mustFindDispatch(incident, team);
        if (d.state == Dispatch.State.ARRIVED) {
            throw ApiException.conflict(team.label + "已到场，请勿重复操作");
        }
        d.state = Dispatch.State.ARRIVED;
        d.arrivedAt = delayMinutes != null
                ? d.dispatchedAt.plusSeconds(delayMinutes * 60L)
                : Instant.now();
        long minutes = Duration.between(d.dispatchedAt, d.arrivedAt).toMinutes();
        timeline(incident, TimelineEvent.Phase.RESPONSE,
                String.format("%s %s 已到场（用时 %d 分钟）", team.label, d.assignee, minutes), team.label);

        if (team == Dispatch.Team.MAINTENANCE && minutes > d.etaLimitMinutes) {
            escalate(incident,
                    String.format("维保迟到：用时 %d 分钟到场，超出 %d 分钟时限", minutes, d.etaLimitMinutes),
                    1, "系统");
        }

        long arrivedCount = Dispatch.count("incident = ?1 and state = ?2", incident, Dispatch.State.ARRIVED);
        if (arrivedCount == 2 && incident.status == IncidentStatus.DISPATCHED) {
            incident.status = IncidentStatus.ON_SCENE;
            timeline(incident, TimelineEvent.Phase.RESCUE, "维保与保安均已到场，开始现场救援", "系统");
        }
        return d;
    }

    /** 五方通话中断 -> 立即升级值班主管 */
    @Transactional
    public RescueIncident markCallInterrupted(Long incidentId) {
        RescueIncident incident = mustGet(incidentId);
        if (incident.status == IncidentStatus.CLOSED) {
            throw ApiException.conflict("事件已关闭");
        }
        if (incident.callStatus == CallStatus.INTERRUPTED) {
            throw ApiException.conflict("通话已处于中断状态");
        }
        incident.callStatus = CallStatus.INTERRUPTED;
        timeline(incident, TimelineEvent.Phase.ESCALATION, "五方通话中断，尝试回拨被困人员", "报警系统");
        escalate(incident, "五方通话中断，无法联系被困人员", 1, "系统");
        return incident;
    }

    @Transactional
    public RescueIncident restoreCall(Long incidentId) {
        RescueIncident incident = mustGet(incidentId);
        if (incident.callStatus != CallStatus.INTERRUPTED) {
            throw ApiException.conflict("通话未中断，无需恢复");
        }
        incident.callStatus = CallStatus.RESTORED;
        timeline(incident, TimelineEvent.Phase.RESPONSE, "五方通话恢复，已重新联系被困人员", "值班员");
        return incident;
    }

    /** 救援完成、人员离梯；获救人员不适 -> 升级并呼叫急救 */
    @Transactional
    public RescueIncident completeRescue(Long incidentId, boolean passengersUnwell) {
        RescueIncident incident = mustGet(incidentId);
        if (incident.status != IncidentStatus.ON_SCENE) {
            throw ApiException.conflict("双方到场后方可完成救援（当前状态: " + incident.status.label + "）");
        }
        incident.status = IncidentStatus.RESCUED;
        incident.passengersUnwell = passengersUnwell;
        timeline(incident, TimelineEvent.Phase.RESCUE,
                String.format("救援完成，被困 %d 人已离开轿厢", incident.trappedCount), "维保");
        if (passengersUnwell) {
            escalate(incident, "获救人员出现身体不适，须立即安排医疗救助", 2, "系统");
        }
        return incident;
    }

    @Transactional
    public RescueIncident arrangeMedical(Long incidentId) {
        RescueIncident incident = mustGet(incidentId);
        if (!incident.passengersUnwell) {
            throw ApiException.conflict("无身体不适人员记录，无需安排医疗救助");
        }
        if (incident.medicalAssistanceArranged) {
            throw ApiException.conflict("医疗救助已安排，请勿重复登记");
        }
        incident.medicalAssistanceArranged = true;
        timeline(incident, TimelineEvent.Phase.HANDOVER, "已为不适人员安排医疗救助（120/现场医护）", "值班员");
        return incident;
    }

    /** 维保/保安分别核对交接项，返回所属事件 */
    @Transactional
    public RescueIncident setCheck(Long incidentId, HandoverCheck.Key key, boolean checked,
                                   String checkedBy, String remark) {
        RescueIncident incident = mustGet(incidentId);
        if (incident.status != IncidentStatus.RESCUED && incident.status != IncidentStatus.CLOSED) {
            throw ApiException.conflict("人员获救后才能进行交接核对（当前状态: " + incident.status.label + "）");
        }
        HandoverCheck check = HandoverCheck.<HandoverCheck>find("incident = ?1 and checkKey = ?2", incident, key)
                .firstResult();
        if (check == null) {
            throw ApiException.notFound("核对项不存在");
        }
        if (checked && isBlank(checkedBy)) {
            throw ApiException.badRequest("勾选核对项必须填写核对人");
        }
        check.checked = checked;
        check.checkedAt = checked ? Instant.now() : null;
        check.checkedBy = checked ? checkedBy.trim() : null;
        check.remark = remark;
        timeline(incident, TimelineEvent.Phase.HANDOVER,
                (checked ? "已核对：" : "已撤销核对：") + key.label + "（" + key.party + "）",
                checked ? checkedBy : "值班员");
        return incident;
    }

    /** 关闭事件：交接缺项一律拦截 */
    @Transactional
    public RescueIncident close(Long incidentId) {
        RescueIncident incident = mustGet(incidentId);
        if (incident.status == IncidentStatus.CLOSED) {
            throw ApiException.conflict("事件已关闭");
        }
        if (incident.status != IncidentStatus.RESCUED) {
            throw ApiException.conflict("救援未完成，不能关闭事件（当前状态: " + incident.status.label + "）");
        }
        List<String> missing = missingHandoverItems(incident);
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("交接内容不齐全，禁止关闭事件", missing);
        }
        incident.status = IncidentStatus.CLOSED;
        incident.closedAt = Instant.now();
        timeline(incident, TimelineEvent.Phase.HANDOVER,
                "交接核对齐全，事件关闭。电梯保持停用，待维保复检合格后方可恢复载客", "值班员");
        return incident;
    }

    /** 复检（事件关闭后由维保执行；不合格则继续停用） */
    @Transactional
    public RescueIncident recheck(Long incidentId, String inspector, boolean passed, String note) {
        RescueIncident incident = mustGet(incidentId);
        if (incident.status != IncidentStatus.CLOSED) {
            throw ApiException.conflict("事件关闭后才能进行复检");
        }
        if (isBlank(inspector)) {
            throw ApiException.badRequest("复检必须填写复检人");
        }
        incident.recheckPassed = passed;
        incident.recheckedAt = Instant.now();
        incident.recheckInspector = inspector.trim();
        incident.recheckNote = note;
        if (passed) {
            timeline(incident, TimelineEvent.Phase.RECHECK,
                    "维保复检合格（复检人：" + inspector + "），可申请恢复载客", inspector);
        } else {
            timeline(incident, TimelineEvent.Phase.RECHECK,
                    "维保复检不合格（复检人：" + inspector + "），电梯继续停用检修：" +
                            Optional.ofNullable(note).orElse(""), inspector);
        }
        return incident;
    }

    /** 恢复载客：未复检合格一律阻止 */
    @Transactional
    public RescueIncident resumeService(Long incidentId) {
        RescueIncident incident = mustGet(incidentId);
        if (!incident.recheckPassed) {
            throw ApiException.forbidden("电梯尚未通过维保复检，禁止恢复载客，必须保持停用");
        }
        if (incident.elevator.status == ElevatorStatus.IN_SERVICE) {
            throw ApiException.conflict("电梯已在载客运行");
        }
        incident.elevator.status = ElevatorStatus.IN_SERVICE;
        timeline(incident, TimelineEvent.Phase.RECHECK, "复检合格，电梯恢复载客运行", "值班员");
        return incident;
    }

    /** 定时巡检：维保超出时限仍未到场 -> 升级（每项只触发一次） */
    @Transactional
    public List<RescueIncident> detectLateMaintenance() {
        List<RescueIncident> escalated = new ArrayList<>();
        List<Dispatch> pending = em.createQuery(
                        "select d from Dispatch d where d.team = com.elevator.rescue.domain.Dispatch.Team.MAINTENANCE" +
                                " and d.state <> com.elevator.rescue.domain.Dispatch.State.ARRIVED", Dispatch.class)
                .getResultList();
        Instant now = Instant.now();
        for (Dispatch d : pending) {
            RescueIncident incident = d.incident;
            if (incident.status == IncidentStatus.CLOSED) {
                continue;
            }
            if (now.isAfter(d.dispatchedAt.plusSeconds(d.etaLimitMinutes * 60L))) {
                long overdue = Duration.between(d.dispatchedAt.plusSeconds(d.etaLimitMinutes * 60L), now).toMinutes();
                escalate(incident,
                        String.format("维保超时未到场：已超 %d 分钟时限（逾期 %d 分钟）", d.etaLimitMinutes, Math.max(overdue, 0)),
                        1, "系统");
                escalated.add(incident);
            }
        }
        return escalated;
    }

    public List<String> missingHandoverItems(RescueIncident incident) {
        List<String> missing = new ArrayList<>();
        for (HandoverCheck check : HandoverCheck.<HandoverCheck>find("incident", incident).list()) {
            if (!check.checked) {
                missing.add(check.checkKey.party + "：" + check.checkKey.label);
            }
        }
        if (incident.passengersUnwell && !incident.medicalAssistanceArranged) {
            missing.add("不适人员医疗救助未安排");
        }
        return missing;
    }

    private void escalate(RescueIncident incident, String reason, int level, String actor) {
        String key = reason.contains("通话") ? "五方通话中断"
                : reason.contains("迟到") ? "维保迟到"
                : reason.contains("超时未到场") ? "维保超时未到场"
                : reason.contains("不适") ? "获救人员身体不适"
                : reason;
        if (incident.escalationReasons != null && incident.escalationReasons.contains(key)) {
            return; // 同一升级原因只记录一次
        }
        incident.addEscalationReason(reason);
        incident.escalationLevel = Math.max(incident.escalationLevel, level);
        String target = level >= 2 ? "升级至项目经理并呼叫 120 急救" : "升级至值班主管督办";
        timeline(incident, TimelineEvent.Phase.ESCALATION, "【升级】" + reason + "，" + target, actor);
    }

    private void timeline(RescueIncident incident, TimelineEvent.Phase phase, String message, String actor) {
        TimelineEvent e = new TimelineEvent();
        e.incident = incident;
        e.phase = phase;
        e.message = message;
        e.actor = actor;
        e.persist();
    }

    private Dispatch mustFindDispatch(RescueIncident incident, Dispatch.Team team) {
        Dispatch d = Dispatch.find("incident = ?1 and team = ?2", incident, team).firstResult();
        if (d == null) {
            throw ApiException.conflict("尚未派出" + team.label);
        }
        return d;
    }

    private RescueIncident mustGet(Long id) {
        RescueIncident incident = RescueIncident.findById(id);
        if (incident == null) {
            throw ApiException.notFound("救援事件不存在: " + id);
        }
        return incident;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
