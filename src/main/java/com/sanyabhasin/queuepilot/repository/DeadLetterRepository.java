package com.sanyabhasin.queuepilot.repository;

import com.sanyabhasin.queuepilot.domain.DeadLetter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetter, UUID> {
  Optional<DeadLetter> findByIdAndTenantId(UUID id, UUID tenantId);

  Page<DeadLetter> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

  Optional<DeadLetter> findByExecutionId(UUID executionId);

  List<DeadLetter> findByTenantIdAndReplayedExecutionIdIsNull(UUID tenantId);
}
