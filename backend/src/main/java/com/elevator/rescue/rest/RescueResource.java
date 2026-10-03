package com.elevator.rescue.rest;

import com.elevator.rescue.dto.Dtos.*;
import com.elevator.rescue.model.Enums;
import com.elevator.rescue.model.RescueEvent;
import com.elevator.rescue.service.RescueService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** 值班室业务接口 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RescueResource {

    @Inject
    RescueService service;

    // ---- 事件 ----

    @GET
    @Path("/events")
    public List<RescueEvent> events() {
        return service.listEvents();
    }

    @GET
    @Path("/events/{id}")
    public RescueEvent get(@PathParam("id") Long id) {
        return service.get(id);
    }

    /** 值班台手工录入报警 */
    @POST
    @Path("/alarms")
    public RescueEvent alarm(AlarmRequest req) {
        return service.receiveAlarm(req, "值班台");
    }

    // ---- 分派与到场 ----

    @POST
    @Path("/events/{id}/dispatch")
    public RescueEvent dispatch(@PathParam("id") Long id, DispatchRequest req) {
        return service.dispatch(id, req);
    }

    @POST
    @Path("/events/{id}/ack")
    public RescueEvent ack(@PathParam("id") Long id, RoleRequest req) {
        if (req == null || req.role() == null) {
            throw new com.elevator.rescue.service.BusinessException("缺少角色 role（MAINTENANCE/GUARD）");
        }
        return service.roleAck(id, req.role());
    }

    @POST
    @Path("/events/{id}/arrived")
    public RescueEvent arrived(@PathParam("id") Long id, RoleRequest req) {
        if (req == null || req.role() == null) {
            throw new com.elevator.rescue.service.BusinessException("缺少角色 role（MAINTENANCE/GUARD）");
        }
        return service.arrived(id, req.role());
    }

    // ---- 通话 ----

    @PUT
    @Path("/events/{id}/call-status")
    public RescueEvent callStatus(@PathParam("id") Long id, CallStatusRequest req) {
        return service.setCallStatus(id, req);
    }

    // ---- 交接 ----

    @POST
    @Path("/events/{id}/handover/{role}")
    public RescueEvent handover(@PathParam("id") Long id,
                                @PathParam("role") Enums.PartyRole role,
                                HandoverRequest req) {
        return service.confirmHandover(id, role, req);
    }

    @POST
    @Path("/events/{id}/unwell")
    public RescueEvent unwell(@PathParam("id") Long id, UnwellRequest req) {
        return service.reportUnwell(id, req);
    }

    // ---- 复检 / 恢复 / 关闭 ----

    @POST
    @Path("/events/{id}/reinspect")
    public RescueEvent reinspect(@PathParam("id") Long id, ReinspectRequest req) {
        return service.reinspect(id, req);
    }

    @POST
    @Path("/events/{id}/resume")
    public RescueEvent resume(@PathParam("id") Long id) {
        return service.resumeService(id);
    }

    @POST
    @Path("/events/{id}/close")
    public RescueEvent close(@PathParam("id") Long id) {
        return service.close(id);
    }
}
