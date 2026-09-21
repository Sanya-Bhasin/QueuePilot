package com.sanyabhasin.queuepilot.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanyabhasin.queuepilot.service.JobExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * EchoJobExecutor is a simple test executor that echoes back the input parameters.
 * Used for testing and demonstration purposes.
 */
@Component
public class EchoJobExecutor implements JobExecutor {

  private static final Logger logger = LoggerFactory.getLogger(EchoJobExecutor.class);

  @Override
  public JobExecutionResult execute(JsonNode params) {
    logger.info("Executing echo job with params: {}", params);

    // Simulate some work
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new JobExecutionResult.TransientFailure("INTERRUPTED", "Job was interrupted");
    }

    return new JobExecutionResult.Success(params);
  }

  @Override
  public String getJobType() {
    return "echo";
  }
}
