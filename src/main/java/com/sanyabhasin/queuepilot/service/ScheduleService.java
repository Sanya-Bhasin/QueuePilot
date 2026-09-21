package com.sanyabhasin.queuepilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanyabhasin.queuepilot.domain.Schedule;
import com.sanyabhasin.queuepilot.repository.ScheduleRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns schedule validation, next-fire calculation, and due schedule claiming. */
@Service
public class ScheduleService {
  private final ScheduleRepository schedules;
  private final ExecutionService executions;

  public ScheduleService(ScheduleRepository schedules, ExecutionService executions) {
    this.schedules = schedules;
    this.executions = executions;
  }

  @Transactional
  public Schedule create(
      UUID tenantId,
      UUID jobId,
      String cron,
      String timezone,
      Instant scheduledFor,
      JsonNode params,
      int maxConcurrent) {
    if (cron == null && scheduledFor == null || cron != null && scheduledFor != null) {
      throw new IllegalArgumentException("Exactly one of cron or scheduledFor is required");
    }
    if (cron != null) {
      CronExpression.parse(cron);
    }
    ZoneId.of(timezone == null ? "UTC" : timezone);
    Schedule schedule = new Schedule(UUID.randomUUID(), tenantId, jobId);
    schedule.setCronExpression(cron);
    schedule.setTimezone(timezone == null ? "UTC" : timezone);
    schedule.setScheduledFor(scheduledFor);
    schedule.setParamsJson(params);
    schedule.setMaxConcurrent(Math.max(1, maxConcurrent));
    schedule.setNextRunAt(scheduledFor != null ? scheduledFor : nextRun(cron, schedule.getTimezone(), Instant.now()));
    return schedules.save(schedule);
  }

  @Transactional
  public void processDueSchedules() {
    List<Schedule> due = schedules.findByIsActiveTrueAndNextRunAtIsNotNullAndNextRunAtLessThanEqual(Instant.now());
    for (Schedule schedule : due) {
      String key = schedule.getId() + ":" + schedule.getNextRunAt();
      executions.enqueueJob(schedule.getTenantId(), schedule.getJobId(), schedule.getParamsJson(), key);
      schedule.setLastRunAt(Instant.now());
      if (schedule.getCronExpression() == null) {
        schedule.setIsActive(false);
        schedule.setNextRunAt(null);
      } else {
        schedule.setNextRunAt(nextRun(schedule.getCronExpression(), schedule.getTimezone(), Instant.now()));
      }
      schedules.save(schedule);
    }
  }

  private Instant nextRun(String cron, String timezone, Instant from) {
    return CronExpression.parse(cron)
        .next(java.time.ZonedDateTime.ofInstant(from, ZoneId.of(timezone)))
        .toInstant();
  }
}
