-- V001__initial_schema.sql
-- QueuePilot initial database schema with all required tables

-- gen_random_uuid() and digest-based API-key setup require pgcrypto on a fresh database.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Tenants table
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    api_key_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_tenants_api_key_hash ON tenants(api_key_hash);
CREATE INDEX idx_tenants_active ON tenants(is_active);

-- Job definitions table
CREATE TABLE job_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    schema_json JSONB NOT NULL,
    version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, name, version)
);

CREATE INDEX idx_job_defs_tenant_active ON job_definitions(tenant_id, is_active);

-- Schedules table
CREATE TABLE schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES job_definitions(id) ON DELETE CASCADE,
    cron_expression VARCHAR(255),
    timezone VARCHAR(63) NOT NULL DEFAULT 'UTC',
    scheduled_for TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,
    last_run_at TIMESTAMPTZ,
    params_json JSONB,
    max_concurrent INT DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_schedules_next_run ON schedules(next_run_at) WHERE is_active = true;
CREATE INDEX idx_schedules_tenant ON schedules(tenant_id);

-- Executions table
CREATE TABLE executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES job_definitions(id) ON DELETE RESTRICT,
    schedule_id UUID REFERENCES schedules(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    params_json JSONB NOT NULL,
    result_json JSONB,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 10,
    next_attempt_at TIMESTAMPTZ,
    last_error VARCHAR(2048),
    expires_at TIMESTAMPTZ,
    idempotency_key VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, idempotency_key)
);

CREATE INDEX idx_executions_tenant_status ON executions(tenant_id, status);
CREATE INDEX idx_executions_next_attempt ON executions(next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_executions_job_status ON executions(job_id, status);
CREATE INDEX idx_executions_created_at ON executions(created_at DESC);

-- Leases table
CREATE TABLE leases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL UNIQUE REFERENCES executions(id) ON DELETE CASCADE,
    worker_id VARCHAR(255) NOT NULL,
    acquired_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_leases_expires_at ON leases(expires_at);
CREATE INDEX idx_leases_worker_id ON leases(worker_id);

-- Dead letters table
CREATE TABLE dead_letters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    execution_id UUID NOT NULL UNIQUE REFERENCES executions(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES job_definitions(id),
    reason VARCHAR(255) NOT NULL,
    error_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    replayed_execution_id UUID REFERENCES executions(id) ON DELETE SET NULL
);

CREATE INDEX idx_dead_letters_tenant ON dead_letters(tenant_id, created_at DESC);

-- Webhook endpoints table
CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    url VARCHAR(2048) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_endpoints_tenant ON webhook_endpoints(tenant_id);

-- Execution attempts table
CREATE TABLE execution_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    worker_id VARCHAR(255),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    error_message TEXT,
    duration_ms INT
);

CREATE INDEX idx_execution_attempts_execution ON execution_attempts(execution_id, attempt_number);

-- Callback logs table
CREATE TABLE callback_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    webhook_id UUID NOT NULL REFERENCES webhook_endpoints(id),
    attempt_number INT NOT NULL,
    delivered_at TIMESTAMPTZ NOT NULL,
    http_status_code INT,
    response_time_ms INT,
    success BOOLEAN NOT NULL,
    error_message TEXT
);

CREATE INDEX idx_callback_logs_execution ON callback_logs(execution_id);
CREATE INDEX idx_callback_logs_webhook ON callback_logs(webhook_id, delivered_at DESC);
