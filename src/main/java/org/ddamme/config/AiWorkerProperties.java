package org.ddamme.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration properties for AI worker system.
 * Prefix: ai.worker
 *
 * Allows toggling workers on/off, tuning thread pools, and controlling OCR behavior.
 */
@Configuration
@ConfigurationProperties(prefix = "ai.worker")
@Data
public class AiWorkerProperties {

    /** Enable/disable AI workers (allows web-only instances) */
    private boolean enabled = true;

    /** Thread pool core size - should match vCPU count */
    private int coreThreads = 2;

    /** Thread pool max size - allows burst capacity */
    private int maxThreads = 4;

    /** Thread pool queue capacity */
    private int queueCapacity = 100;

    /** Number of jobs to claim per poll cycle */
    private int batchSize = 10;

    /** Milliseconds between job polls */
    private long pollInterval = 5000;

    /** Milliseconds between stuck job reclaim checks */
    private long reclaimInterval = 60000;

    /** Duration before job considered stuck */
    private Duration staleJobTimeout = Duration.ofMinutes(15);

    /** Maximum retry attempts before moving job to DLQ */
    private int maxAttempts = 3;

    /** OCR-specific configuration */
    private OcrConfig ocr = new OcrConfig();

    @Data
    public static class OcrConfig {
        /** Maximum pages to process per PDF (cost control) */
        private int maxPages = 50;

        /** Tesseract language (eng, fra, deu, spa, etc.) */
        private String language = "eng";

        /** Tesseract data path (tessdata directory) */
        private String dataPath = "/usr/share/tesseract-ocr/4.00/tessdata";
        
        /** Auto-create OCR jobs on file upload (for eligible file types) */
        private boolean autoCreate = true;
        
        /** File types eligible for OCR processing */
        private java.util.List<String> fileTypes = java.util.List.of("application/pdf", "image/*");
    }
}
