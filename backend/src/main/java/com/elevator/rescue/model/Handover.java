package com.elevator.rescue.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * 救援交接核对单。
 * 维保与现场保安分别独立核对：开门、人员离梯、设备停用，
 * 三方（双角色各三项）全部确认才允许关闭事件。
 * 设备复检通过前必须保持停用，恢复载客被单独拦截。
 */
@Entity
@Table(name = "handover")
public class Handover extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @JsonIgnore
    public RescueEvent event;

    // ---- 维保核对 ----
    public String maintenanceConfirmer;
    public boolean maintenanceDoorOpened;
    public boolean maintenancePersonsExited;
    public boolean maintenanceEquipmentStopped;
    public Instant maintenanceConfirmedAt;

    // ---- 现场保安核对 ----
    public String guardConfirmer;
    public boolean guardDoorOpened;
    public boolean guardPersonsExited;
    public boolean guardEquipmentStopped;
    public Instant guardConfirmedAt;

    // ---- 获救人员状况 ----
    public boolean unwellAfterRescue;
    public String unwellNote;
    public Instant unwellAt;

    // ---- 设备复检（救援结束 ≠ 设备可运行）----
    public boolean reinspectionPassed;
    public String inspector;
    public Instant reinspectedAt;

    public Instant serviceResumedAt;

    /** 交接内容是否齐全：维保、保安各自三项全部核对并签名 */
    public boolean isComplete() {
        return maintenanceDoorOpened && maintenancePersonsExited && maintenanceEquipmentStopped
                && maintenanceConfirmer != null && !maintenanceConfirmer.isBlank()
                && guardDoorOpened && guardPersonsExited && guardEquipmentStopped
                && guardConfirmer != null && !guardConfirmer.isBlank();
    }
}
