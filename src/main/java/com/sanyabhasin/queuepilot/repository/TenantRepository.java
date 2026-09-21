package com.sanyabhasin.queuepilot.repository;

import com.sanyabhasin.queuepilot.domain.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
  Optional<Tenant> findByName(String name);

  Optional<Tenant> findByApiKeyHash(byte[] apiKeyHash);
}
