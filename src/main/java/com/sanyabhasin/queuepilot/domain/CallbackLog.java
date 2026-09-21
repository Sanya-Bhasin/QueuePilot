package com.sanyabhasin.queuepilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * CallbackLog entity recording the delivery of an execution result to a webhook endpoint.
 * Multiple delivery attempts may be logged if retries occur.
 */
@Entity
@Table(
    name = "callback_logs",
    indexes = {
      @Index(name = "idx_callback_logs_execution", columnList = "execution_id"),
      @Index(name = "idx_callback_logs_webhook", columnList = "webhook_id, delivered_at DESC")
    })
public class CallbackLog {

  @Id @Column(columnDefinition = "uuid") private UUID id;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID executionId;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID webhookId;

  @Column(nullable = false)
  private Integer attemptNumber;

  @Column(nullable = false)
  private Instant deliveredAt;

  private Integer httpStatusCode;

  private Integer responseTimeMs;

  @Column(nullable = false)
  private Boolean success;

  private String errorMessage;

  public CallbackLog() {}

  public CallbackLog(UUID id, UUID executionId, UUID webhookId, Integer attemptNumber) {
    this.id = id;
    this.executionId = executionId;
    this.webhookId = webhookId;
    this.attemptNumber = attemptNumber;
    this.deliveredAt = Instant.now();
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

  public UUID getWebhookId() {
    return webhookId;
  }

  public void setWebhookId(UUID webhookId) {
    this.webhookId = webhookId;
  }

  public Integer getAttemptNumber() {
    return attemptNumber;
  }

  public void setAttemptNumber(Integer attemptNumber) {
    this.attemptNumber = attemptNumber;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public void setDeliveredAt(Instant deliveredAt) {
    this.deliveredAt = deliveredAt;
  }

  public Integer getHttpStatusCode() {
    return httpStatusCode;
  }

  public void setHttpStatusCode(Integer httpStatusCode) {
    this.httpStatusCode = httpStatusCode;
  }

  public Integer getResponseTimeMs() {
    return responseTimeMs;
  }

  public void setResponseTimeMs(Integer responseTimeMs) {
    this.responseTimeMs = responseTimeMs;
  }

  public Boolean getSuccess() {
    return success;
  }

  public void setSuccess(Boolean success) {
    this.success = success;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
