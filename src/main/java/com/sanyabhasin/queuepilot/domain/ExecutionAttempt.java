package com.sanyabhasin.queuepilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * ExecutionAttempt entity recording a single attempt to execute a job. Each attempt logs the
 * outcome (success, transient failure, permanent failure), error details, and duration.
 */
@Entity
@Table(
    name = "execution_attempts",
    indexes = {
      @Index(name = "idx_execution_attempts_execution", columnList = "execution_id, attempt_number")
    })
public class ExecutionAttempt {

  @Id @Column(columnDefinition = "uuid") private UUID id;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID executionId;

  @Column(nullable = false)
  private Integer attemptNumber;

  private String workerId;

  @Column(nullable = false)
  private Instant startedAt;

  private Instant endedAt;

  @Column(nullable = false)
  private String status; // SUCCESS, TRANSIENT_FAILURE, PERMANENT_FAILURE

  private String errorCode;

  private String errorMessage;

  private Integer durationMs;

  public ExecutionAttempt() {}

  public ExecutionAttempt(UUID id, UUID executionId, Integer attemptNumber) {
    this.id = id;
    this.executionId = executionId;
    this.attemptNumber = attemptNumber;
    this.startedAt = Instant.now();
  }

  public ExecutionAttempt(UUID id, UUID executionId, Integer attemptNumber, String status) {
    this(id, executionId, attemptNumber);
    this.status = status;
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

  public Integer getAttemptNumber() {
    return attemptNumber;
  }

  public void setAttemptNumber(Integer attemptNumber) {
    this.attemptNumber = attemptNumber;
  }

  public String getWorkerId() {
    return workerId;
  }

  public void setWorkerId(String workerId) {
    this.workerId = workerId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(Instant endedAt) {
    this.endedAt = endedAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Integer getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(Integer durationMs) {
    this.durationMs = durationMs;
  }
}
