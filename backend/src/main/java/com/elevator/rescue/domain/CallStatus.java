package com.elevator.rescue.domain;

/** 五方通话状态 */
public enum CallStatus {
    CONNECTED("通话中"),
    INTERRUPTED("通话中断"),
    RESTORED("通话恢复");

    public final String label;

    CallStatus(String label) {
        this.label = label;
    }
}
