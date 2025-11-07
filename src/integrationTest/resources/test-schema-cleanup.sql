-- Clean up test data after each integration test
-- Executed by @Sql(executionPhase = AFTER_TEST_METHOD)

-- Delete in correct order to respect foreign key constraints
DELETE FROM ai_jobs;
DELETE FROM file_metadata;
DELETE FROM users;

-- Reset sequences if needed (optional, but ensures clean state)
-- Note: This is safe for test databases only

