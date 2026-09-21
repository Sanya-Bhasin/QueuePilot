package com.sanyabhasin.queuepilot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanyabhasin.queuepilot.domain.Execution;
import com.sanyabhasin.queuepilot.repository.ExecutionRepository;
import com.sanyabhasin.queuepilot.service.ExecutionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ExecutionController exposes REST endpoints for enqueueing, tracking, and managing jobs.
 */
@RestController
@RequestMapping("/executions")
public class ExecutionController {

  private final ExecutionService executionService;
  private final ExecutionRepository executionRepository;

  public ExecutionController(ExecutionService executionService, ExecutionRepository executionRepository) {
    this.executionService = executionService;
    this.executionRepository = executionRepository;
  }

  /**
   * Enqueue a new job. Tenant ID is passed as a header for multi-tenant isolation.
   */
  @PostMapping
  public ResponseEntity<Execution> enqueueJob(
      @RequestHeader("X-Tenant-ID") UUID tenantId,
      @RequestBody EnqueueRequest request
  ) {
    Execution execution = executionService.enqueueJob(
        tenantId,
        request.jobId(),
        request.params(),
        request.idempotencyKey()
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(execution);
  }

  /**
   * Get execution status by ID.
   */
  @GetMapping("/{executionId}")
  public ResponseEntity<Execution> getExecution(
      @RequestHeader("X-Tenant-ID") UUID tenantId,
      @PathVariable UUID executionId
  ) {
    Execution execution = executionRepository.findByIdAndTenantId(executionId, tenantId)
        .orElse(null);
    if (execution == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(execution);
  }

  /**
   * List executions for a tenant.
   */
  @GetMapping
  public ResponseEntity<List<Execution>> listExecutions(
      @RequestHeader("X-Tenant-ID") UUID tenantId
  ) {
    List<Execution> executions = executionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    return ResponseEntity.ok(executions);
  }

  /**
   * Cancel a pending execution.
   */
  @PostMapping("/{executionId}/cancel")
  public ResponseEntity<Void> cancelExecution(
      @RequestHeader("X-Tenant-ID") UUID tenantId,
      @PathVariable UUID executionId
  ) {
    executionService.cancelExecution(tenantId, executionId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Request body for enqueue operation.
   */
  public record EnqueueRequest(
      UUID jobId,
      JsonNode params,
      String idempotencyKey
  ) {}
}
