# QueuePilot Learning Notes

This document explains the key engineering concepts in QueuePilot that you'll need to understand for interviews or deeper study.

## At-Least-Once Delivery

**What it means**: Every job is executed at least once, possibly more.

**Why we chose it**: Guarantees no jobs are silently lost. Better to retry a job twice than lose it entirely.

**How QueuePilot implements it**:
1. RabbitMQ uses **durable queues** - messages survive broker restarts (persisted to disk)
2. Workers use **manual acknowledgment** - messages are only removed from queue after successful execution
3. If a worker crashes before acknowledging, RabbitMQ redelivers the message to another worker

**Code locations**:
- `RabbitMQConfig`: Declares durable queues
- `JobWorker.processJob()`: Manual ACK in line `channel.basicAck(envelope.getDeliveryTag(), false)`
- `RabbitMQConfig.rabbitTemplate()`: Sets `mandatory(true)` for failure callbacks

**Interview question you might get**: "How do you guarantee messages aren't lost?"
Answer: Durable queues (disk-backed) + manual acknowledgment + redelivery on worker failure.

---

## Idempotency and Deduplication

**Problem it solves**: 
- Client retries HTTP request → Duplicate execution created
- We only want to execute the job once, even with duplicate requests

**How QueuePilot implements it**:
1. **Database constraint**: UNIQUE(tenant_id, idempotency_key) on executions table
2. **Service logic**: `ExecutionService.enqueueJob()` checks if idempotency_key already exists
3. If exists, returns the original execution instead of creating a new one

```java
if (idempotencyKey != null) {
  var existing = executionRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
  if (existing.isPresent()) {
    return existing.get();  // Return existing, don't create duplicate
  }
}
```

**Why this works**:
- If two requests arrive simultaneously, database constraint prevents duplicate rows
- If first request creates execution and returns, second request sees it and returns same one
- Client sees consistent results

**Code location**: `ExecutionService.enqueueJob()` lines 52-69

**Interview question**: "How do you handle duplicate requests?"
Answer: Idempotency key with UNIQUE database constraint prevents duplicates at API and database layer.

---

## Worker Leases and Crash Recovery

**Problem it solves**: 
- Worker crashes while executing a job
- Message is acknowledged but job never finishes
- Job is lost forever (no retry)

**How QueuePilot implements it**:
1. Worker acquires a **lease** when claiming a job: `claimExecution()` creates Lease record
2. Lease has expiry time (TTL): typically 30 seconds
3. While working, worker doesn't extend lease (simplified version)
4. If worker crashes, lease expires
5. Lease table has no foreign key constraints → expired leases can be cleaned up
6. Job status remains CLAIMED but lease is expired → can be detected and retried

**Why this matters**:
- Prevents zombie jobs (stuck in CLAIMED forever)
- Without leases, crashed worker's jobs would hang indefinitely
- With leases, system can detect crash and recover

**Code locations**:
- `Lease` entity: TTL stored as `expiresAt` timestamp
- `ExecutionService.claimExecution()`: Creates lease when job starts
- Database schema: Lease has no ON DELETE CASCADE, only TTL cleanup

**Interview question**: "What happens if a worker crashes mid-execution?"
Answer: Job has a lease with TTL. When lease expires, system can detect worker crash and retry the job.

---

## Retry Strategy and Exponential Backoff

**Problem it solves**: 
- Network timeouts, temporary database unavailability
- Without retry, every transient error = permanent failure
- With naive retry, hammers failing service (thundering herd)

**QueuePilot's approach**:
1. **Exponential backoff**: delay = min(max_delay, base * multiplier ^ attempts)
   - Attempt 1: 1 second
   - Attempt 2: 2 seconds
   - Attempt 3: 4 seconds
   - ... up to 300 seconds (5 minutes)

2. **Max attempt limit**: 10 attempts maximum
3. **Max age limit**: Don't retry if job older than 24 hours

**Code location**: `RetryCalculator.calculateNextRetryDelay()`

```java
long delaySeconds = Math.min(
    DEFAULT_MAX_DELAY_SECONDS,
    (long) (DEFAULT_BASE_DELAY_SECONDS * Math.pow(DEFAULT_MULTIPLIER, attempt))
);
```

**Why this strategy**:
- Exponential backoff: gives failing service time to recover without overwhelming it
- Max attempt limit: prevents infinite retry loops
- Max age limit: prevents jobs from being retried 5 years later if database restart

**Interview question**: "How do you handle transient failures?"
Answer: Exponential backoff (1s, 2s, 4s, etc.) with 10 max attempts and 24-hour age limit.

---

## Dead-Letter Queues

**Problem it solves**:
- After all retries exhausted, job still fails
- Need to store it somewhere for human inspection
- Need to be able to replay it later

**How QueuePilot implements it**:
1. **Permanent failures** (application errors) immediately go to dead-letter
2. **Transient failures** retry per backoff strategy
3. When retries exhausted, move to dead-letter
4. Dead-letter table stores execution_id, job_id, reason
5. Later, operator can inspect and manually replay

**Code locations**:
- `ExecutionService.moveToDeadLetter()`: Creates DeadLetter record
- `ExecutionService.failExecution()`: Checks if retry possible, otherwise moves to dead-letter
- `DeadLetter` entity: Tracks original execution and replay status

**Interview question**: "What happens when a job permanently fails?"
Answer: Job is moved to dead-letter table for operator inspection and manual replay if needed.

---

## Transient vs Permanent Failures

