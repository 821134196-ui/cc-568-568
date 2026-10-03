package com.elevator.rescue.bootstrap;

import com.elevator.rescue.service.IncidentService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** 每 20 秒巡检：维保超过到场时限仍未签到则自动升级（同一原因只升级一次） */
@ApplicationScoped
public class EscalationScheduler {

    @Inject
    IncidentService incidentService;

    @Scheduled(every = "20s", delayed = "30s")
    void checkLateMaintenance() {
        incidentService.detectLateMaintenance();
    }
}
