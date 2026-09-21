package com.sanyabhasin.queuepilot.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanyabhasin.queuepilot.domain.Execution;
import com.sanyabhasin.queuepilot.domain.ExecutionAttempt;
import com.sanyabhasin.queuepilot.domain.JobDefinition;
import com.sanyabhasin.queuepilot.repository.ExecutionRepository;
import com.sanyabhasin.queuepilot.repository.JobDefinitionRepository;
import com.sanyabhasin.queuepilot.service.ExecutionService;
import com.sanyabhasin.queuepilot.service.JobExecutionResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;

/**
 * Consumes durable job messages. Manual acknowledgement is intentional: a message remains
 * recoverable if the worker fails before the database transition is committed.
 */
@Component
public class JobWorker {
  private static final Logger logger = LoggerFactory.getLogger(JobWorker.class);

  private final ExecutionService executionService;
  private final ExecutionRepository executionRepository;
  private final JobDefinitionRepository jobDefinitionRepository;
  private final List<JobExecutor> executors;
  private final ObjectMapper objectMapper;

  public JobWorker(
      ExecutionService executionService,
      ExecutionRepository executionRepository,
      JobDefinitionRepository jobDefinitionRepository,
      List<JobExecutor> executors,
      ObjectMapper objectMapper) {
    this.executionService = executionService;
    this.executionRepository = executionRepository;
    this.jobDefinitionRepository = jobDefinitionRepository;
    this.executors = executors;
    this.objectMapper = objectMapper;
  }

  @RabbitListener(
      queues = "queuepilot.jobs",
      ackMode = "MANUAL")
  public void processJob(
      @Payload String payload,
      Message message,
      @Header("X-Execution-ID") String executionIdHeader,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
      Channel channel) throws Exception {
    UUID executionId = UUID.fromString(executionIdHeader);
    try {
      Execution execution =
          executionRepository
              .findById(executionId)
              .orElseThrow(() -> new IllegalArgumentException("Execution not found"));
      if (!"PENDING".equals(execution.getStatus())) {
        channel.basicAck(deliveryTag, false);
        return;
      }

      JobDefinition definition =
          jobDefinitionRepository
              .findById(execution.getJobId())
              .orElseThrow(() -> new IllegalArgumentException("Job definition not found"));
      JobExecutor executor =
          executors.stream()
              .filter(candidate -> candidate.getJobType().equals(definition.getName()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "No executor registered for job type " + definition.getName()));

      String workerId = "worker-" + UUID.randomUUID();
      executionService.claimExecution(executionId, workerId, Instant.now().plusSeconds(30));
      Instant startedAt = Instant.now();
      JobExecutionResult result = executor.execute(execution.getParamsJson());
      ExecutionAttempt attempt =
          new ExecutionAttempt(
              UUID.randomUUID(), executionId, execution.getAttemptCount() + 1, "SUCCESS");
      attempt.setWorkerId(workerId);
      attempt.setEndedAt(Instant.now());
      attempt.setDurationMs((int) Duration.between(startedAt, Instant.now()).toMillis());

      if (result instanceof JobExecutionResult.Success success) {
        attempt.setStatus("SUCCESS");
        executionService.recordAttempt(executionId, attempt);
        executionService.succeedExecution(executionId, success.getData());
      } else if (result instanceof JobExecutionResult.TransientFailure failure) {
        attempt.setStatus("TRANSIENT_FAILURE");
        attempt.setErrorCode(failure.getErrorCode());
        attempt.setErrorMessage(failure.getMessage());
        executionService.recordAttempt(executionId, attempt);
        executionService.failExecution(executionId, attempt, failure);
      } else if (result instanceof JobExecutionResult.PermanentFailure failure) {
        attempt.setStatus("PERMANENT_FAILURE");
        attempt.setErrorCode(failure.getErrorCode());
        attempt.setErrorMessage(failure.getMessage());
        executionService.recordAttempt(executionId, attempt);
        executionService.permanentFailExecution(executionId, attempt, failure);
      }
      channel.basicAck(deliveryTag, false);
    } catch (Exception exception) {
      logger.error("Job processing failed for {}", executionId, exception);
      channel.basicNack(deliveryTag, false, true);
    }
  }
}
