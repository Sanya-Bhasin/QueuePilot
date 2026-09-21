# QueuePilot Architecture

## System Overview

QueuePilot is a distributed job queue system that ensures reliable execution of asynchronous tasks. It combines PostgreSQL (persistent state), RabbitMQ (worker distribution), and Spring Boot (orchestration).

```
┌─────────────────────────────────────────────────────────────┐
│                     HTTP API Layer                           │
│  (ExecutionController, JobDefinitionController, etc.)        │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  Service Layer                               │
│  ExecutionService, JobDefinitionService, etc.                │
│  - Enqueue/claim/complete jobs                               │
│  - Manage retries and dead-letters                           │
│  - Multi-tenant isolation                                    │
└────┬──────────────────────────────────────┬────────────────┘
     │                                      │
     ▼                                      ▼
┌──────────────────┐              ┌─────────────────────────┐
│  PostgreSQL DB   │              │  RabbitMQ Broker        │
│                  │              │                         │
│ - Executions     │              │ - Job Queue (durable)   │
│ - Leases         │              │ - Callback Queue        │
│ - Dead-letters   │              │                         │
│ - History        │              │ Manual ACK, Persistent  │
└──────────────────┘              └────────┬────────────────┘
                                           │
                                           ▼
                                   ┌──────────────────┐
                                   │  JobWorker(s)    │
                                   │                  │
                                   │ - Consume msgs   │
                                   │ - Execute jobs   │
                                   │ - Update status  │
                                   │ - Handle errors  │
                                   └──────────────────┘
```

## Core Components

### ExecutionService (Business Logic)

Central orchestration of the execution lifecycle:

1. **enqueueJob()**: 
   - Validates job definition exists
   - Checks idempotency key (prevents duplicates)
   - Creates Execution record in DB
   - Publishes message to RabbitMQ
   - Returns execution reference

2. **claimExecution()**: 
   - Updates status to CLAIMED
   - Creates Lease record with TTL (crash recovery)

3. **succeedExecution()**: 
   - Updates status to SUCCEEDED
   - Deletes lease
   - Stores result

4. **failExecution() / permanentFailExecution()**: 
   - Records attempt details
   - Calculates next retry delay
   - Either schedules retry or moves to dead-letter

5. **moveToDeadLetter()**: 
   - Updates status to DEAD_LETTERED
   - Creates DeadLetter record for inspection
   - Logs reason

### JobWorker (Message Consumer)

Listens on RabbitMQ job queue and executes jobs:

1. **Receive message**: From RabbitMQ job queue (manual ACK mode)
2. **Load execution**: Fetch from database
3. **Claim job**: Create lease to signal "I'm working on this"
4. **Execute**: Call appropriate JobExecutor based on job type
5. **Handle result**: 
   - Success → mark SUCCEEDED, delete lease, ack message
   - Transient failure → failExecution() (will retry), ack message
   - Permanent failure → permanentFailExecution() (goes to dead-letter), ack message
6. **Error handling**: If something goes wrong, NACK message (redelivery)

### Retry Calculator

Implements exponential backoff strategy:

- **Formula**: delay = min(MAX_DELAY, BASE_DELAY * MULTIPLIER ^ attempts)
- **Progression**: 1s, 2s, 4s, 8s, 16s, 32s, 64s, 128s, 256s, 300s (capped)
- **Limits**: 
  - Max 10 attempts
  - Max age 24 hours (prevent infinite retries on old jobs)

Returns null (no retry) if limits exceeded.

### RabbitMQ Config & Publisher

Ensures at-least-once delivery:

- **Durable queues**: Survive broker restarts (persisted to disk)
- **Persistent messages**: DeliveryMode set to PERSISTENT
- **Publisher confirms**: RabbitTemplate validates publish success
- **Manual acknowledgment**: Workers explicitly ack only after state update

## Database Schema

### Core Tables

**tenants**
```
id: UUID PRIMARY KEY
api_key_hash: VARCHAR(256)
created_at: TIMESTAMPTZ
```
Multi-tenant boundary. Each request must specify tenant_id.

**job_definitions**
```
id: UUID PRIMARY KEY
tenant_id: UUID FOREIGN KEY (tenants)
name: VARCHAR(255)
version: INT
description: TEXT
is_active: BOOLEAN
created_at: TIMESTAMPTZ

UNIQUE(tenant_id, name, version)
```
Registered job types. Versioning allows rolling out new versions without breaking running jobs.

**executions**
```
id: UUID PRIMARY KEY
tenant_id: UUID FOREIGN KEY (tenants)
job_id: UUID FOREIGN KEY (job_definitions)
params: JSONB
status: VARCHAR(50)  -- PENDING, CLAIMED, SUCCEEDED, FAILED, DEAD_LETTERED
result: JSONB
attempt_count: INT
next_attempt_at: TIMESTAMPTZ
created_at: TIMESTAMPTZ
expires_at: TIMESTAMPTZ
last_error: VARCHAR(512)
idempotency_key: VARCHAR(255)
version: INT

UNIQUE(tenant_id, idempotency_key) NULLS NOT DISTINCT
INDEX on (tenant_id, status)
INDEX on (tenant_id, next_attempt_at)
```
Individual job execution instances. Each attempt increments attempt_count.

