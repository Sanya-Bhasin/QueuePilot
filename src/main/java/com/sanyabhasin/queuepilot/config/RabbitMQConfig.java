package com.sanyabhasin.queuepilot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration declares queues, exchanges, and bindings for the QueuePilot system.
 *
 * WHY durable queues and persistent messages: If the broker crashes, messages survive
 * because they are persisted to disk. If a worker crashes before acknowledging, the message
 * is redelivered to another worker. This gives us at-least-once delivery semantics.
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

  // Queue and exchange names
  public static final String JOB_QUEUE_NAME = "queuepilot.jobs";
  public static final String CALLBACK_QUEUE_NAME = "queuepilot.callbacks";
  public static final String DELAY_EXCHANGE_NAME = "queuepilot.delay";
  public static final String DIRECT_EXCHANGE_NAME = "queuepilot.direct";

  /**
   * Main job execution queue. Durable so messages survive broker restart.
   * Messages are only removed when explicitly acknowledged by worker.
   */
  @Bean
  public Queue jobQueue() {
    return QueueBuilder.durable(JOB_QUEUE_NAME)
        .maxLengthBytes(1_000_000_000) // 1GB limit to prevent memory issues
        .build();
  }

  /**
   * Callback delivery queue for webhook result deliveries.
   */
  @Bean
  public Queue callbackQueue() {
    return QueueBuilder.durable(CALLBACK_QUEUE_NAME)
        .build();
  }

  /**
   * Direct exchange for standard message routing.
   */
  @Bean
  public DirectExchange directExchange() {
    return new DirectExchange(DIRECT_EXCHANGE_NAME, true, false);
  }

  /**
   * Bind job queue to direct exchange with routing key "job".
   */
  @Bean
  public Binding jobBinding(Queue jobQueue, DirectExchange directExchange) {
    return BindingBuilder.bind(jobQueue)
        .to(directExchange)
        .with("job");
  }

  /**
   * Bind callback queue to direct exchange with routing key "callback".
   */
  @Bean
  public Binding callbackBinding(Queue callbackQueue, DirectExchange directExchange) {
    return BindingBuilder.bind(callbackQueue)
        .to(directExchange)
        .with("callback");
  }

  /**
   * RabbitTemplate for publishing messages. Auto-configured but we customize to ensure
   * confirm and return callbacks are enabled for reliability.
   */
  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMandatory(true);
    return template;
  }
}
