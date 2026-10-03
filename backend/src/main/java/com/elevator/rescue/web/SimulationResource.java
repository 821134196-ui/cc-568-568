package com.elevator.rescue.web;

import com.elevator.rescue.domain.Dispatch;
import com.elevator.rescue.domain.RescueIncident;
import com.elevator.rescue.service.IncidentService;
import com.elevator.rescue.web.dto.IncidentView;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * 本地模拟接口：
 * - /api/sim/alarms          模拟楼宇五方通话/物联平台推送困人报警
 * - /api/sim/ack, arrive     模拟维保单位与现场保安的接单回执、到场签到
 */
@Path("/api/sim")
@Produces(MediaType.APPLICATION_JSON)
public class SimulationResource {

    @Inject
    IncidentService service;

    public record AlarmRequest(String elevatorCode, Integer trappedCount, String note) {}

    public record AlarmResponse(Long incidentId, boolean merged, int alarmCount, IncidentView incident) {}

    @POST
    @Path("/alarms")
    public AlarmResponse alarm(AlarmRequest req) {
        if (req.elevatorCode() == null || req.elevatorCode().isBlank()) {
            throw com.elevator.rescue.service.ApiException.badRequest("缺少电梯编号 elevatorCode");
        }
        int count = req.trappedCount() != null ? req.trappedCount() : 1;
        IncidentService.AlarmResult result = service.receiveAlarm(req.elevatorCode(), count, req.note());
        return new AlarmResponse(result.incident().id, result.merged(), result.alarmCount(),
                IncidentView.of(result.incident(), service.missingHandoverItems(result.incident())));
    }

    public record CallbackRequest(Long incidentId, String team, Integer delayMinutes) {}

    @POST
    @Path("/ack")
    public IncidentView ack(CallbackRequest req) {
        Dispatch d = service.acknowledge(req.incidentId(), parseTeam(req.team()));
        return IncidentView.of(d.incident, service.missingHandoverItems(d.incident));
    }

    @POST
    @Path("/arrive")
    public IncidentView arrive(CallbackRequest req) {
        Dispatch d = service.arrive(req.incidentId(), parseTeam(req.team()), req.delayMinutes());
        return IncidentView.of(d.incident, service.missingHandoverItems(d.incident));
    }

    /** 立即执行一次维保迟到巡检（定时任务每 20 秒自动执行，此端点用于演示即时触发） */
    @POST
    @Path("/escalation-check")
    public List<Long> escalationCheck() {
        return service.detectLateMaintenance().stream().map(i -> i.id).toList();
    }

    private Dispatch.Team parseTeam(String team) {        if (team == null) {
            throw com.elevator.rescue.service.ApiException.badRequest("缺少 team（MAINTENANCE/SECURITY）");
        }
        try {
            return Dispatch.Team.valueOf(team.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw com.elevator.rescue.service.ApiException.badRequest("未知队伍: " + team);
        }
    }
}
