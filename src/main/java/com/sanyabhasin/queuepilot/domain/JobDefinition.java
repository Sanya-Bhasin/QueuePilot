package com.sanyabhasin.queuepilot.domain;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Job definition entity representing a job type that can be registered and executed. */
@Entity
@Table(
    name = "job_definitions",
    indexes = {@Index(name = "idx_job_defs_tenant_active", columnList = "tenant_id, is_active")})
public class JobDefinition {

  @Id @Column(columnDefinition = "uuid") private UUID id;

  @Column(nullable = false, columnDefinition = "uuid")
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  private String description;

  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON) 
  private JsonNode schemaJson;

  @Column(nullable = false)
  private Integer version;

  @Column(nullable = false)
  private Boolean isActive;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public JobDefinition() {}

  public JobDefinition(UUID id, UUID tenantId, String name, JsonNode schemaJson) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name;
    this.schemaJson = schemaJson;
    this.version = 1;
    this.isActive = true;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public JobDefinition(UUID id, UUID tenantId, String name, Integer version, String description) {
    this(id, tenantId, name, com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
    this.version = version;
    this.description = description;
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public JsonNode getSchemaJson() {
    return schemaJson;
  }

  public void setSchemaJson(JsonNode schemaJson) {
    this.schemaJson = schemaJson;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
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
