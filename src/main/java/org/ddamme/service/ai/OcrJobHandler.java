package org.ddamme.service.ai;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import org.ddamme.database.model.AiJob;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.JobType;
import org.ddamme.database.repository.MetadataRepository;
import org.ddamme.service.StorageService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Job handler for OCR text extraction.
 *
 * Process:
 * 1. Fetch FileMetadata from database
 * 2. Download file from S3 to temp location
 * 3. Determine type (PDF vs image)
 * 4. Extract text using OcrService
 * 5. Save text to files.file_text column
 * 6. Store metadata (confidence, model version)
 * 7. Record metrics
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OcrJobHandler implements JobHandler {

    private final OcrService ocrService;
    private final MetadataRepository metadataRepository;
    private final StorageService storageService;
    private final MeterRegistry meterRegistry;

    private static final String OCR_MODEL_VERSION = "tesseract-5.x";
    
    // Fixed set of error types for metrics label hygiene (prevents cardinality explosion)
    private static final java.util.Set<String> VALID_ERROR_TYPES = java.util.Set.of(
        "pdf_encrypted",      // InvalidPasswordException
        "page_corrupt",       // Per-page OCR failure
        "oom_guard",          // OutOfMemoryError caught
        "s3_not_found",       // NoSuchKeyException
        "native_library_load_failed", // UnsatisfiedLinkError (JNA/Tesseract load failure)
        "unknown"             // Catch-all for unexpected errors
    );
    
    /**
     * Record error metric with normalized type (prevents cardinality explosion).
     */
    private void recordError(String errorType) {
        String normalizedType = VALID_ERROR_TYPES.contains(errorType) ? errorType : "unknown";
        meterRegistry.counter("ai.ocr.errors", "type", normalizedType).increment();
    }

    @Override
    public boolean supports(AiJob job) {
        return job.getJobType() == JobType.OCR;
    }

    @Override
    public void execute(AiJob job) throws Exception {
        Instant startTime = Instant.now();
        Path tempFile = null;

        try {
            // Guard 1: Check DB record still exists
            FileMetadata metadata = metadataRepository.findById(job.getFileId()).orElse(null);
            if (metadata == null) {
                log.warn("File {} deleted from DB before OCR started; marking job DONE-noop", job.getFileId());
                return; // No-op success (file deleted by user)
            }

            log.info("Processing OCR for file: id={}, name={}, type={}",
                    metadata.getId(), metadata.getOriginalFilename(), metadata.getContentType());

            // Download file to temp location (includes S3 existence check)
            tempFile = downloadToTemp(metadata);

            // Extract text based on content type
            OcrService.OcrResult result = extractText(tempFile, metadata.getContentType());

            // Save results to database
            saveOcrResults(metadata, result);

            // Store summary in job output
            Map<String, Object> output = new HashMap<>();
            output.put("text_length", result.text().length());
            output.put("page_count", result.pageCount());
            output.put("confidence", result.confidence());
            job.setOutputData(output);

            // Record metrics
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            Timer.builder("ai.ocr.duration")
                    .tag("type", isPdf(metadata.getContentType()) ? "pdf" : "image")
                    .register(meterRegistry)
                    .record(Duration.ofMillis(durationMs));

            meterRegistry.counter("ai.ocr.pages",
                    "type", isPdf(metadata.getContentType()) ? "pdf" : "image")
                    .increment(result.pageCount());

            log.info("OCR completed: fileId={}, textLength={}, pages={}, confidence={}, duration={}ms",
                    metadata.getId(), result.text().length(), result.pageCount(),
                    String.format("%.2f", result.confidence()), durationMs);

        } finally {
            // Cleanup temp file and directory
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Path tempDir = tempFile.getParent();
                    Files.delete(tempFile);
                    log.debug("Deleted temp file: {}", tempFile);

                    // Try to delete temp directory if empty
                    if (tempDir != null && Files.exists(tempDir)) {
                        try {
                            Files.delete(tempDir);
                            log.debug("Deleted temp directory: {}", tempDir);
                        } catch (IOException e) {
                            // Directory not empty or other issue - ignore
                            log.trace("Could not delete temp directory (may not be empty): {}", tempDir);
                        }
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }
        }
    }

    /**
     * Download file from S3 to temporary location.
     * 
     * Guard 2: S3 existence check via NoSuchKeyException.
     * If file missing from S3, throws exception to move job to DLQ.
     */
    private Path downloadToTemp(FileMetadata metadata) throws IOException {
        Path tempDir = Files.createTempDirectory("ocr-");

        // Extract extension safely (avoid path traversal from originalFilename)
        String ext = "";
        if (metadata.getOriginalFilename() != null && metadata.getOriginalFilename().contains(".")) {
            ext = metadata.getOriginalFilename().substring(metadata.getOriginalFilename().lastIndexOf('.'));
        }

        // Use random temp file name with safe extension
        Path tempFile = Files.createTempFile(tempDir, "file-", ext);
        
        // Delete the file created by createTempFile() since AWS SDK expects it to not exist
        Files.delete(tempFile);

        log.debug("Downloading file to temp: {}", tempFile);
        
        try {
            storageService.downloadToFile(metadata.getStorageKey(), tempFile);
        } catch (NoSuchKeyException e) {
            log.warn("File {} missing from S3 (key: {}); cannot process OCR",
                     metadata.getId(), metadata.getStorageKey());
            throw new IllegalStateException("S3_NOT_FOUND: " + metadata.getStorageKey(), e);
        }

        return tempFile;
    }

    /**
     * Extract text based on content type.
     * For PDFs, first attempts native text extraction before falling back to OCR.
     */
    private OcrService.OcrResult extractText(Path file, String contentType) throws Exception {
        try {
            if (isPdf(contentType)) {
                // Try native text extraction first (huge CPU savings for digital PDFs)
                OcrService.OcrResult nativeText = tryPdfTextExtraction(file);
                if (nativeText != null) {
                    log.info("Using native PDF text extraction (skipping OCR): {} chars extracted",
                            nativeText.text().length());
                    return nativeText;
                }

                // Fall back to OCR for scanned PDFs
                log.debug("Native text extraction insufficient, using OCR");
                return ocrService.extractTextFromPdf(file);
            } else if (isImage(contentType)) {
                return ocrService.extractTextFromImage(file);
            } else {
                throw new IllegalArgumentException("Unsupported content type for OCR: " + contentType);
            }
        } catch (UnsatisfiedLinkError unsatisfiedLinkError) {
            // Native library load failure (likely tmpfs noexec or missing JNA dependencies)
            recordError("native_library_load_failed");
            log.error("OCR native library (JNA/Tesseract/Leptonica) failed to load. " +
                     "Check tmpfs has exec permissions, presence of libleptonica/libtesseract shared libraries, " +
                     "and JNA temp directory is accessible.", unsatisfiedLinkError);
            throw new RuntimeException("OCR native library failed to load: " + unsatisfiedLinkError.getMessage(),
                                     unsatisfiedLinkError);
        }
    }

    /**
     * Attempts to extract text from PDF using PDFBox (native text).
     * Returns null if insufficient text is found (scanned PDF).
     * 
     * Fast-fail for encrypted PDFs: throws exception to move job to DLQ.
     * 
     * Improved multi-threshold:
     * - ≥200 chars OR
     * - ≥120 non-whitespace chars OR
     * - ≥15 tokens (words)
     * 
     * This catches small invoices/receipts with condensed text or tables.
     *
     * @param file PDF file path
     * @return OcrResult with extracted text, or null if PDF appears to be scanned
     */
    private OcrService.OcrResult tryPdfTextExtraction(Path file) {
        // Single load: encryption check + text extraction folded together (saves I/O)
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            // Loader.loadPDF throws InvalidPasswordException for encrypted PDFs
            String extracted = new PDFTextStripper().getText(doc);

            if (extracted == null || extracted.isBlank()) {
                log.debug("PDF has no native text, will use OCR");
                return null;
            }

            // Multi-threshold: ≥200 chars OR ≥120 non-ws OR ≥15 tokens
            // Catches small invoices/forms with condensed text or tables
            String stripped = extracted.strip();
            String condensed = extracted.replaceAll("\\s+", "");
            String[] tokens = extracted.split("\\s+");
            int tokenCount = (int) java.util.Arrays.stream(tokens)
                    .filter(t -> t.matches("\\w+"))  // Only count word tokens
                    .count();
            
            boolean enoughNativeText = stripped.length() >= 200
                                    || condensed.length() >= 120
                                    || tokenCount >= 15;

            if (enoughNativeText) {
                log.info("Native text extraction successful: {} chars, {} non-ws, {} tokens", 
                         stripped.length(), condensed.length(), tokenCount);
                return new OcrService.OcrResult(
                        stripped,
                        doc.getNumberOfPages(),
                        1.0f // Perfect confidence for native text
                );
            }

            log.debug("PDF has insufficient native text ({} chars, {} non-ws, {} tokens), will use OCR",
                    stripped.length(), condensed.length(), tokenCount);
            return null;

        } catch (InvalidPasswordException e) {
            // Fast-fail: encrypted PDFs go straight to DLQ (no retry waste)
            log.error("PDF is password-protected, cannot extract text: {}", file.getFileName());
            recordError("pdf_encrypted");
            throw new IllegalStateException("PDF_ENCRYPTED: Password-protected PDFs not supported", e);
        } catch (Exception e) {
            log.warn("Native PDF text extraction failed, will fall back to OCR: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Save OCR results to database.
     */
    private void saveOcrResults(FileMetadata metadata, OcrService.OcrResult result) {
        metadata.setFileText(result.text());
        metadata.setOcrConfidence(result.confidence());

        // Set model version based on confidence
        // 1.0 = native PDF text extraction, < 1.0 = Tesseract OCR
        if (result.confidence() == 1.0f) {
            metadata.setOcrModelVersion("pdfbox-text");
        } else {
            metadata.setOcrModelVersion(OCR_MODEL_VERSION);
        }

        metadataRepository.save(metadata);

        log.debug("Saved OCR results for file {}", metadata.getId());
    }

    /**
     * Check if content type is PDF.
     * 
     * Uses startsWith to handle variants like:
     * - "application/pdf"
     * - "application/pdf; charset=binary"
     * - "application/pdf;charset=UTF-8"
     */
    private boolean isPdf(String contentType) {
        return contentType != null && contentType.startsWith("application/pdf");
    }

    private boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }
}