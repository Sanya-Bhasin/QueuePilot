package com.sanyabhasin.queuepilot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanyabhasin.queuepilot.domain.DeadLetter;
import com.sanyabhasin.queuepilot.domain.Execution;
import com.sanyabhasin.queuepilot.domain.ExecutionAttempt;
import com.sanyabhasin.queuepilot.domain.JobDefinition;
import com.sanyabhasin.queuepilot.messaging.RabbitMQPublisher;
import com.sanyabhasin.queuepilot.repository.DeadLetterRepository;
import com.sanyabhasin.queuepilot.repository.ExecutionAttemptRepository;
import com.sanyabhasin.queuepilot.repository.ExecutionRepository;
import com.sanyabhasin.queuepilot.repository.JobDefinitionRepository;
import com.sanyabhasin.queuepilot.repository.LeaseRepository;
import com.sanyabhasin.queuepilot.repository.WebhookEndpointRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

  @Mock private ExecutionRepository executionRepository;
  @Mock private JobDefinitionRepository jobDefinitionRepository;
  @Mock private ExecutionAttemptRepository executionAttemptRepository;
  @Mock private LeaseRepository leaseRepository;
  @Mock private DeadLetterRepository deadLetterRepository;
  @Mock private RabbitMQPublisher rabbitMQPublisher;
  @Mock private WebhookEndpointRepository webhookEndpointRepository;

  private ExecutionService service;

  @BeforeEach
  void setUp() {
    service =
        new ExecutionService(
            executionRepository,
            jobDefinitionRepository,
            executionAttemptRepository,
            leaseRepository,
            deadLetterRepository,
            rabbitMQPublisher,
            new RetryCalculator(),
            webhookEndpointRepository);
  }

  @Test
  void enqueueJobReturnsExistingExecutionForDuplicateIdempotencyKey() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    String idempotencyKey = "dedupe-1";
    Execution existing = new Execution(UUID.randomUUID(), tenantId, jobId, new ObjectMapper().nullNode());

    when(executionRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey))
        .thenReturn(Optional.of(existing));

    Execution result = service.enqueueJob(tenantId, jobId, new ObjectMapper().createObjectNode(), idempotencyKey);

    assertSame(existing, result);
    verify(executionRepository, never()).save(any(Execution.class));
    verify(rabbitMQPublisher, never()).publishExecution(any(Execution.class));
  }

  @Test
  void failExecutionMovesExecutionToDeadLetterWhenRetryIsExhausted() {
    UUID executionId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    Execution execution = new Execution(executionId, tenantId, jobId, new ObjectMapper().createObjectNode());
    execution.setAttemptCount(9);
    execution.setMaxAttempts(10);
    execution.setStatus("CLAIMED");

    when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));

    ExecutionAttempt attempt = new ExecutionAttempt(UUID.randomUUID(), executionId, 10, "TRANSIENT_FAILURE");
    JobExecutionResult.TransientFailure failure = new JobExecutionResult.TransientFailure("TIMEOUT", "request timed out");

    service.failExecution(executionId, attempt, failure);

    assertEquals("DEAD_LETTERED", execution.getStatus());
    verify(deadLetterRepository).save(any(DeadLetter.class));
    verify(leaseRepository).deleteByExecutionId(executionId);
  }

  @Test
  void succeedExecutionPublishesCallbacksForActiveWebhooks() {
    UUID executionId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    JsonNode result = new ObjectMapper().createObjectNode().put("ok", true);
    Execution execution = new Execution(executionId, tenantId, jobId, new ObjectMapper().createObjectNode());
    execution.setStatus("CLAIMED");

    when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
    when(webhookEndpointRepository.findByTenantIdAndIsActiveTrue(tenantId))
        .thenReturn(List.of(new com.sanyabhasin.queuepilot.domain.WebhookEndpoint(UUID.randomUUID(), tenantId, "https://example.com/callback", "secret")));

    service.succeedExecution(executionId, result);

    assertEquals("SUCCEEDED", execution.getStatus());
    verify(rabbitMQPublisher).publishCallback(
        eq(executionId),
        eq(tenantId),
        any(UUID.class),
        eq("https://example.com/callback"),
        eq("secret"),
        eq("SUCCEEDED"),
        eq(result));
  }
}
