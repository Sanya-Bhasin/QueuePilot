package com.sanyabhasin.queuepilot.service;

import com.sanyabhasin.queuepilot.domain.Execution;
import com.sanyabhasin.queuepilot.domain.ExecutionAttempt;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RetryCalculatorTest {

  private final RetryCalculator retryCalculator = new RetryCalculator();

  @Test
  void testExponentialBackoffProgression() {
    Execution execution = createExecution(0);
    ExecutionAttempt attempt = new ExecutionAttempt(UUID.randomUUID(), execution.getId(), 1, "TIMEOUT");

    // First retry: 1 second
    Duration delay1 = retryCalculator.calculateNextRetryDelay(execution, attempt);
    assertEquals(Duration.ofSeconds(1), delay1);

    // Second retry: 2 seconds
    execution.setAttemptCount(1);
    delay1 = retryCalculator.calculateNextRetryDelay(execution, attempt);
    assertEquals(Duration.ofSeconds(2), delay1);

    // Third retry: 4 seconds
    execution.setAttemptCount(2);
    delay1 = retryCalculator.calculateNextRetryDelay(execution, attempt);
    assertEquals(Duration.ofSeconds(4), delay1);
  }

  @Test
  void testMaxDelayCapping() {
    Execution execution = createExecution(0);
    execution.setAttemptCount(20); // High attempt count to exceed max delay
    execution.setMaxAttempts(25);

    ExecutionAttempt attempt = new ExecutionAttempt(UUID.randomUUID(), execution.getId(), 21, "TIMEOUT");

    Duration delay = retryCalculator.calculateNextRetryDelay(execution, attempt);
    // Should be capped at 300 seconds (5 minutes)
    assertEquals(Duration.ofSeconds(300), delay);
  }

  @Test
  void testRetryCountLimit() {
    Execution execution = createExecution(0);
    execution.setAttemptCount(10); // 10 failed attempts

    ExecutionAttempt attempt = new ExecutionAttempt(UUID.randomUUID(), execution.getId(), 11, "TIMEOUT");

    Duration delay = retryCalculator.calculateNextRetryDelay(execution, attempt);
    assertNull(delay); // No more retries allowed
  }

  @Test
  void testMaxAgeLimit() {
    Execution execution = createExecution(0);
    // Execution created 25 hours ago
    execution.setCreatedAt(Instant.now().minus(Duration.ofHours(25)));
    execution.setAttemptCount(1);

    ExecutionAttempt attempt = new ExecutionAttempt(UUID.randomUUID(), execution.getId(), 2, "TIMEOUT");

    Duration delay = retryCalculator.calculateNextRetryDelay(execution, attempt);
    assertNull(delay); // Exceeded max age, no retry
  }

  private Execution createExecution(int initialAttempts) {
    Execution execution = new Execution(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        null
    );
    execution.setAttemptCount(initialAttempts);
    return execution;
  }
}
