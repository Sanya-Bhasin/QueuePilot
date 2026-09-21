package com.sanyabhasin.queuepilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * WebhookEndpoint entity representing a registered webhook URL where execution results are
 * delivered. Results are signed with HMAC-SHA256 using the endpoint's secret.
 */
@Entity
@Table(
    name = "webhook_endpoints",
    indexes = {@Index(name = "idx_webhook_endpoints_tenant", columnList = "tenant_id")})
public class WebhookEndpoint {

  @Id @Column(columnDefinition = "uuid") private UUID id;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID tenantId;

  @Column(nullable = false)
  private String url;

  @Column(nullable = false)
  private String secret;

  @Column(nullable = false)
  private Boolean isActive;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public WebhookEndpoint() {}

  public WebhookEndpoint(UUID id, UUID tenantId, String url, String secret) {
    this.id = id;
    this.tenantId = tenantId;
    this.url = url;
    this.secret = secret;
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

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
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