**leases**
```
id: UUID PRIMARY KEY
execution_id: UUID UNIQUE (prevents multiple workers)
worker_id: VARCHAR(255)
expires_at: TIMESTAMPTZ
created_at: TIMESTAMPTZ

FOREIGN KEY (execution_id) REFERENCES executions
```
Worker heartbeat. When worker claims job, creates lease with TTL (typically 30s). If worker crashes, lease expires and system can detect and retry.

**execution_attempts**
```
id: UUID PRIMARY KEY
execution_id: UUID
attempt_number: INT
status: VARCHAR(50)
error: VARCHAR(512)
duration_ms: BIGINT
created_at: TIMESTAMPTZ
```
Audit trail of each attempt (useful for debugging, not critical path).

**dead_letters**
```
id: UUID PRIMARY KEY
tenant_id: UUID FOREIGN KEY (tenants)
execution_id: UUID UNIQUE FOREIGN KEY (executions)
job_id: UUID
reason: VARCHAR(255)  -- PERMANENT_FAILURE_ERROR, TRANSIENT_FAILURE_LIMIT, etc.
replayed_execution_id: UUID  -- If replayed, links to new execution
created_at: TIMESTAMPTZ
```
Permanent failures stored for inspection. Operator can manually replay by creating new execution.

**webhook_endpoints** (not yet fully implemented)
```
id: UUID PRIMARY KEY
tenant_id: UUID FOREIGN KEY (tenants)
url: VARCHAR(2048)
secret: VARCHAR(256)  -- For HMAC-SHA256 signing
is_active: BOOLEAN
created_at: TIMESTAMPTZ
```

**callback_logs** (not yet fully implemented)
```
id: UUID PRIMARY KEY
execution_id: UUID
webhook_endpoint_id: UUID
attempt_count: INT
status: VARCHAR(50)
http_status: INT
created_at: TIMESTAMPTZ
```

## Design Patterns

### Idempotency

**Problem**: Duplicate HTTP requests → duplicate executions

**Solution**:
1. Client provides idempotency_key with request
2. API checks UNIQUE(tenant_id, idempotency_key) before creating
3. If key exists, return existing execution (idempotent)

**Code**: ExecutionService.enqueueJob() lines 52-69

### At-Least-Once Delivery

**Problem**: Messages can be lost if broker crashes or worker dies

**Solution**:
1. RabbitMQ durable queues (disk-persisted)
2. Persistent message delivery mode
3. Manual acknowledgment in workers
4. If worker crashes before ack, broker redelivers to another worker

**Result**: Jobs execute at least once (possibly more if worker crashes after execution but before ack)

**Code**: 
- RabbitMQConfig: Durable queue + persistent delivery
- JobWorker.processJob(): Manual ack at end

### Crash Recovery via Leases

**Problem**: Worker crashes while executing job

**Solution**:
1. Worker creates Lease when claiming job (TTL ~30s)
2. Lease recovery is handled by a scheduled repair task that requeues expired claimed jobs
3. If worker crashes, the lease expires and another worker may claim the job again
4. This project keeps lease renewal lightweight and local; it is designed for portfolio use and not a production-grade distributed lease coordinator

**Result**: Crashed job eventually retried and remains recoverable without losing the job record

### Exponential Backoff

**Problem**: Transient errors (network timeout, temp unavailable)

**Solution**: Retry with increasing delays (1s, 2s, 4s, ..., 300s max)

**Benefits**:
- Gives failing service time to recover
- Doesn't overwhelm with immediate retries
- Bounded by max attempts (10) and max age (24h)

### Dead-Letter Queue

**Problem**: Job permanently fails after all retries

**Solution**:
1. Transient failure → retry per backoff
2. Permanent failure → immediately to dead-letter
3. After retries exhausted → dead-letter
4. Operator inspects and manually replays if desired

**Result**: No job is silently lost; all failures tracked

### Multi-Tenant Isolation

**Pattern**: Tenant_id on every entity and query

**Enforcement**:
- API layer: Every endpoint validates X-Tenant-ID header
- Service layer: Every operation checks tenant_id
- Database layer: Indexes on (tenant_id, ...) for isolation

**Result**: Complete data isolation between customers

## Message Flow Example

### Happy Path

```
1. Client POST /api/executions { jobId, params, idempotencyKey }
   └─> ExecutionController.enqueueJob()

2. ExecutionService.enqueueJob()
   ├─> Check idempotency key (UNIQUE constraint)
   ├─> Validate job definition exists
   ├─> Create Execution record (status: PENDING)
   ├─> Publish to RabbitMQ job queue
   └─> Return Execution { id, status: PENDING }

3. JobWorker consumes message from job queue
   ├─> Load Execution from DB
   ├─> Claim: create Lease (TTL 30s)
   ├─> Execute: call JobExecutor.execute(params)
   ├─> Receive Success result
   ├─> Update status to SUCCEEDED
   ├─> Delete lease
   ├─> Acknowledge message (remove from queue)
   └─> Done

4. Client can poll /api/executions/{id} to see status: SUCCEEDED
```

