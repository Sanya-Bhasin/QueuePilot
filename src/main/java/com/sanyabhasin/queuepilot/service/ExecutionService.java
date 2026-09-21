package com.sanyabhasin.queuepilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanyabhasin.queuepilot.domain.DeadLetter;
import com.sanyabhasin.queuepilot.domain.Execution;
import com.sanyabhasin.queuepilot.domain.ExecutionAttempt;
import com.sanyabhasin.queuepilot.domain.JobDefinition;
import com.sanyabhasin.queuepilot.domain.Lease;
import com.sanyabhasin.queuepilot.messaging.RabbitMQPublisher;
import com.sanyabhasin.queuepilot.repository.DeadLetterRepository;
import com.sanyabhasin.queuepilot.repository.ExecutionAttemptRepository;
import com.sanyabhasin.queuepilot.repository.ExecutionRepository;
import com.sanyabhasin.queuepilot.repository.JobDefinitionRepository;
import com.sanyabhasin.queuepilot.repository.LeaseRepository;
import com.sanyabhasin.queuepilot.repository.WebhookEndpointRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ExecutionService coordinates the core job execution workflow: enqueueing, tracking,
 * retrying, and moving to dead-letter. All execution transitions are transactional to maintain
 * consistency.
 *
 * WHY transactional: Multiple state changes must appear atomic. If enqueue fails after the
 * Execution record is created but before RabbitMQ publish, a redelivery of the HTTP request
 * would create a duplicate unless caught by the idempotency key.
 */
@Service
public class ExecutionService {

  private static final Logger logger = LoggerFactory.getLogger(ExecutionService.class);

  private final ExecutionRepository executionRepository;
  private final JobDefinitionRepository jobDefinitionRepository;
  private final ExecutionAttemptRepository executionAttemptRepository;
  private final LeaseRepository leaseRepository;
  private final DeadLetterRepository deadLetterRepository;
  private final RabbitMQPublisher rabbitMQPublisher;
  private final RetryCalculator retryCalculator;
  private final WebhookEndpointRepository webhookEndpointRepository;

  public ExecutionService(
      ExecutionRepository executionRepository,
      JobDefinitionRepository jobDefinitionRepository,
      ExecutionAttemptRepository executionAttemptRepository,
      LeaseRepository leaseRepository,
      DeadLetterRepository deadLetterRepository,
      RabbitMQPublisher rabbitMQPublisher,
      RetryCalculator retryCalculator,
      WebhookEndpointRepository webhookEndpointRepository
  ) {
    this.executionRepository = executionRepository;
    this.jobDefinitionRepository = jobDefinitionRepository;
    this.executionAttemptRepository = executionAttemptRepository;
    this.leaseRepository = leaseRepository;
    this.deadLetterRepository = deadLetterRepository;
    this.rabbitMQPublisher = rabbitMQPublisher;
    this.retryCalculator = retryCalculator;
    this.webhookEndpointRepository = webhookEndpointRepository;
  }