**Why this distinction matters**: Determines whether to retry or give up.

**Transient Failure** (e.g., TIMEOUT, NETWORK_ERROR):
- Retryable: temporary network issue, service momentarily down
- Action: Schedule retry with exponential backoff

**Permanent Failure** (e.g., INVALID_INPUT, UNAUTHORIZED):
- Not retryable: data is fundamentally wrong or job logic failed
- Action: Move immediately to dead-letter, no retry

**Code location**: `JobExecutionResult` sealed class with three types:
```java
sealed interface JobExecutionResult {
  record Success(JsonNode data) implements JobExecutionResult {}
  record TransientFailure(String errorCode, String message) implements JobExecutionResult {}
  record PermanentFailure(String errorCode, String message) implements JobExecutionResult {}
}
```

**Why sealed class**: Forces exhaustiveness. Every code path must handle all three cases at compile time.

---

## Multi-Tenant Isolation

**Problem it solves**: Multiple customers share QueuePilot, must prevent data leakage.

**How QueuePilot implements it**:
1. **API level**: Every request includes X-Tenant-ID header
2. **Service level**: `ExecutionService` checks tenant_id for every operation
3. **Database level**: tenant_id on every table, with indexes for fast filtering

**Code locations**:
- `ExecutionController`: Extracts X-Tenant-ID from header
- `ExecutionService.enqueueJob()`: Validates tenant owns the job
- Database schema: Every table has tenant_id + index on (tenant_id, ...)

**Security model**:
- Each tenant can only see/modify their own jobs
- No tenant can accidentally see another's data
- No shared state between tenants except infrastructure

---

## Transaction Boundaries

**Key principle**: Database transactions ensure consistency of related changes.

**Example: EnqueueJob Transaction**:
```java
@Transactional
public Execution enqueueJob(...) {
  // 1. Check idempotency key (UNIQUE constraint)
  // 2. Create Execution record (INSERT)
  // 3. Publish to RabbitMQ (external, can fail)
  
  // ALL these happen together, or NONE
  // If DB insert succeeds but RabbitMQ publish fails, transaction rolls back
}
```

**Why it matters**:
- Prevents partially completed state
- Either job is fully enqueued or not at all
- Prevents data corruption

**Code locations**:
- `ExecutionService`: Every public method has @Transactional
- `RetryCalculator`: Stateless, no transaction needed
- `JobWorker.processJob()`: Note - no explicit @Transactional; each service call manages its own

---

## PostgreSQL as Source of Truth

**Architecture principle**: PostgreSQL is the single source of truth; everything else is derived.

**Why this matters**:
- RabbitMQ can lose messages (if not acknowledged)
- Redis can lose data (if not persisted)
- PostgreSQL is durable and consistent
- Reconciliation is possible if other systems fail

**Example execution flow**:
1. Client enqueues job → ExecutionService creates Execution record in DB → returns immediately
2. Service publishes to RabbitMQ → if fails, transaction rolls back, no DB record
3. Worker reads from RabbitMQ → updates Execution status in DB
4. Database has full audit trail; RabbitMQ just helps with worker distribution

**Code locations**:
- Every Service class uses repositories that update PostgreSQL
- RabbitMQ is auxiliary (worker distribution), not source of truth

---

## Concurrency Control Without Locks

**Pattern used**: Optimistic concurrency where possible, database constraints elsewhere.

**Example - Execution Status Transitions**:
- No row-level locks on execution during processing
- Instead, rely on database constraints (UNIQUE, CHECK)
- Leases prevent two workers from running same job simultaneously
- Status field enforces state machine

**Why this matters**:
- Avoids lock contention (better performance)
- Database constraints + leases still prevent race conditions
- Scales better with many workers

---

## Key Code Locations for Interview Prep

**Start here**:
1. `Execution.java` - Understand entity, status field
2. `ExecutionService.enqueueJob()` - Idempotency logic
3. `RetryCalculator.calculateNextRetryDelay()` - Retry strategy
4. `JobWorker.processJob()` - Worker loop, at-least-once semantics
5. `RabbitMQConfig` - Queue declarations, durability

**Then explore**:
1. Database schema (`V001__initial_schema.sql`)
2. `ExecutionService.failExecution()` - Transient failure handling
3. `ExecutionService.moveToDeadLetter()` - Dead-letter pattern
4. Repositories - Query patterns for multi-tenant filtering

**Advanced**:
1. Error handling and transaction rollback scenarios
2. Lease cleanup and crash recovery
3. At-least-once delivery guarantees

---

## Common Interview Questions and Answers

**Q: How do you ensure reliable job execution?**
A: Three layers: PostgreSQL (source of truth) + durable RabbitMQ queues + manual acknowledgment in workers. If worker crashes, message redelivered.

**Q: What happens with duplicate requests?**
A: Idempotency key + UNIQUE database constraint. Second request sees original execution and returns it, preventing duplicate.

**Q: How do you handle failures?**
A: Transient failures retry with exponential backoff (1s, 2s, 4s, etc.). Permanent failures go to dead-letter immediately. After 10 attempts or 24 hours, give up.

**Q: How does the system scale?**
A: Add more workers consuming from same RabbitMQ queue. PostgreSQL is single source of truth. Leases prevent duplicate execution.

**Q: What if a worker crashes?**
A: Lease expires (TTL). Another worker can detect crash and retry. System recovers automatically.

**Q: How is multi-tenancy enforced?**
A: tenant_id on every request, every query, every table. Cannot accidentally access another tenant's data.
