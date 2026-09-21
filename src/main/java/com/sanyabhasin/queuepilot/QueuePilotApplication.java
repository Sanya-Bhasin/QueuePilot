package com.sanyabhasin.queuepilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * QueuePilot main application entry point.
 */
@SpringBootApplication
@EnableScheduling
public class QueuePilotApplication {

  public static void main(String[] args) {
    SpringApplication.run(QueuePilotApplication.class, args);
  }
}
