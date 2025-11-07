-- V4: AI File Metadata - Extend files table with AI capabilities
-- Supports: OCR text, embeddings, PII detection, summaries, structured data
-- Features: Full-text search with weighted ranking (filename > text)

-- Add AI-related columns to files table
ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS file_text TEXT;
ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS file_structured_json JSONB;
ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS ai_summary TEXT;
ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS ai_keywords TEXT[];
ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS pii_level VARCHAR(20); -- none|low|medium|high|critical
ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS ocr_confidence REAL;
ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS ocr_model_version VARCHAR(50);
ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS embedding_model_version VARCHAR(50);

-- Full-text search with weighted ranking
-- Weight A (highest): filename matches
-- Weight B: text content matches
ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS search_vector tsvector
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(original_filename, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(file_text, '')), 'B')
            ) STORED;

-- Indexes for search and filtering
CREATE INDEX IF NOT EXISTS idx_files_search_vector ON file_metadata USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_files_user ON file_metadata (user_id);
CREATE INDEX IF NOT EXISTS idx_files_pii_level ON file_metadata (user_id, pii_level) WHERE pii_level IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_files_text_exists ON file_metadata (user_id) WHERE file_text IS NOT NULL;
