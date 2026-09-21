package com.sanyabhasin.queuepilot.controller;

import com.sanyabhasin.queuepilot.domain.DeadLetter;
import com.sanyabhasin.queuepilot.domain.Execution;
import com.sanyabhasin.queuepilot.repository.DeadLetterRepository;
import com.sanyabhasin.queuepilot.service.ExecutionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dead-letters")
public class DeadLetterController {
  private final DeadLetterRepository repository;
  private final ExecutionService executionService;

  public DeadLetterController(
      DeadLetterRepository repository, ExecutionService executionService) {
    this.repository = repository;
    this.executionService = executionService;
  }

  @GetMapping
  public List<DeadLetter> list(@RequestHeader("X-Tenant-ID") UUID tenantId) {
    return repository.findByTenantIdAndReplayedExecutionIdIsNull(tenantId);
  }

  @GetMapping("/{id}")
  public DeadLetter get(@RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id) {
    return repository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Dead letter not found"));
  }

  @PostMapping("/{id}/replay")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Execution replay(@RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id) {
    return executionService.replayDeadLetter(tenantId, id);
  }
}
