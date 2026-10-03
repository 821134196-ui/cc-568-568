package com.elevator.rescue.web;

import com.elevator.rescue.domain.Dispatch;
import com.elevator.rescue.domain.HandoverCheck;
import com.elevator.rescue.domain.IncidentStatus;
import com.elevator.rescue.domain.RescueIncident;
import com.elevator.rescue.service.IncidentService;
import com.elevator.rescue.web.dto.IncidentView;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/incidents")
@Produces(MediaType.APPLICATION_JSON)
public class IncidentResource {

    @Inject
    IncidentService service;

    @GET
    public List<IncidentView> list(@QueryParam("includeClosed") boolean includeClosed) {
        List<RescueIncident> incidents = includeClosed
                ? RescueIncident.list("order by id desc")
                : RescueIncident.list("status <> ?1 order by id desc", IncidentStatus.CLOSED);
        return incidents.stream().map(i -> IncidentView.of(i, service.missingHandoverItems(i))).toList();
    }

    @GET
    @Path("/{id}")
    public IncidentView get(@PathParam("id") Long id) {
        RescueIncident incident = RescueIncident.findById(id);
        if (incident == null) {
            throw com.elevator.rescue.service.ApiException.notFound("救援事件不存在: " + id);
        }
        return IncidentView.of(incident, service.missingHandoverItems(incident));
    }

    public record DispatchRequest(
            String maintenanceAssignee, String securityAssignee,
            Integer maintenanceEtaMinutes, Integer securityEtaMinutes) {}

    @POST
    @Path("/{id}/dispatch")
    public IncidentView dispatch(@PathParam("id") Long id, DispatchRequest req) {
        RescueIncident i = service.dispatch(id, req.maintenanceAssignee(), req.securityAssignee(),
                req.maintenanceEtaMinutes(), req.securityEtaMinutes());
        return IncidentView.of(i, service.missingHandoverItems(i));
    }

    @POST
    @Path("/{id}/call/interrupt")
    public IncidentView interruptCall(@PathParam("id") Long id) {
        RescueIncident i = service.markCallInterrupted(id);
        return IncidentView.of(i, service.missingHandoverItems(i));
    }

    @POST
    @Path("/{id}/call/restore")
    public IncidentView restoreCall(@PathParam("id") Long id) {
        RescueIncident i = service.restoreCall(id);
        return IncidentView.of(i, service.missingHandoverItems(i));
    }

    public record RescueCompleteRequest(boolean passengersUnwell) {}

    @POST
    @Path("/{id}/rescue-complete")
    public IncidentView completeRescue(@PathParam("id") Long id, RescueCompleteRequest req) {
        RescueIncident i = service.completeRescue(id, req.passengersUnwell());
        return IncidentView.of(i, service.missingHandoverItems(i));
    }

    @POST
    @Path("/{id}/medical")
    public IncidentView arrangeMedical(@PathParam("id") Long id) {
        RescueIncident i = service.arrangeMedical(id);
        return IncidentView.of(i, service.missingHandoverItems(i));
    }

    public record CheckRequest(String key, Boolean checked, String checkedBy, String remark) {}

    @POST
    @Path("/{id}/checks")
    public IncidentView setCheck(@PathParam("id") Long id, CheckRequest req) {
        if (req.key() == null) {
            throw com.elevator.rescue.service.ApiException.badRequest("缺少核对项 key");
        }
        HandoverCheck.Key key;
        try {
            key = HandoverCheck.Key.valueOf(req.key());
        } catch (IllegalArgumentException e) {
            throw com.elevator.rescue.service.ApiException.badRequest("未知核对项: " + req.key());
        }
        RescueIncident i = service.setCheck(id, key, Boolean.TRUE.equals(req.checked()),
                req.checkedBy(), req.remark());
        return IncidentView.of(i, service.missingHandoverItems(i));
    }

    @POST
    @Path("/{id}/close")
    public IncidentView close(@PathParam("id") Long id) {
        RescueIncident i = service.close(id);
        return IncidentView.of(i, service.missingHandoverItems(i));
    }

    public record RecheckRequest(String inspector, Boolean passed, String note) {}

    @POST
    @Path("/{id}/recheck")
    public IncidentView recheck(@PathParam("id") Long id, RecheckRequest req) {
        RescueIncident i = service.recheck(id, req.inspector(), Boolean.TRUE.equals(req.passed()), req.note());
        return IncidentView.of(i, service.missingHandoverItems(i));
    }

    @POST
    @Path("/{id}/resume")
    public IncidentView resume(@PathParam("id") Long id) {
        RescueIncident i = service.resumeService(id);
        return IncidentView.of(i, service.missingHandoverItems(i));
    }
}
