-- V3_8: Consolidate claim indexes for optimal query performance
-- Date: October 25, 2025
-- Purpose: Remove redundant/suboptimal indexes, create single perfect-match claim index
--
-- Issue Analysis:
-- 1. idx_ai_jobs_file_type is redundant (UNIQUE constraint already has index)
-- 2. Two competing claim indexes with suboptimal column orders
-- 3. One index includes job_status in columns (redundant in partial index)
--
-- Solution: Single index that exactly matches ORDER BY in claimJobIds CTE

-- Drop redundant file_type index (covered by unique constraint)
DROP INDEX IF EXISTS idx_ai_jobs_file_type;

-- Drop old claim indexes
DROP INDEX IF EXISTS idx_ai_jobs_pending;
DROP INDEX IF EXISTS idx_ai_jobs_claim;

-- Create single optimal claim index
-- Matches: ORDER BY next_attempt_at ASC NULLS FIRST, priority ASC, created_at ASC, id ASC
-- Enables index-only scans and top-N heap optimization
CREATE INDEX IF NOT EXISTS idx_ai_jobs_claim_v2
    ON ai_jobs (next_attempt_at, priority, created_at, id)
    WHERE job_status = 'PENDING';

-- Performance notes:
-- - Partial index (WHERE job_status='PENDING') keeps index small
-- - Column order exactly matches query ORDER BY for index-only scans
-- - (next_attempt_at, priority, created_at, id) enables efficient top-N heap
-- - PostgreSQL can skip locked rows efficiently with SKIP LOCKED
--
-- Expected EXPLAIN plan:
--   -> Limit (LIMIT :batchSize)
--      -> LockRows
--         -> Index Scan using idx_ai_jobs_claim_v2 on ai_jobs
--            Index Cond: ((job_status = 'PENDING') AND ...)
--            Filter: (next_attempt_at IS NULL OR next_attempt_at <= now())



