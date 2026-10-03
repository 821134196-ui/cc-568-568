package com.elevator.rescue.web;

import com.elevator.rescue.domain.Elevator;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/elevators")
@Produces(MediaType.APPLICATION_JSON)
public class ElevatorResource {

    public record ElevatorView(Long id, String code, String building, String location,
                               String maintenanceVendor, String status, String statusLabel) {
        static ElevatorView of(Elevator e) {
            return new ElevatorView(e.id, e.code, e.building, e.location,
                    e.maintenanceVendor, e.status.name(), e.status.label);
        }
    }

    @GET
    public List<ElevatorView> list() {
        return Elevator.<Elevator>list("order by code").stream().map(ElevatorView::of).toList();
    }
}
