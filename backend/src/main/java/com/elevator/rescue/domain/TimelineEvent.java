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

/** 事件时间线条目：报警、响应、处置全过程 */
@Entity
@Table(indexes = @Index(name = "idx_timeline_incident", columnList = "incident_id"))
public class TimelineEvent extends BaseEntity {

    public enum Phase {
        ALARM("报警"),
        RESPONSE("响应"),
        RESCUE("处置"),
        ESCALATION("升级"),
        HANDOVER("交接"),
        RECHECK("复检");

        public final String label;

        Phase(String label) {
            this.label = label;
        }
    }

    @ManyToOne(optional = false)
    public RescueIncident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Phase phase;

    @Column(nullable = false, length = 300)
    public String message;

    @Column(nullable = false)
    public Instant occurredAt = Instant.now();

    /** 记录人/来源，如 值班员、维保、保安、系统 */
    @Column(length = 50)
    public String actor;
}
