-- V3.5: Add unique constraint for idempotent job creation
-- Prevents duplicate jobs for the same file + job type across ALL states
-- This complements the partial unique index (V3.3) which only covers PENDING/RUNNING states

-- Drop the partial unique index since we're replacing it with a full constraint
DROP INDEX IF EXISTS idx_ai_jobs_inflight_unique;

-- Add full unique constraint across all job states
-- This ensures one job per (file, type) regardless of status
ALTER TABLE ai_jobs
    ADD CONSTRAINT uq_ai_jobs_file_type UNIQUE (file_id, job_type);

-- Recreate index for query performance (no uniqueness enforcement)
-- This index supports fast job claiming queries
CREATE INDEX IF NOT EXISTS idx_ai_jobs_file_type_status
    ON ai_jobs (file_id, job_type, job_status);

-- Add covering index for efficient job claiming
-- Supports: WHERE job_status = 'PENDING' AND next_attempt_at <= NOW()
--           ORDER BY priority ASC, created_at ASC
CREATE INDEX IF NOT EXISTS idx_ai_jobs_claim
    ON ai_jobs (job_status, next_attempt_at, priority, created_at)
    WHERE job_status = 'PENDING';
