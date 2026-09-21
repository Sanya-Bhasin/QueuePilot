package com.sanyabhasin.queuepilot.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanyabhasin.queuepilot.domain.Execution;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQPublisher handles publishing execution tasks to the job queue for worker consumption.
 */
@Component
public class RabbitMQPublisher {

  private static final Logger logger = LoggerFactory.getLogger(RabbitMQPublisher.class);

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  public RabbitMQPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Publish an execution to the job queue. The worker will consume this message,
   * execute the job, and acknowledge or reject based on outcome.
   */
  public void publishExecution(Execution execution) {
    try {
      // Create a message payload containing the execution ID and tenant ID
      JsonNode payload = objectMapper.createObjectNode()
          .put("execution_id", execution.getId().toString())
          .put("tenant_id", execution.getTenantId().toString())
          .put("job_id", execution.getJobId().toString())
          .put("attempt_count", execution.getAttemptCount());

      byte[] body = objectMapper.writeValueAsBytes(payload);

      MessageProperties props = new MessageProperties();
      props.setContentType("application/json");
      props.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE); // Persistent
      props.setHeader("X-Execution-ID", execution.getId().toString());
      props.setHeader("X-Tenant-ID", execution.getTenantId().toString());

      Message message = new Message(body, props);

      rabbitTemplate.convertAndSend(
          com.sanyabhasin.queuepilot.config.RabbitMQConfig.DIRECT_EXCHANGE_NAME,
          "job",
          message
      );

      logger.debug("Published execution {} to job queue", execution.getId());
    } catch (Exception e) {
      logger.error("Failed to publish execution {} to RabbitMQ", execution.getId(), e);
      throw new RuntimeException("Failed to publish execution", e);
    }
  }

  /**
   * Publish a callback delivery task to the callback queue.
   */
  public void publishCallback(UUID executionId, UUID tenantId, String webhookUrl, JsonNode result) {
    publishCallback(executionId, tenantId, null, webhookUrl, null, "SUCCEEDED", result);
  }

  public void publishCallback(
      UUID executionId,
      UUID tenantId,
      UUID webhookId,
      String webhookUrl,
      String secret,
      String status,
      JsonNode result) {
    try {
      JsonNode payload = objectMapper.createObjectNode()
          .put("execution_id", executionId.toString())
          .put("tenant_id", tenantId.toString())
          .put("webhook_url", webhookUrl)
          .put("webhook_id", webhookId == null ? null : webhookId.toString())
          .put("secret", secret)
          .put("status", status)
          .set("result", result);

      byte[] body = objectMapper.writeValueAsBytes(payload);

      MessageProperties props = new MessageProperties();
      props.setContentType("application/json");
      props.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE);

      Message message = new Message(body, props);

      rabbitTemplate.convertAndSend(
          com.sanyabhasin.queuepilot.config.RabbitMQConfig.DIRECT_EXCHANGE_NAME,
          "callback",
          message
      );

      logger.debug("Published callback for execution {} to callback queue", executionId);
    } catch (Exception e) {
      logger.error("Failed to publish callback for execution {}", executionId, e);
      throw new RuntimeException("Failed to publish callback", e);
    }
  }
}
