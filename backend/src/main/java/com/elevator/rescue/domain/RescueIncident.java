package com.elevator.rescue.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 一次困人救援事件。同一电梯在事件未关闭前的重复报警合并到本事件
 * （见 alarmCount 与 AlarmReport.mergedIntoId）。
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_incident_elevator", columnList = "elevator_id"),
        @Index(name = "idx_incident_status", columnList = "status")
})
public class RescueIncident extends BaseEntity {

    @ManyToOne(optional = false)
    public Elevator elevator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IncidentStatus status = IncidentStatus.ALARMED;

    /** 最近一次报警报告的被困人数（重复报警时取最新值） */
    @Column(nullable = false)
    public int trappedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CallStatus callStatus = CallStatus.CONNECTED;

    @Column(nullable = false)
    public Instant openedAt = Instant.now();

    public Instant closedAt;

    /** 累计报警次数（含首次），>1 说明发生过重复报警合并 */
    @Column(nullable = false)
    public int alarmCount = 1;

    /** 升级等级：0 未升级 / 1 值班主管 / 2 项目经理+急救 */
    @Column(nullable = false)
    public int escalationLevel = 0;

    /** 已触发的升级原因，逗号分隔 */
    @Column(length = 500)
    public String escalationReasons = "";

    /** 获救人员是否身体不适 */
    @Column(nullable = false)
    public boolean passengersUnwell = false;

    /** 不适人员是否已安排医疗救助 */
    @Column(nullable = false)
    public boolean medicalAssistanceArranged = false;

    /** 维保复检是否合格（事件关闭后仍需复检才能恢复载客） */
    @Column(nullable = false)
    public boolean recheckPassed = false;

    public Instant recheckedAt;

    @Column(length = 100)
    public String recheckInspector;

    @Column(length = 500)
    public String recheckNote;

    public void addEscalationReason(String reason) {
        if (escalationReasons == null || escalationReasons.isBlank()) {
            escalationReasons = reason;
        } else if (!escalationReasons.contains(reason)) {
            escalationReasons = escalationReasons + "、" + reason;
        }
    }

    public static RescueIncident findActiveByElevator(Long elevatorId) {
        return find("elevator.id = ?1 and status <> ?2 order by id desc",
                elevatorId, IncidentStatus.CLOSED).firstResult();
    }
}
