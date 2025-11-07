-- V3.3: Create indexes for ai_jobs table
-- All indexes in one transactional migration for atomicity

-- Basic indexes for foreign keys and lookups
CREATE INDEX IF NOT EXISTS idx_ai_jobs_user ON ai_jobs (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_jobs_file ON ai_jobs (file_id);
CREATE INDEX IF NOT EXISTS idx_ai_jobs_depends ON ai_jobs (depends_on_job_id) WHERE depends_on_job_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ai_jobs_locked ON ai_jobs (locked_by, locked_at) WHERE locked_by IS NOT NULL;

-- Partial unique index: Prevent duplicate jobs in PENDING/RUNNING state
CREATE UNIQUE INDEX IF NOT EXISTS idx_ai_jobs_inflight_unique
    ON ai_jobs (file_id, job_type)
    WHERE job_status IN ('PENDING', 'RUNNING');

-- Fast job claiming: Supports ORDER BY next_attempt_at, priority, id
-- Allows deferred retries to wait their turn naturally
CREATE INDEX idx_ai_jobs_pending
  ON ai_jobs (next_attempt_at, priority, id)
  WHERE job_status = 'PENDING';

-- Targeted index for reclaim query (findByJobStatusAndLockedAtBefore)
-- Supports: SELECT ... WHERE job_status = 'RUNNING' AND locked_at < ?
CREATE INDEX IF NOT EXISTS idx_ai_jobs_running_locked_at
    ON ai_jobs (locked_at)
    WHERE job_status = 'RUNNING';
