package com.elevator.rescue.rest;

import com.elevator.rescue.dto.Dtos.*;
import com.elevator.rescue.model.Enums;
import com.elevator.rescue.model.RescueEvent;
import com.elevator.rescue.service.RescueService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * 本地模拟接口：
 *  - 电梯五方对讲/告警平台上报的困人报警
 *  - 维保单位的接单回执与到场打卡
 * 真实环境由外部平台推送，此处本地模拟。
 */
@Path("/api/mock")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MockResource {

    @Inject
    RescueService service;

    /** 楼宇电梯基础档案（模拟） */
    private static final List<Map<String, String>> ELEVATORS = List.of(
            Map.of("elevatorId", "A1-K01", "location", "A栋1单元·客梯"),
            Map.of("elevatorId", "A1-H02", "location", "A栋1单元·货梯"),
            Map.of("elevatorId", "B2-K01", "location", "B栋2单元·客梯"),
            Map.of("elevatorId", "C1-K03", "location", "C栋1单元·客梯"));

    @GET
    @Path("/elevators")
    public List<Map<String, String>> elevators() {
        return ELEVATORS;
    }

    /** 模拟告警平台/五方对讲推送困人报警 */
    @POST
    @Path("/alarm")
    public RescueEvent alarm(MockAlarmRequest req) {
        if (req == null || req.elevatorId() == null) {
            throw new com.elevator.rescue.service.BusinessException("缺少 elevatorId");
        }
        String location = req.location();
        if (location == null || location.isBlank()) {
            location = ELEVATORS.stream()
                    .filter(x -> x.get("elevatorId").equals(req.elevatorId()))
                    .map(x -> x.get("location")).findFirst().orElse(req.elevatorId());
        }
        Enums.CallStatus call = Boolean.TRUE.equals(req.callInterrupted())
                ? Enums.CallStatus.INTERRUPTED : Enums.CallStatus.CONNECTED;
        AlarmRequest inner = new AlarmRequest(req.elevatorId(), location,
                req.trappedCount(), call);
        return service.receiveAlarm(inner, "五方对讲(模拟)");
    }

    /** 模拟维保单位回执：kind = ack（接单）或 arrived（到场打卡） */
    @POST
    @Path("/maintenance-callback/{eventId}/{kind}")
    public RescueEvent maintenanceCallback(@PathParam("eventId") Long eventId,
                                           @PathParam("kind") String kind) {
        return switch (kind) {
            case "ack" -> service.roleAck(eventId, Enums.PartyRole.MAINTENANCE);
            case "arrived" -> service.arrived(eventId, Enums.PartyRole.MAINTENANCE);
            default -> throw new com.elevator.rescue.service.BusinessException("未知回执类型: " + kind);
        };
    }

    /** 模拟保安到场打卡 */
    @POST
    @Path("/guard-callback/{eventId}/{kind}")
    public RescueEvent guardCallback(@PathParam("eventId") Long eventId,
                                     @PathParam("kind") String kind) {
        return switch (kind) {
            case "ack" -> service.roleAck(eventId, Enums.PartyRole.GUARD);
            case "arrived" -> service.arrived(eventId, Enums.PartyRole.GUARD);
            default -> throw new com.elevator.rescue.service.BusinessException("未知回执类型: " + kind);
        };
    }
}
