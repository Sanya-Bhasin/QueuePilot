package com.sanyabhasin.queuepilot.repository;

import com.sanyabhasin.queuepilot.domain.CallbackLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallbackLogRepository extends JpaRepository<CallbackLog, UUID> {
  List<CallbackLog> findByExecutionIdOrderByAttemptNumberAsc(UUID executionId);

  List<CallbackLog> findByWebhookIdOrderByDeliveredAtDesc(UUID webhookId);
}
