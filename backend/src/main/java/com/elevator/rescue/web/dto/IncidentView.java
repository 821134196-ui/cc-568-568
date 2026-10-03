package com.elevator.rescue.web.dto;

import com.elevator.rescue.domain.AlarmReport;
import com.elevator.rescue.domain.Dispatch;
import com.elevator.rescue.domain.HandoverCheck;
import com.elevator.rescue.domain.RescueIncident;
import com.elevator.rescue.domain.TimelineEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** 对外 JSON 视图（避免实体双向关联导致的序列化循环） */
public record IncidentView(
        Long id,
        String elevatorCode,
        String building,
        String location,
        String maintenanceVendor,
        String elevatorStatus,
        String elevatorStatusLabel,
        String status,
        String statusLabel,
        int trappedCount,
        String callStatus,
        String callStatusLabel,
        int alarmCount,
        int escalationLevel,
        String escalationReasons,
        boolean passengersUnwell,
        boolean medicalAssistanceArranged,
        boolean recheckPassed,
        Instant recheckedAt,
        String recheckInspector,
        String recheckNote,
        Instant openedAt,
        Instant closedAt,
        List<DispatchView> dispatches,
        List<CheckView> checks,
        List<TimelineView> timeline,
        List<AlarmView> alarms,
        List<String> missingItems,
        boolean canClose,
        boolean canResume
) {
    public record DispatchView(
            String team, String teamLabel, String state, String stateLabel,
            String assignee, int etaLimitMinutes,
            Instant dispatchedAt, Instant acknowledgedAt, Instant arrivedAt,
            Long elapsedMinutes, boolean late
    ) {}

    public record CheckView(
            String key, String code, String party, String label,
            boolean checked, Instant checkedAt, String checkedBy, String remark
    ) {}

    public record TimelineView(
            Long id, String phase, String phaseLabel, String message,
            String actor, Instant occurredAt
    ) {}

    public record AlarmView(
            Long id, int trappedCount, Instant receivedAt,
            boolean merged, String callerNote
    ) {}

    public static IncidentView of(RescueIncident i, List<String> missingItems) {
        List<DispatchView> dispatches = Dispatch.<Dispatch>find("incident", i).list().stream()
                .sorted(Comparator.comparing(d -> d.team.name()))
                .map(d -> new DispatchView(
                        d.team.name(), d.team.label, d.state.name(), d.state.label,
                        d.assignee, d.etaLimitMinutes,
                        d.dispatchedAt, d.acknowledgedAt, d.arrivedAt,
                        d.arrivedAt != null
                                ? java.time.Duration.between(d.dispatchedAt, d.arrivedAt).toMinutes() : null,
                        d.arrivedAt != null
                                && java.time.Duration.between(d.dispatchedAt, d.arrivedAt).toMinutes() > d.etaLimitMinutes))
                .toList();

        List<CheckView> checks = HandoverCheck.<HandoverCheck>find("incident", i).list().stream()
                .sorted(Comparator.comparing(c -> c.checkKey.ordinal()))
                .map(c -> new CheckView(c.checkKey.name(), c.checkKey.code, c.checkKey.party,
                        c.checkKey.label, c.checked, c.checkedAt, c.checkedBy, c.remark))
                .toList();

        List<TimelineView> timeline = TimelineEvent.<TimelineEvent>find("incident = ?1 order by occurredAt, id", i)
                .list().stream()
                .map(t -> new TimelineView(t.id, t.phase.name(), t.phase.label, t.message, t.actor, t.occurredAt))
                .toList();

        List<AlarmView> alarms = AlarmReport.<AlarmReport>find("incident = ?1 order by receivedAt, id", i)
                .list().stream()
                .map(a -> new AlarmView(a.id, a.trappedCount, a.receivedAt, a.mergedInto != null, a.callerNote))
                .toList();

        boolean closed = i.status.name().equals("CLOSED");
        return new IncidentView(
                i.id,
                i.elevator.code, i.elevator.building, i.elevator.location, i.elevator.maintenanceVendor,
                i.elevator.status.name(), i.elevator.status.label,
                i.status.name(), i.status.label,
                i.trappedCount, i.callStatus.name(), i.callStatus.label,
                i.alarmCount, i.escalationLevel, i.escalationReasons,
                i.passengersUnwell, i.medicalAssistanceArranged,
                i.recheckPassed, i.recheckedAt, i.recheckInspector, i.recheckNote,
                i.openedAt, i.closedAt,
                dispatches, checks, timeline, alarms, missingItems,
                i.status.name().equals("RESCUED") && missingItems.isEmpty(),
                i.recheckPassed && closed && i.elevator.status.name().equals("OUT_OF_SERVICE")
        );
    }
}
