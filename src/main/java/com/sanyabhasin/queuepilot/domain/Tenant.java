package com.sanyabhasin.queuepilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tenant entity representing a logical boundary for multi-tenancy. All jobs, schedules, and
 * executions are scoped to a tenant. Tenant isolation is enforced at API and database levels.
 */
@Entity
@Table(name = "tenants", indexes = {
    @Index(name = "idx_tenants_api_key_hash", columnList = "api_key_hash"),
    @Index(name = "idx_tenants_active", columnList = "is_active")
})
public class Tenant {

  @Id @Column(columnDefinition = "uuid") private UUID id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(nullable = false)
  private byte[] apiKeyHash;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Column(nullable = false)
  private Boolean isActive;

  public Tenant() {}

  public Tenant(UUID id, String name, byte[] apiKeyHash) {
    this.id = id;
    this.name = name;
    this.apiKeyHash = apiKeyHash;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.isActive = true;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public byte[] getApiKeyHash() {
    return apiKeyHash;
  }

  public void setApiKeyHash(byte[] apiKeyHash) {
    this.apiKeyHash = apiKeyHash;
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

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }
}
