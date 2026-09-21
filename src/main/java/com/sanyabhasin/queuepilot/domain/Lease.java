package com.sanyabhasin.queuepilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Lease entity representing a distributed lease on a job execution. A worker holds a lease
 * while processing a job; the lease expires after a TTL (e.g., 30 seconds). If the worker
 * crashes before renewing its heartbeat, the lease expires and RabbitMQ redelivers the message
 * to another worker. This design prevents poison-message loops and ensures no two workers
 * process the same job simultaneously.
 */
@Entity
@Table(
    name = "leases",
    indexes = {
      @Index(name = "idx_leases_expires_at", columnList = "expires_at"),
      @Index(name = "idx_leases_worker_id", columnList = "worker_id")
    })
public class Lease {

  @Id @Column(columnDefinition = "uuid") private UUID id;

  @Column(nullable = false, columnDefinition = "uuid", unique = true)
  private UUID executionId;

  @Column(nullable = false)
  private String workerId;

  @Column(nullable = false)
  private Instant acquiredAt;

  @Column(nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private Instant lastHeartbeatAt;

  public Lease() {}

  public Lease(UUID id, UUID executionId, String workerId, Instant expiresAt) {
    this.id = id;
    this.executionId = executionId;
    this.workerId = workerId;
    this.acquiredAt = Instant.now();
    this.expiresAt = expiresAt;
    this.lastHeartbeatAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getExecutionId() {
    return executionId;
  }

  public void setExecutionId(UUID executionId) {
    this.executionId = executionId;
  }

  public String getWorkerId() {
    return workerId;
  }

  public void setWorkerId(String workerId) {
    this.workerId = workerId;
  }

  public Instant getAcquiredAt() {
    return acquiredAt;
  }

  public void setAcquiredAt(Instant acquiredAt) {
    this.acquiredAt = acquiredAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getLastHeartbeatAt() {
    return lastHeartbeatAt;
  }

  public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
    this.lastHeartbeatAt = lastHeartbeatAt;
  }
}
