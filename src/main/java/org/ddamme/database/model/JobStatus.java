package org.ddamme.database.model;

/**
 * Lifecycle states for AI job processing.
 * Maps to PostgreSQL ENUM: job_status
 */
public enum JobStatus {
    /** Job created, waiting to be claimed by a worker */
    PENDING,

    /** Job claimed and currently being processed */
    RUNNING,

    /** Job completed successfully */
    DONE,

    /** Job failed, will retry if attempts remaining */
    FAILED,

    /** Dead Letter Queue - permanent failure after max retries */
    DLQ
}

