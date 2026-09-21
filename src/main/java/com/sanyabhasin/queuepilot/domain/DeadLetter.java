package com.sanyabhasin.queuepilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * DeadLetter entity representing a permanently failed execution. Executions move to dead letters
 * when: (1) max attempts exceeded, (2) execution too old, or (3) job executor returns
 * PERMANENT_FAILURE. Operators inspect dead letters, diagnose failures, and replay if the
 * underlying issue is fixed.
 */
@Entity
@Table(
    name = "dead_letters",
    indexes = {@Index(name = "idx_dead_letters_tenant", columnList = "tenant_id, created_at DESC")})
public class DeadLetter {

  @Id @Column(columnDefinition = "uuid") private UUID id;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID tenantId;

  @Column(nullable = false, columnDefinition = "uuid", unique = true)
  private UUID executionId;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID jobId;

  @Column(nullable = false)
  private String reason; // e.g., MAX_ATTEMPTS_EXCEEDED

  private String errorSummary;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(columnDefinition = "uuid")
  private UUID replayedExecutionId;

  public DeadLetter() {}

  public DeadLetter(UUID id, UUID tenantId, UUID executionId, UUID jobId, String reason) {
    this.id = id;
    this.tenantId = tenantId;
    this.executionId = executionId;
    this.jobId = jobId;
    this.reason = reason;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public UUID getExecutionId() {
    return executionId;
  }

  public void setExecutionId(UUID executionId) {
    this.executionId = executionId;
  }

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getErrorSummary() {
    return errorSummary;
  }

  public void setErrorSummary(String errorSummary) {
    this.errorSummary = errorSummary;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public UUID getReplayedExecutionId() {
    return replayedExecutionId;
  }

  public void setReplayedExecutionId(UUID replayedExecutionId) {
    this.replayedExecutionId = replayedExecutionId;
  }
}
