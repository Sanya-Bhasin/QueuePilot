package com.sanyabhasin.queuepilot.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanyabhasin.queuepilot.domain.CallbackLog;
import com.sanyabhasin.queuepilot.repository.CallbackLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.rabbitmq.client.Channel;

/** Delivers signed callbacks and records every attempt for operator inspection. */
@Component
public class CallbackDeliveryWorker {
  private final ObjectMapper mapper;
  private final RestTemplate restTemplate;
  private final CallbackLogRepository logs;

  public CallbackDeliveryWorker(
      ObjectMapper mapper, RestTemplate restTemplate, CallbackLogRepository logs) {
    this.mapper = mapper;
    this.restTemplate = restTemplate;
    this.logs = logs;
  }

  @RabbitListener(queues = "queuepilot.callbacks", ackMode = "MANUAL")
  public void deliver(
      @Payload String payload,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
      Channel channel) throws Exception {
    JsonNode body = mapper.readTree(payload);
    UUID executionId = UUID.fromString(body.get("execution_id").asText());
    UUID webhookId = UUID.fromString(body.get("webhook_id").asText());
    String secret = body.get("secret").asText();
    String serialized = mapper.writeValueAsString(body.get("result"));
    String signature = sign(serialized, secret);
    int attempt = 1;
    long started = System.currentTimeMillis();
    CallbackLog log = new CallbackLog(UUID.randomUUID(), executionId, webhookId, attempt);
    try {
      var response =
          restTemplate.postForEntity(
              body.get("webhook_url").asText(),
              new org.springframework.http.HttpEntity<>(
                  serialized,
                  new org.springframework.http.HttpHeaders() {{
                    set("Content-Type", "application/json");
                    set("X-QueuePilot-Signature", signature);
                  }}),
              String.class);
      log.setHttpStatusCode(response.getStatusCode().value());
      log.setSuccess(response.getStatusCode().is2xxSuccessful());
      if (!log.getSuccess()) {
        log.setErrorMessage("Webhook returned non-success status");
      }
      channel.basicAck(deliveryTag, false);
    } catch (Exception exception) {
      log.setSuccess(false);
      log.setErrorMessage(exception.getMessage());
      channel.basicNack(deliveryTag, false, false);
    } finally {
      log.setDeliveredAt(Instant.now());
      log.setResponseTimeMs((int) (System.currentTimeMillis() - started));
      logs.save(log);
    }
  }

  private String sign(String payload, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }
}
