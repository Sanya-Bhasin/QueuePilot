package com.sanyabhasin.queuepilot.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanyabhasin.queuepilot.service.JobExecutionResult;

/**
 * JobExecutor is the interface that custom job implementations must implement.
 * Each job type (e.g., webhook calls, data processing) has a corresponding executor
 * that knows how to process that job type.
 */
public interface JobExecutor {

  /**
   * Execute a job with the given parameters. The executor must return one of:
   * - Success: job completed successfully
   * - TransientFailure: temporary error that warrants retry (e.g., network timeout)
   * - PermanentFailure: permanent error that should not be retried (e.g., invalid input)
   *
   * WHY sealed result class: Forces exhaustiveness at compile time. Every code path
   * must explicitly handle all three outcomes, preventing silent failures.
   */
  JobExecutionResult execute(JsonNode params);

  /**
   * Return the job type this executor handles.
   */
  String getJobType();
}
