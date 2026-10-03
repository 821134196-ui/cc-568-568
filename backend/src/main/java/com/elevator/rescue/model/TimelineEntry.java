package com.elevator.rescue.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

/** 事件时间线条目 */
@Entity
@Table(name = "timeline_entry")
public class TimelineEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @JsonIgnore
    public RescueEvent event;

    @Enumerated(EnumType.STRING)
    public Enums.TimelineType type;

    /** 操作方，如 维保(张工) / 保安 / 值班员 / 系统 */
    public String actor;

    public String detail;

    public Instant at;
}
