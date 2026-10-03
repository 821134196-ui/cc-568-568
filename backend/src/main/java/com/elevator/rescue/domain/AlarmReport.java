package com.elevator.rescue.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 模拟报警接口上报的每一条报警。若上报时该电梯已有未关闭事件，
 * mergedInto 指向既有事件（不重复派队）；否则 mergedInto 为 null 并新建事件。
 */
@Entity
@Table(indexes = @Index(name = "idx_alarm_incident", columnList = "incident_id"))
public class AlarmReport extends BaseEntity {

    @ManyToOne(optional = false)
    public Elevator elevator;

    /** 报警归属的事件（重复报警会被合并到同一事件） */
    @ManyToOne(optional = false)
    public RescueIncident incident;

    @Column(nullable = false)
    public int trappedCount;

    @Column(nullable = false)
    public Instant receivedAt = Instant.now();

    /** 重复报警时指向被合并到的既有事件，null 表示本报警为首发 */
    @ManyToOne
    public RescueIncident mergedInto;

    @Column(length = 200)
    public String callerNote;
}
