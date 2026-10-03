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

/** 任务分派回执（维保 / 现场保安各一条），由模拟维保接口回传到场时间 */
@Entity
@Table(indexes = @Index(name = "idx_dispatch_incident", columnList = "incident_id"))
public class Dispatch extends BaseEntity {

    public enum Team {
        MAINTENANCE("维保"),
        SECURITY("现场保安");

        public final String label;

        Team(String label) {
            this.label = label;
        }
    }

    public enum State {
        ASSIGNED("已派单"),
        ACKNOWLEDGED("已回执"),
        ARRIVED("已到场");

        public final String label;

        State(String label) {
            this.label = label;
        }
    }

    @ManyToOne(optional = false)
    public RescueIncident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public State state = State.ASSIGNED;

    /** 被派人员/班组名称 */
    @Column(nullable = false, length = 100)
    public String assignee;

    /** 要求到场时限（分钟），用于迟到升级判定 */
    @Column(nullable = false)
    public int etaLimitMinutes;

    @Column(nullable = false)
    public Instant dispatchedAt = Instant.now();

    /** 维保回执接单时间 */
    public Instant acknowledgedAt;

    /** 实际到场时间 */
    public Instant arrivedAt;

    @Column(length = 200)
    public String note;
}
