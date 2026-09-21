package com.sanyabhasin.queuepilot.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JobExecutionResult represents the outcome of a single job execution attempt. Job executors
 * must return one of these to indicate success, transient failure (retry), or permanent failure
 * (no retry).
 */
public sealed class JobExecutionResult permits JobExecutionResult.Success, JobExecutionResult.TransientFailure, JobExecutionResult.PermanentFailure {

  public static final class Success extends JobExecutionResult {
    private final JsonNode data;

    public Success(JsonNode data) {
      this.data = data;
    }

    public JsonNode getData() {
      return data;
    }

    public JsonNode data() {
      return data;
    }
  }

  public static final class TransientFailure extends JobExecutionResult {
    private final String errorCode;
    private final String message;

    public TransientFailure(String errorCode, String message) {
      this.errorCode = errorCode;
      this.message = message;
    }

    public String getErrorCode() {
      return errorCode;
    }

    public String getMessage() {
      return message;
    }

    public String errorCode() {
      return errorCode;
    }

    public String message() {
      return message;
    }
  }

  public static final class PermanentFailure extends JobExecutionResult {
    private final String errorCode;
    private final String message;

    public PermanentFailure(String errorCode, String message) {
      this.errorCode = errorCode;
      this.message = message;
    }

    public String getErrorCode() {
      return errorCode;
    }

    public String getMessage() {
      return message;
    }

    public String errorCode() {
      return errorCode;
    }

    public String message() {
      return message;
    }
  }
}
