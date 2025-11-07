package org.ddamme.service.ai;

import org.ddamme.database.model.AiJob;

/**
 * Interface for job type handlers.
 * Each AI job type (OCR, EMBED, PII_SCAN, etc.) implements this interface.
 *
 * Spring auto-discovers all JobHandler beans and routes jobs accordingly.
 */
public interface JobHandler {

    /**
     * Execute the job.
     *
     * @param job The job to process
     * @throws Exception on failure (triggers retry logic)
     */
    void execute(AiJob job) throws Exception;

    /**
     * Check if this handler supports the given job.
     *
     * @param job The job to check
     * @return true if this handler can process the job
     */
    boolean supports(AiJob job);
}

