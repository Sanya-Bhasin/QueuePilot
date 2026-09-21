package com.sanyabhasin.queuepilot.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduleMonitor {
  private final ScheduleService schedules;

  public ScheduleMonitor(ScheduleService schedules) {
    this.schedules = schedules;
  }

  @Scheduled(fixedDelayString = "${queuepilot.scheduler.interval-ms:10000}")
  public void enqueueDueSchedules() {
    schedules.processDueSchedules();
  }
}
