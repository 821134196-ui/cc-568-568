package com.elevator.rescue.dto;

import com.elevator.rescue.model.Enums;

public final class Dtos {
    private Dtos() {}

    public record AlarmRequest(String elevatorId, String location, Integer trappedCount,
                               Enums.CallStatus callStatus) {}

    public record DispatchRequest(String maintenanceTeam, String guardName, Integer etaMinutes) {}

    public record CallStatusRequest(Enums.CallStatus callStatus) {}

    public record RoleRequest(Enums.PartyRole role) {}

    public record HandoverRequest(String confirmer, Boolean doorOpened,
                                  Boolean personsExited, Boolean equipmentStopped) {}

    public record UnwellRequest(Boolean unwell, String note) {}

    public record ReinspectRequest(String inspector, Boolean passed) {}

    public record MockAlarmRequest(String elevatorId, String location,
                                   Integer trappedCount, Boolean callInterrupted) {}
}
