-- V3.2: Create ai_jobs table
-- Table creation with constraints (transactional DDL)

CREATE TABLE IF NOT EXISTS ai_jobs
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_id           BIGINT      NOT NULL REFERENCES file_metadata (id) ON DELETE CASCADE,
    job_type          job_type    NOT NULL,
    job_status        job_status  NOT NULL DEFAULT 'PENDING',
    priority          INT         NOT NULL DEFAULT 5, -- Lower number = higher priority (1-10)

    -- Retry logic
    attempts          INT         NOT NULL DEFAULT 0,
    max_attempts      INT         NOT NULL DEFAULT 3,
    next_attempt_at   TIMESTAMPTZ,

    -- Worker coordination
    locked_by         VARCHAR(255),                   -- Worker instance ID
    locked_at         TIMESTAMPTZ,

    -- Job dependencies (for chaining)
    depends_on_job_id BIGINT      REFERENCES ai_jobs (id) ON DELETE SET NULL,

    -- Input/output data
    input_params      JSONB,
    output_data       JSONB,

    -- Error tracking
    error_message     TEXT,

    -- Timestamps
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ
);
