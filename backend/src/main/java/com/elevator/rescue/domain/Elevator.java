package com.elevator.rescue.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 电梯台账（演示数据，本地维护） */
@Entity
public class Elevator extends BaseEntity {

    /** 设备编号，如 EL-1001 */
    @Column(nullable = false, unique = true)
    public String code;

    /** 楼宇名称 */
    @Column(nullable = false)
    public String building;

    /** 电梯位置，如 1 号楼 2 单元东梯 */
    @Column(nullable = false)
    public String location;

    /** 维保单位 */
    @Column(nullable = false)
    public String maintenanceVendor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ElevatorStatus status = ElevatorStatus.IN_SERVICE;
}
