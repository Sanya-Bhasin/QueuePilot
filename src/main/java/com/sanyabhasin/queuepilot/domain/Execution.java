package com.sanyabhasin.queuepilot.domain;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Execution entity representing a single job execution instance. Status transitions: PENDING →
 * CLAIMED → SUCCEEDED/FAILED or DEAD_LETTERED. At-least-once delivery is guaranteed by
 * RabbitMQ; idempotency keys prevent duplicate work.
 */
@Entity
@Table(
    name = "executions",
    indexes = {
      @Index(name = "idx_executions_tenant_status", columnList = "tenant_id, status"),
      @Index(name = "idx_executions_next_attempt", columnList = "next_attempt_at"),
      @Index(name = "idx_executions_job_status", columnList = "job_id, status"),
      @Index(name = "idx_executions_created_at", columnList = "created_at DESC")
    })
public class Execution {

  @Id @Column(columnDefinition = "uuid") private UUID id;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID tenantId;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID jobId;

  @Column(columnDefinition = "uuid")
  private UUID scheduleId;

  @Column(nullable = false)
  private String status; // PENDING, CLAIMED, SUCCEEDED, FAILED, DEAD_LETTERED

  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private JsonNode paramsJson;

  @Column(columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON) 
  private JsonNode resultJson;

  @Column(nullable = false)
  private Integer attemptCount;

  @Column(nullable = false)
  private Integer maxAttempts;

  private Instant nextAttemptAt;

  private String lastError;

  private Instant expiresAt;

  private String idempotencyKey;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public Execution() {}

  public Execution(UUID id, UUID tenantId, UUID jobId, JsonNode paramsJson) {
    this.id = id;
    this.tenantId = tenantId;
    this.jobId = jobId;
    this.paramsJson = paramsJson;
    this.status = "PENDING";
    this.attemptCount = 0;
    this.maxAttempts = 10;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
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

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public void setScheduleId(UUID scheduleId) {
    this.scheduleId = scheduleId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public JsonNode getParamsJson() {
    return paramsJson;
  }

  public void setParamsJson(JsonNode paramsJson) {
    this.paramsJson = paramsJson;
  }

  public JsonNode getResultJson() {
    return resultJson;
  }

  public void setResultJson(JsonNode resultJson) {
    this.resultJson = resultJson;
  }

  public Integer getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(Integer attemptCount) {
    this.attemptCount = attemptCount;
  }

  public Integer getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(Integer maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(Instant nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
