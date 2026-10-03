package com.elevator.rescue.domain;

/** 电梯运行状态：救援事件期间必须停用，复检合格后才能恢复载客 */
public enum ElevatorStatus {
    IN_SERVICE("载客运行"),
    OUT_OF_SERVICE("停用");

    public final String label;

    ElevatorStatus(String label) {
        this.label = label;
    }
}
