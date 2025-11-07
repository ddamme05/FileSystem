-- V3.4: Create function and trigger for ai_jobs table
-- Auto-update updated_at timestamp on row updates

CREATE OR REPLACE FUNCTION update_ai_jobs_updated_at()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ai_jobs_updated_at_trigger
    BEFORE UPDATE
    ON ai_jobs
    FOR EACH ROW
EXECUTE FUNCTION update_ai_jobs_updated_at();
