package com.sanyabhasin.queuepilot.service;

import com.sanyabhasin.queuepilot.domain.Execution;
import com.sanyabhasin.queuepilot.messaging.RabbitMQPublisher;
import com.sanyabhasin.queuepilot.repository.ExecutionRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges persisted retry timing to RabbitMQ. Keeping nextAttemptAt in PostgreSQL makes restart
 * recovery deterministic; a broker message is only emitted once the durable time is due.
 */
@Service
public class RetryMonitor {
  private final ExecutionRepository executionRepository;
  private final RabbitMQPublisher publisher;

  public RetryMonitor(ExecutionRepository executionRepository, RabbitMQPublisher publisher) {
    this.executionRepository = executionRepository;
    this.publisher = publisher;
  }

  @Scheduled(fixedDelayString = "${queuepilot.retry.poll-interval-ms:1000}")
  @Transactional
  public void publishDueRetries() {
    for (Execution execution :
        executionRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            "PENDING", Instant.now())) {
      publisher.publishExecution(execution);
      execution.setNextAttemptAt(null);
      executionRepository.save(execution);
    }
  }
}
