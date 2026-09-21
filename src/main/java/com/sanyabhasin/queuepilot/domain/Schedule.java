package com.sanyabhasin.queuepilot.domain;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Schedule entity representing a recurring or one-time job schedule. The scheduler periodically
 * scans for due schedules and enqueues executions. CRON expressions are evaluated in the
 * tenant's configured timezone.
 */
@Entity
@Table(
    name = "schedules",
    indexes = {
      @Index(name = "idx_schedules_next_run", columnList = "next_run_at"),
      @Index(name = "idx_schedules_tenant", columnList = "tenant_id")
    })
public class Schedule {

  @Id @Column(columnDefinition = "uuid") private UUID id;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID tenantId;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID jobId;

  private String cronExpression;

  @Column(nullable = false)
  private String timezone;

  private Instant scheduledFor; // one-time schedule

  private Instant nextRunAt;

  private Instant lastRunAt;

  @Column(columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private JsonNode paramsJson;

  @Column(nullable = false)
  private Integer maxConcurrent;

  @Column(nullable = false)
  private Boolean isActive;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public Schedule() {}

  public Schedule(UUID id, UUID tenantId, UUID jobId) {
    this.id = id;
    this.tenantId = tenantId;
    this.jobId = jobId;
    this.timezone = "UTC";
    this.maxConcurrent = 1;
    this.isActive = true;
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

  public String getCronExpression() {
    return cronExpression;
  }

  public void setCronExpression(String cronExpression) {
    this.cronExpression = cronExpression;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public Instant getScheduledFor() {
    return scheduledFor;
  }

  public void setScheduledFor(Instant scheduledFor) {
    this.scheduledFor = scheduledFor;
  }

  public Instant getNextRunAt() {
    return nextRunAt;
  }

  public void setNextRunAt(Instant nextRunAt) {
    this.nextRunAt = nextRunAt;
  }

  public Instant getLastRunAt() {
    return lastRunAt;
  }

  public void setLastRunAt(Instant lastRunAt) {
    this.lastRunAt = lastRunAt;
  }

  public JsonNode getParamsJson() {
    return paramsJson;
  }

  public void setParamsJson(JsonNode paramsJson) {
    this.paramsJson = paramsJson;
  }

  public Integer getMaxConcurrent() {
    return maxConcurrent;
  }

  public void setMaxConcurrent(Integer maxConcurrent) {
    this.maxConcurrent = maxConcurrent;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
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
