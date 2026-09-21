package com.sanyabhasin.queuepilot.service;

import com.sanyabhasin.queuepilot.domain.Execution;
import com.sanyabhasin.queuepilot.domain.ExecutionAttempt;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * RetryCalculator determines whether an execution should be retried and calculates the next
 * retry delay using exponential backoff.
 *
 * Formula: delay = min(max_delay, base_delay * (multiplier ^ attempt_count))
 *
 * Two limits enforce stop conditions:
 * 1. max_attempts: execution will not retry after this many attempts
 * 2. max_age_seconds: execution will not retry if older than this duration (default 24 hours)
 *
 * WHY: Exponential backoff prevents overwhelming a temporarily-unavailable service. The max_age
 * limit ensures we don't retry jobs indefinitely; after a day, an execution is considered
 * "stale" and is moved to dead-letter if it hasn't succeeded.
 */
@Component
public class RetryCalculator {

  private static final int DEFAULT_BASE_DELAY_SECONDS = 1;
  private static final double DEFAULT_MULTIPLIER = 2.0;
  private static final int DEFAULT_MAX_DELAY_SECONDS = 300; // 5 minutes
  private static final int DEFAULT_MAX_AGE_SECONDS = 86400; // 24 hours

  /**
   * Determines if an execution should be retried and returns the next retry delay.
   *
   * @param execution the execution to evaluate
   * @param attempt the most recent attempt
   * @return the delay before next retry, or null if no retry should occur
   */
  public Duration calculateNextRetryDelay(Execution execution, ExecutionAttempt attempt) {
    // If max attempts exceeded, no retry
    if (execution.getAttemptCount() >= execution.getMaxAttempts()) {
      return null;
    }

    // If execution is too old, no retry
    long ageSeconds = Duration.between(execution.getCreatedAt(), Instant.now()).getSeconds();
    if (ageSeconds > DEFAULT_MAX_AGE_SECONDS) {
      return null;
    }

    // Calculate backoff: base * (multiplier ^ attemptCount)
    long delaySeconds = Math.min(
        DEFAULT_MAX_DELAY_SECONDS,
        (long) (DEFAULT_BASE_DELAY_SECONDS * Math.pow(DEFAULT_MULTIPLIER, execution.getAttemptCount()))
    );

    return Duration.ofSeconds(delaySeconds);
  }

  /**
   * Get the configured maximum age for an execution. After this duration, no retries occur.
   */
  public Duration getMaxAge() {
    return Duration.ofSeconds(DEFAULT_MAX_AGE_SECONDS);
  }
}
