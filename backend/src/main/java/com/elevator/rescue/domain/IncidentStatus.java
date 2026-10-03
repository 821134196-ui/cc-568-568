package com.elevator.rescue.domain;

/**
 * 事件状态机：
 * ALARMED 接警待分派 -> DISPATCHED 已派出 -> ON_SCENE 双方到场救援中
 * -> RESCUED 人员已获救 -> CLOSED 事件关闭（交接齐全）
 * 关闭后电梯仍保持停用，直到复检合格并被恢复（见 Elevator.status）。
 */
public enum IncidentStatus {
    ALARMED("接警待派"),
    DISPATCHED("已派救援"),
    ON_SCENE("现场救援"),
    RESCUED("人员已获救"),
    CLOSED("事件已关闭");

    public final String label;

    IncidentStatus(String label) {
        this.label = label;
    }
}
