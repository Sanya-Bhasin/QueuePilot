package com.sanyabhasin.queuepilot.repository;

import com.sanyabhasin.queuepilot.domain.WebhookEndpoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
  Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, UUID tenantId);

  default Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, UUID tenantId, boolean active) {
    return findByIdAndTenantId(id, tenantId).filter(endpoint -> endpoint.getIsActive() == active);
  }

  List<WebhookEndpoint> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
