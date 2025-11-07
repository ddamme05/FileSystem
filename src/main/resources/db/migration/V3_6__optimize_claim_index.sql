-- V3.6: Optimize claim index to match ORDER BY exactly
-- Includes id ASC as final tie-breaker for deterministic ordering

-- Drop the old index
DROP INDEX IF EXISTS idx_ai_jobs_pending;

-- Create optimized index matching the claim query ORDER BY:
-- ORDER BY next_attempt_at ASC NULLS FIRST, priority ASC, created_at ASC, id ASC
CREATE INDEX IF NOT EXISTS idx_ai_jobs_claim
    ON ai_jobs (next_attempt_at, priority, created_at, id)
    WHERE job_status = 'PENDING';

-- Note: DDL literal 'PENDING' is fine here (no CAST needed in DDL)
-- This index enables efficient index-only scans for the claim query
-- The id column ensures deterministic ordering even when other columns tie

