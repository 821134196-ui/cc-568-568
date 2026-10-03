package com.elevator.rescue.model;

/** 领域枚举集中定义 */
public final class Enums {
    private Enums() {}

    /** 事件状态 */
    public enum EventStatus {
        ALARMED,      // 报警受理
        DISPATCHED,   // 已分派
        ON_SCENE,     // 维保到场
        RESCUED,      // 人员已获救
        CLOSED        // 事件关闭（交接齐全）
    }

    /** 五方通话状态 */
    public enum CallStatus {
        CONNECTED,    // 通话中
        INTERRUPTED,  // 通话中断
        RESTORED      // 通话恢复
    }

    /** 设备运行状态 */
    public enum EquipmentStatus {
        RUNNING,      // 运行中
        LOCKED_OUT,   // 停用中（救援后必须保持）
        INSPECTED,    // 复检合格，待恢复
        RESUMED       // 已恢复载客
    }

    /** 升级级别 */
    public enum EscalationLevel {
        NONE, L1, L2
    }

    /** 交接核对方 */
    public enum PartyRole {
        MAINTENANCE,  // 维保
        GUARD         // 现场保安
    }

    /** 时间线类型 */
    public enum TimelineType {
        ALARM,           // 报警接入
        ALARM_MERGED,    // 重复报警合并
        DISPATCHED,      // 任务分派
        ACK,             // 回执（维保/保安）
        ARRIVED,         // 到场
        CALL_CHANGED,    // 通话状态变化
        UPGRADED,        // 升级
        RESCUE_UPDATE,   // 开门/离梯核对
        LOCKOUT,         // 设备停用核对
        UNWELL,          // 获救后不适
        REINSPECTED,     // 复检
        RESUME_BLOCKED,  // 阻止恢复载客
        CLOSE_BLOCKED,   // 阻止关闭
        RESUMED,         // 恢复载客
        CLOSED           // 事件关闭
    }
}
