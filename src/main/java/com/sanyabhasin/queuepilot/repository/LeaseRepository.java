package com.sanyabhasin.queuepilot.repository;

import com.sanyabhasin.queuepilot.domain.Lease;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseRepository extends JpaRepository<Lease, UUID> {
  Optional<Lease> findByExecutionId(UUID executionId);

  List<Lease> findByExpiresAtLessThan(Instant time);

  List<Lease> findByWorkerId(String workerId);

  void deleteByExecutionId(UUID executionId);
}
