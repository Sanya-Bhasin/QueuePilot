package com.sanyabhasin.queuepilot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanyabhasin.queuepilot.domain.Schedule;
import com.sanyabhasin.queuepilot.repository.ScheduleRepository;
import com.sanyabhasin.queuepilot.service.ScheduleService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/schedules")
public class ScheduleController {
  private final ScheduleService service;
  private final ScheduleRepository repository;

  public ScheduleController(ScheduleService service, ScheduleRepository repository) {
    this.service = service;
    this.repository = repository;
  }

  @PostMapping
  public ResponseEntity<Schedule> create(
      @RequestHeader("X-Tenant-ID") UUID tenantId, @RequestBody Request request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            service.create(
                tenantId,
                request.jobId(),
                request.cron(),
                request.timezone(),
                request.scheduledFor(),
                request.params(),
                request.maxConcurrent()));
  }

  @GetMapping
  public List<Schedule> list(@RequestHeader("X-Tenant-ID") UUID tenantId) {
    return repository.findByTenantIdAndIsActiveTrue(tenantId);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(
      @RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id) {
    Schedule schedule =
        repository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found"));
    schedule.setIsActive(false);
    repository.save(schedule);
    return ResponseEntity.noContent().build();
  }

  public record Request(
      UUID jobId,
      String cron,
      String timezone,
      Instant scheduledFor,
      JsonNode params,
      int maxConcurrent) {}
}
