CREATE INDEX IF NOT EXISTS idx_file_metadata_user_uploaded
    ON file_metadata (user_id, upload_timestamp DESC);

