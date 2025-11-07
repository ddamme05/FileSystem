-- V3_7: Supporting indexes (placeholder)
-- Date: October 24, 2025
-- Purpose: Originally planned for reconciler anti-join index
--
-- Note: This migration is now effectively a no-op because:
-- 1. idx_ai_jobs_file_type was redundant (unique constraint already has index)
-- 2. idx_ai_jobs_running_locked_at already created in V3_3
--
-- Migration kept for version continuity (Flyway requires sequential versions)
-- See V3_8 for index consolidation

-- No-op: Flyway requires non-empty migration
-- The unique constraint uq_ai_jobs_file_type (file_id, job_type) from V3_5
-- already provides the index needed for reconciler lookups

