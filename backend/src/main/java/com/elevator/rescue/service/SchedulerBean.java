package com.elevator.rescue.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** 周期性业务检查：维保 SLA 迟到升级 */
@ApplicationScoped
public class SchedulerBean {

    @Inject
    RescueService service;

    @Scheduled(every = "${rescue.late-check.every:15s}")
    void checkLate() {
        service.checkLateMaintenance();
    }
}
