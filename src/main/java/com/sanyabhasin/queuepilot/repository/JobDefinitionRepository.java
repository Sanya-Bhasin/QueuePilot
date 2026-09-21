package com.sanyabhasin.queuepilot.repository;

import com.sanyabhasin.queuepilot.domain.JobDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobDefinitionRepository extends JpaRepository<JobDefinition, UUID> {
  Optional<JobDefinition> findByTenantIdAndNameAndVersion(UUID tenantId, String name, Integer version);

  List<JobDefinition> findByTenantIdAndIsActiveTrue(UUID tenantId);

  Optional<JobDefinition> findTopByTenantIdAndNameOrderByVersionDesc(UUID tenantId, String name);

  Optional<JobDefinition> findByIdAndTenantIdAndIsActiveTrue(UUID id, UUID tenantId);
}
