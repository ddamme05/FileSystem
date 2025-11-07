package org.ddamme.service.ai;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net for best-effort job creation pattern.
 * Scans for files without OCR jobs and creates them.
 * 
 * Pattern: Best-Effort with Reconciler
 * - Upload commits before job creation (after-commit hook)
 * - If job creation fails, reconciler backfills missing jobs
 * - Runs hourly to close the gap
 * 
 * See: docs/BEST_EFFORT_PATTERN.md
 * See: cursor_v2_rationale.md Section 1
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.ocr.reconciler.enabled", havingValue = "true", matchIfMissing = true)
public class OcrReconciler {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${ai.ocr.reconciler.lookback-days:30}")
    private int lookbackDays;

    /**
     * Reconciles missing OCR jobs for eligible files.
     * 
     * Uses NOT EXISTS anti-join (plans better than LEFT JOIN ... IS NULL).
     * Performance: UNIQUE constraint uq_ai_jobs_file_type provides index for lookups.
     * 
     * Logs at DEBUG level when created==0 to avoid hourly noise.
     */
    @Scheduled(cron = "${ai.ocr.reconciler.cron:0 15 * * * *}") // hourly @ :15
    public void reconcileMissingJobs() {
        try {
            // NOT EXISTS pattern for anti-join (better query plan)
            // PostgreSQL-safe interval parameterization: (? * INTERVAL '1 day')
            int created = jdbcTemplate.update("""
                INSERT INTO ai_jobs (user_id, file_id, job_type, job_status, priority, created_at, updated_at)
                SELECT 
                    fm.user_id,
                    fm.id,
                    'OCR'::job_type,
                    'PENDING'::job_status,
                    5,
                    NOW(),
                    NOW()
                FROM file_metadata fm
                WHERE (fm.content_type = 'application/pdf' OR fm.content_type LIKE 'image/%')
                  AND fm.upload_timestamp >= NOW() - (? * INTERVAL '1 day')
                  AND NOT EXISTS (
                    SELECT 1 FROM ai_jobs aj
                    WHERE aj.file_id = fm.id AND aj.job_type = 'OCR'::job_type
                  )
                ON CONFLICT (file_id, job_type) DO NOTHING
                """, lookbackDays);

            // Log at INFO only when jobs created (DEBUG otherwise to avoid noise)
            if (created > 0) {
                log.info("OCR reconciliation created {} missing jobs (lookback: {} days)", created, lookbackDays);
            } else {
                log.debug("OCR reconciliation: no missing jobs found (lookback: {} days)", lookbackDays);
            }

            // Metrics: track created jobs and success
            meterRegistry.counter("ai.reconciler.jobs.created", "type", "OCR").increment(created);
            meterRegistry.counter("ai.reconciler.run", "result", "success").increment();
        } catch (Exception e) {
            log.error("OCR reconciliation failed", e);
            meterRegistry.counter("ai.reconciler.run", "result", "failure").increment();
            // Don't rethrow - allow next scheduled run to retry
        }
    }
}

