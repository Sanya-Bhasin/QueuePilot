package com.sanyabhasin.queuepilot.repository;

import com.sanyabhasin.queuepilot.domain.ExecutionAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionAttemptRepository extends JpaRepository<ExecutionAttempt, UUID> {
  List<ExecutionAttempt> findByExecutionIdOrderByAttemptNumberAsc(UUID executionId);

  ExecutionAttempt findTopByExecutionIdOrderByAttemptNumberDesc(UUID executionId);
}