### Transient Failure Path

```
1. [Same as above until execution]

2. JobExecutor.execute() returns TransientFailure("TIMEOUT", "...")

3. JobWorker.processJob()
   ├─> Call ExecutionService.failExecution()
   ├─> Update attempt_count
   ├─> Calculate next retry delay (1s, 2s, 4s, ...)
   ├─> Update status: PENDING
   ├─> Set next_attempt_at: now + delay
   ├─> Republish to RabbitMQ
   ├─> Acknowledge message
   └─> Job will be retried after delay

4. [Broker holds message until next_attempt_at, then redelivers to worker]
```

### Permanent Failure Path

```
1. JobExecutor.execute() returns PermanentFailure("INVALID_INPUT", "...")

2. JobWorker.processJob()
   ├─> Call ExecutionService.permanentFailExecution()
   ├─> Move to dead-letter
   ├─> Status: DEAD_LETTERED
   ├─> Create DeadLetter record
   ├─> Acknowledge message
   └─> Job ends here

3. Operator inspects dead_letters table
   ├─> See reason: INVALID_INPUT
   ├─> Optionally replay: ExecutionService.replayDeadLetter()
   └─> Creates new execution with same params
```

## Scalability Considerations

### Horizontal Scaling

**Workers**: Add more JobWorker instances
- All consume from same RabbitMQ queue
- Broker distributes messages across workers
- Leases prevent duplicate execution (one lease per execution)

**RabbitMQ**: 
- Single broker in development
- Production: clustered RabbitMQ for HA
- Durable queues survive node failures

**PostgreSQL**:
- Single database (source of truth)
- All reads/writes go through single node
- Scaling: read replicas for reporting, primary for transactional writes

### Performance

**Indexing**:
- (tenant_id, status) for fast status filtering
- (tenant_id, next_attempt_at) for fast retry scanning
- (tenant_id, idempotency_key) for deduplication

**Batching**:
- Hibernate batch_size: 25
- Fetch size: 50

**Connection pooling**:
- Hikari pool: max 20, min 5

## Failure Scenarios

### Worker Crashes

**Before ACK**:
- Message remains in queue
- Broker redelivers to another worker
- Job retried

**After ACK but before DB update**:
- Message removed from queue
- Job may have executed, but DB update is lost
- Job status remains PENDING indefinitely (unless lease cleanup runs)

**Solution**: Leases with TTL detection + periodic cleanup (not yet implemented)

### RabbitMQ Broker Crashes

- Durable queues survive (persisted to disk)
- Worker connections drop
- On restart, unacked messages redelivered
- Workers reconnect and resume

### PostgreSQL Crashes

- All persistent state lost if no backup/replication
- RabbitMQ messages may still be in queue
- On recovery, workers will try to process messages but find no execution record
- Those messages will eventually be dead-lettered

### Network Partition

- Workers cannot reach RabbitMQ/DB
- Messages stuck in queue until network heals
- On recovery, retries resume

## Trade-Offs & Decisions

### At-Least-Once vs Exactly-Once

**Chosen**: At-least-once delivery

**Why**: 
- Exactly-once requires distributed transactions (complex)
- At-least-once + idempotent jobs is simpler and sufficient
- Most real-world job systems use at-least-once

### PostgreSQL as Source of Truth

**Why**:
- Durable, transactional, ACID
- Message queue (RabbitMQ) is best-effort, not reliable
- All reconciliation and replay flows through DB

### Exponential Backoff Limits

- Max 10 attempts: prevents infinite retries
- Max 24 hours: prevents zombie jobs from resurfacing
- Max delay 300s: prevents accumulation of delayed jobs

**Alternative**: Configurable per job type (future improvement)

### No Distributed Transactions

**Why**: 
- Complex to implement
- Performance cost
- Instead: rely on PostgreSQL transactions + RabbitMQ durability

### Manual Acknowledgment in Workers

**Why**: 
- Ensures message removed only after state update
- Tradeoff: code must explicitly ack/nack

**Alternative**: Auto-ack (simpler but less reliable)

## Future Improvements

1. **Lease renewal**: Worker background thread extending lease TTL while working
2. **Fair-share scheduling**: Limit concurrent executions per tenant per job type
3. **Webhook delivery**: Full retry logic for callback delivery
4. **Rate limiting**: Redis-backed token bucket per destination
5. **Tracing**: OpenTelemetry span propagation across services
6. **Cron scheduling**: Full CRON support with timezone awareness
7. **Metrics/Dashboard**: Real-time job monitoring
8. **Dead-letter replay**: Bulk replay with validation
9. **Job dependencies**: DAG execution (job A → job B → job C)
10. **Priority queues**: High-priority jobs ahead of standard jobs
