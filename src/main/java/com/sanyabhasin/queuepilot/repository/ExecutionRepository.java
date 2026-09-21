package com.sanyabhasin.queuepilot.repository;

import com.sanyabhasin.queuepilot.domain.Execution;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
  Optional<Execution> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

  Optional<Execution> findByIdAndTenantId(UUID id, UUID tenantId);

  List<Execution> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Page<Execution> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status, Pageable pageable);

  Page<Execution> findByTenantIdAndJobIdAndStatusOrderByCreatedAtDesc(UUID tenantId, UUID jobId, String status, Pageable pageable);

  List<Execution> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(String status, Instant time);

  List<Execution> findByStatusAndExpiresAtLessThanEqual(String status, Instant time);

  List<Execution> findByStatusAndCreatedAtGreaterThanOrderByCreatedAtAsc(String status, Instant time);

  long countByTenantIdAndStatus(UUID tenantId, String status);
}