  /**
   * Enqueue a job for execution. If an idempotency key is provided and an execution with that
   * key already exists within the window, return the existing execution instead of creating a
   * duplicate.
   */
  @Transactional
  public Execution enqueueJob(UUID tenantId, UUID jobId, JsonNode params, String idempotencyKey) {
    // Check if idempotency key already exists
    if (idempotencyKey != null) {
      var existing = executionRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
      if (existing.isPresent()) {
        logger.info("Idempotency key {} already exists, returning existing execution", idempotencyKey);
        return existing.get();
      }
    }

    // Validate job definition exists
    JobDefinition jobDef = jobDefinitionRepository.findByIdAndTenantIdAndIsActiveTrue(jobId, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Job definition not found or inactive"));

    // Create execution record
    UUID executionId = UUID.randomUUID();
    Execution execution = new Execution(executionId, tenantId, jobId, params);
    execution.setIdempotencyKey(idempotencyKey);
    execution.setExpiresAt(Instant.now().plus(retryCalculator.getMaxAge()));

    execution = executionRepository.save(execution);
    logger.info("Created execution {} for job {}", executionId, jobId);

    // Publish to RabbitMQ for worker consumption
    rabbitMQPublisher.publishExecution(execution);

    return execution;
  }

  /**
   * Mark an execution as claimed by a worker. Creates a lease record.
   */
  @Transactional
  public void claimExecution(UUID executionId, String workerId, Instant leaseExpiry) {
    Execution execution = executionRepository.findById(executionId)
        .orElseThrow(() -> new IllegalArgumentException("Execution not found"));

    execution.setStatus("CLAIMED");
    executionRepository.save(execution);

    Lease lease = new Lease(UUID.randomUUID(), executionId, workerId, leaseExpiry);
    leaseRepository.save(lease);

    logger.debug("Claimed execution {} by worker {}", executionId, workerId);
  }

  /**
   * Mark an execution as succeeded. Updates status and records the result.
   */
  @Transactional
  public void succeedExecution(UUID executionId, JsonNode resultData) {
    Execution execution = executionRepository.findById(executionId)
        .orElseThrow(() -> new IllegalArgumentException("Execution not found"));

    execution.setStatus("SUCCEEDED");
    execution.setResultJson(resultData);
    execution.setUpdatedAt(Instant.now());
    executionRepository.save(execution);
    leaseRepository.deleteByExecutionId(executionId);
    webhookEndpointRepository.findByTenantIdAndIsActiveTrue(execution.getTenantId())
        .forEach(webhook ->
            rabbitMQPublisher.publishCallback(
                executionId, execution.getTenantId(), webhook.getId(), webhook.getUrl(),
                webhook.getSecret(), "SUCCEEDED", resultData));

    logger.info("Execution {} succeeded", executionId);
  }

  @Transactional
  public void recordAttempt(UUID executionId, ExecutionAttempt attempt) {
    executionAttemptRepository.save(attempt);
    Execution execution = executionRepository.findById(executionId)
        .orElseThrow(() -> new IllegalArgumentException("Execution not found"));
    execution.setAttemptCount(Math.max(execution.getAttemptCount(), attempt.getAttemptNumber()));
    execution.setUpdatedAt(Instant.now());
    executionRepository.save(execution);
  }

  /**
   * Handle a failed execution attempt. Determines if retry should occur or move to dead-letter.
   */
  @Transactional
  public void failExecution(UUID executionId, ExecutionAttempt attempt, JobExecutionResult.TransientFailure failure) {
    Execution execution = executionRepository.findById(executionId)
        .orElseThrow(() -> new IllegalArgumentException("Execution not found"));

    execution.setAttemptCount(execution.getAttemptCount() + 1);
    execution.setLastError(failure.getMessage());
    executionAttemptRepository.save(attempt);

    var retryDelay = retryCalculator.calculateNextRetryDelay(execution, attempt);

    if (retryDelay != null) {
      // Schedule retry
      execution.setStatus("PENDING");
      execution.setNextAttemptAt(Instant.now().plus(retryDelay));
      executionRepository.save(execution);
      logger.info("Execution {} scheduled for retry in {} seconds", executionId, retryDelay.getSeconds());

      // RetryMonitor publishes only after nextAttemptAt. Publishing here would defeat backoff.
    } else {
      // Move to dead-letter
      moveToDeadLetter(execution, "TRANSIENT_FAILURE_LIMIT");
    }

    leaseRepository.deleteByExecutionId(executionId);
  }

  /**
   * Handle a permanent failure. Immediately moves to dead-letter.
   */
  @Transactional
  public void permanentFailExecution(UUID executionId, ExecutionAttempt attempt, JobExecutionResult.PermanentFailure failure) {
    Execution execution = executionRepository.findById(executionId)
        .orElseThrow(() -> new IllegalArgumentException("Execution not found"));

    execution.setLastError(failure.getMessage());
    executionAttemptRepository.save(attempt);

    moveToDeadLetter(execution, "PERMANENT_FAILURE_ERROR");
    leaseRepository.deleteByExecutionId(executionId);
  }

  /**
   * Move an execution to dead-letter storage. This is the final resting place for executions
   * that cannot be retried.
   */
  @Transactional
  public void moveToDeadLetter(Execution execution, String reason) {
    execution.setStatus("DEAD_LETTERED");
    executionRepository.save(execution);

    DeadLetter deadLetter = new DeadLetter(
        UUID.randomUUID(),
        execution.getTenantId(),
        execution.getId(),
        execution.getJobId(),
        reason
    );
    deadLetterRepository.save(deadLetter);

    logger.warn("Execution {} moved to dead-letter: {}", execution.getId(), reason);
  }

  /**
   * Cancel a pending execution. Only works if the execution hasn't been claimed yet.
   */
  @Transactional
  public void cancelExecution(UUID tenantId, UUID executionId) {
    Execution execution = executionRepository.findByIdAndTenantId(executionId, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Execution not found"));

    if (!"PENDING".equals(execution.getStatus())) {
      throw new IllegalStateException("Cannot cancel execution that is not PENDING");
    }

    execution.setStatus("FAILED");
    executionRepository.save(execution);

    logger.info("Cancelled execution {}", executionId);
  }

  /**
   * Replay a dead-lettered execution as a new execution with the same parameters.
   */
  @Transactional
  public Execution replayDeadLetter(UUID tenantId, UUID deadLetterId) {
    DeadLetter deadLetter = deadLetterRepository.findByIdAndTenantId(deadLetterId, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Dead-letter not found"));

    Execution original = executionRepository.findById(deadLetter.getExecutionId())
        .orElseThrow(() -> new IllegalArgumentException("Original execution not found"));

    // Create new execution with same parameters
    Execution newExecution = enqueueJob(tenantId, deadLetter.getJobId(), original.getParamsJson(), null);

    // Mark dead-letter as replayed
    deadLetter.setReplayedExecutionId(newExecution.getId());
    deadLetterRepository.save(deadLetter);

    logger.info("Replayed dead-letter {} as new execution {}", deadLetterId, newExecution.getId());

    return newExecution;
  }
}
