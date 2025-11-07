-- V3.1: Create ENUM types for AI Jobs
-- Enums are non-transactional in PostgreSQL, so they get their own migration

-- Create job_type enum
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'job_type') THEN
            CREATE TYPE job_type AS ENUM ('OCR', 'EMBED', 'PII_SCAN', 'REDACT', 'SUMMARIZE');
        END IF;
    END
$$;

-- Create job_status enum
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'job_status') THEN
            CREATE TYPE job_status AS ENUM ('PENDING', 'RUNNING', 'DONE', 'FAILED', 'DLQ');
        END IF;
    END
$$;
