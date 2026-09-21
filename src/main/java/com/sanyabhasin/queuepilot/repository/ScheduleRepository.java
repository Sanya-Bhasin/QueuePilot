package com.sanyabhasin.queuepilot.repository;

import com.sanyabhasin.queuepilot.domain.Schedule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
  List<Schedule> findByTenantIdAndIsActiveTrue(UUID tenantId);

  List<Schedule> findByIsActiveTrueAndNextRunAtIsNotNullAndNextRunAtLessThanEqual(Instant time);

  Optional<Schedule> findByIdAndTenantId(UUID id, UUID tenantId);
}
