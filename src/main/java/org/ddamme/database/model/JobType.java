package org.ddamme.database.model;

/**
 * Types of AI jobs that can be processed.
 * Maps to PostgreSQL ENUM: job_type
 */
public enum JobType {
    /** Extract text from PDFs and images using OCR */
    OCR,

    /** Generate embeddings for semantic search (Phase 2) */
    EMBED,

    /** Scan for personally identifiable information (Phase 4) */
    PII_SCAN,

    /** Redact sensitive information from documents (Phase 5) */
    REDACT,

    /** Generate AI summaries of documents (Future) */
    SUMMARIZE
}

