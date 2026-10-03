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
 * 救援结束后的交接核对项。维保与现场保安分别核对；
 * 任一项缺失，事件不能关闭（业务层强制，见 IncidentService#close）。
 */
@Entity
@Table(indexes = @Index(name = "idx_handover_incident", columnList = "incident_id"))
public class HandoverCheck extends BaseEntity {

    public enum Key {
        // 维保核对
        DOOR_OPEN_CONFIRMED("doorOpenConfirmed", "维保", "电梯已开门到位"),
        EQUIPMENT_LOCKED_OUT("equipmentLockedOut", "维保", "设备已断电停用挂牌"),
        // 保安核对
        ALL_PERSONS_EXITED("allPersonsExited", "保安", "被困人员全部安全离梯"),
        SCENE_CLEARED("sceneCleared", "保安", "现场已清场并设警示围挡");

        public final String code;
        public final String party;
        public final String label;

        Key(String code, String party, String label) {
            this.code = code;
            this.party = party;
            this.label = label;
        }
    }

    @ManyToOne(optional = false)
    public RescueIncident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Key checkKey;

    @Column(nullable = false)
    public boolean checked = false;

    public Instant checkedAt;

    /** 核对人签名（姓名/工号） */
    @Column(length = 100)
    public String checkedBy;

    @Column(length = 300)
    public String remark;
}
