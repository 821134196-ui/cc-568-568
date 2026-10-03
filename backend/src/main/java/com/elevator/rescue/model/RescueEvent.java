package com.elevator.rescue.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 一次电梯困人救援事件（同一电梯短时间重复报警合并为一条） */
@Entity
@Table(name = "rescue_event")
public class RescueEvent extends BaseEntity {

    @Column(unique = true, nullable = false)
    public String eventNo;

    @Column(nullable = false)
    public String elevatorId;

    /** 电梯位置描述，如 A栋1单元·客梯 */
    public String location;

    /** 被困人数（取历次报警最大值） */
    public int trappedCount;

    @Enumerated(EnumType.STRING)
    public Enums.CallStatus callStatus = Enums.CallStatus.CONNECTED;

    @Enumerated(EnumType.STRING)
    public Enums.EventStatus status = Enums.EventStatus.ALARMED;

    @Enumerated(EnumType.STRING)
    public Enums.EquipmentStatus equipmentStatus = Enums.EquipmentStatus.RUNNING;

    @Enumerated(EnumType.STRING)
    public Enums.EscalationLevel escalationLevel = Enums.EscalationLevel.NONE;

    public Instant firstAlarmAt;
    public Instant lastAlarmAt;
    public int alarmCount = 1;

    /** 维保到场时限 */
    public Instant slaDueAt;
    public boolean maintenanceLate = false;

    public Instant rescuedAt;
    public Instant closedAt;

    @OneToOne(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    public Dispatch dispatch;

    @OneToOne(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    public Handover handover;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("at ASC")
    public List<TimelineEntry> timeline = new ArrayList<>();
}
