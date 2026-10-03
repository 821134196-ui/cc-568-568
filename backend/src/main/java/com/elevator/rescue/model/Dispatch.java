package com.elevator.rescue.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

/** 任务分派与到场跟踪 */
@Entity
@Table(name = "dispatch")
public class Dispatch extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @JsonIgnore
    public RescueEvent event;

    public String maintenanceTeam;
    public String guardName;
    public int etaMinutes;

    public Instant dispatchedAt;
    /** 维保单位回执（模拟接口） */
    public Instant maintenanceAckAt;
    public Instant guardAckAt;
    public Instant maintenanceArrivedAt;
    public Instant guardArrivedAt;
}
