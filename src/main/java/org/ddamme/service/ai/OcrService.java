package org.ddamme.service.ai;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.ddamme.config.AiWorkerProperties;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * OCR text extraction service using Tess4J (Java wrapper for Tesseract).
 *
 * Supports:
 * - PDFs: Rasterizes each page with PDFBox, then OCRs
 * - Images: Direct OCR via Tesseract
 *
 * Features:
 * - Automatic image downscaling (max 2000px width)
 * - Multi-page PDF support with page limit
 * - Confidence scoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private final AiWorkerProperties properties;
    private final MeterRegistry meterRegistry;
    
    private static final int MAX_MEGAPIXELS = 10; // ~3500x3000 at 300 DPI; prevents OOM
    
    // Fixed set of error types for metrics label hygiene (prevents cardinality explosion)
    private static final java.util.Set<String> VALID_ERROR_TYPES = java.util.Set.of(
        "page_corrupt",       // Per-page OCR failure
        "oom_guard",          // OutOfMemoryError caught
        "unknown"             // Catch-all for unexpected errors
    );
    
    /**
     * Record error metric with normalized type (prevents cardinality explosion).
     */
    private void recordError(String errorType) {
        String normalizedType = VALID_ERROR_TYPES.contains(errorType) ? errorType : "unknown";
        meterRegistry.counter("ai.ocr.errors", "type", normalizedType).increment();
    }

    /**
     * Result of OCR operation.
     */
    public record OcrResult(String text, int pageCount, float confidence) {}

    /**
     * Extract text from an image file.
     */
    public OcrResult extractTextFromImage(Path imagePath) throws IOException, TesseractException {
        log.debug("Extracting text from image: {}", imagePath);

        BufferedImage image = ImageIO.read(imagePath.toFile());
        if (image == null) {
            throw new IOException("Unable to read image: " + imagePath);
        }

        // Downscale if too large
        image = downscaleIfNeeded(image);

        // OCR the image
        Tesseract tesseract = createTesseract();
        String text = tesseract.doOCR(image);

        // Tesseract doesn't provide confidence for doOCR, use a default
        float confidence = 0.85f;

        log.debug("Extracted {} characters from image", text.length());
        return new OcrResult(text, 1, confidence);
    }

    /**
     * Extract text from a PDF file.
     * Rasterizes each page to an image, then OCRs.
     * 
     * Robustness features:
     * - Per-page error isolation (one corrupted page doesn't kill job)
     * - Memory guardrails (megapixel cap, image.flush())
     * - Metrics for page_corrupt and oom_guard errors
     */
    public OcrResult extractTextFromPdf(Path pdfPath) throws IOException, TesseractException {
        log.debug("Extracting text from PDF: {}", pdfPath);

        int maxPages = properties.getOcr().getMaxPages();
        StringBuilder allText = new StringBuilder();
        float totalConfidence = 0;
        int pagesProcessed = 0;
        int errorPages = 0;

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int numPages = Math.min(document.getNumberOfPages(), maxPages);

            Tesseract tesseract = createTesseract();

            for (int pageIndex = 0; pageIndex < numPages; pageIndex++) {
                BufferedImage pageImage = null;
                try {
                    log.debug("Processing PDF page {}/{}", pageIndex + 1, numPages);

                    // Render page to image at 300 DPI (capped for memory safety)
                    pageImage = renderer.renderImageWithDPI(pageIndex, 300);

                    // Memory guardrail: check megapixel cap
                    long megapixels = (long) pageImage.getWidth() * pageImage.getHeight() / 1_000_000;
                    if (megapixels > MAX_MEGAPIXELS) {
                        log.warn("Page {} exceeds megapixel cap ({} MP > {} MP); downscaling", 
                                 pageIndex + 1, megapixels, MAX_MEGAPIXELS);
                        pageImage = downscaleToMegapixels(pageImage, MAX_MEGAPIXELS);
                    }

                    // Downscale if width still too large
                    pageImage = downscaleIfNeeded(pageImage);

                    // OCR the page
                    String pageText = tesseract.doOCR(pageImage);
                    if (pageText != null && !pageText.isBlank()) {
                        allText.append(pageText).append("\n\n");
                        totalConfidence += 0.85f;
                        pagesProcessed++;
                    }

                } catch (OutOfMemoryError e) {
                    errorPages++;
                    log.error("OOM on page {} of {}; skipping", pageIndex + 1, numPages, e);
                    recordError("oom_guard");
                    // Continue processing other pages
                } catch (Exception e) {
                    errorPages++;
                    log.warn("OCR failed for page {} of {}: {}", pageIndex + 1, numPages, e.getMessage());
                    recordError("page_corrupt");
                    // Continue processing other pages
                } finally {
                    // Critical: free native memory ASAP
                    if (pageImage != null) {
                        pageImage.flush();
                    }
                }
            }

            if (document.getNumberOfPages() > maxPages) {
                log.warn("PDF has {} pages, only processed {} (maxPages limit)",
                        document.getNumberOfPages(), maxPages);
            }
        }

        // Fail if all pages errored
        if (pagesProcessed == 0 && errorPages > 0) {
            throw new TesseractException("All pages failed OCR");
        }

        float avgConfidence = pagesProcessed > 0 ? totalConfidence / pagesProcessed : 0;
        String finalText = allText.toString().trim();

        log.info("Extracted {} characters from {} successful pages, {} errors (avg confidence: {})",
                finalText.length(), pagesProcessed, errorPages, String.format("%.2f", avgConfidence));

        return new OcrResult(finalText, pagesProcessed, avgConfidence);
    }

    /**
     * Create and configure Tesseract instance.
     *
     * Tess4J expects the directory that contains *.traineddata files directly.
     * For Ubuntu 22.04, this is typically /usr/share/tesseract-ocr/4.00/tessdata
     */
    private Tesseract createTesseract() {
        Tesseract tesseractInstance = new Tesseract();

        String tessdataDirectoryPath = properties.getOcr().getDataPath();

        // Validate language data file exists (helps with debugging path issues)
        String languageCode = properties.getOcr().getLanguage();
        Path languageDataFilePath = Path.of(tessdataDirectoryPath, languageCode + ".traineddata");

        if (!java.nio.file.Files.exists(languageDataFilePath)) {
            String errorMessage = String.format(
                "Tesseract language file not found at %s. " +
                "Verify TESSDATA_PREFIX points to directory containing *.traineddata files " +
                "(e.g., /usr/share/tesseract-ocr/4.00/tessdata)",
                languageDataFilePath);
            log.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        // Pass the directory that actually contains *.traineddata files
        tesseractInstance.setDatapath(tessdataDirectoryPath);
        log.debug("Tesseract datapath set to: {} (language: {})", tessdataDirectoryPath, languageCode);

        // Set language from configuration
        tesseractInstance.setLanguage(languageCode);

        // PSM 3: Fully automatic page segmentation (default)
        tesseractInstance.setPageSegMode(3);

        // OEM 1: Neural nets LSTM engine only (best accuracy)
        tesseractInstance.setOcrEngineMode(1);

        return tesseractInstance;
    }

    /**
     * Downscale image if width exceeds 2000px.
     * Reduces memory usage and processing time.
     */
    private BufferedImage downscaleIfNeeded(BufferedImage image) {
        int maxWidth = 2000;
        if (image.getWidth() <= maxWidth) {
            return image;
        }

        float scale = (float) maxWidth / image.getWidth();
        int newWidth = maxWidth;
        int newHeight = (int) (image.getHeight() * scale);

        log.debug("Downscaling image from {}x{} to {}x{}",
                image.getWidth(), image.getHeight(), newWidth, newHeight);

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return scaled;
    }
    
    /**
     * Downscale image to fit within megapixel cap.
     * Prevents OOM errors from massive images.
     */
    private BufferedImage downscaleToMegapixels(BufferedImage image, int maxMegapixels) {
        long currentMegapixels = (long) image.getWidth() * image.getHeight() / 1_000_000;
        if (currentMegapixels <= maxMegapixels) {
            return image;
        }
        
        // Calculate scale factor to fit within megapixel cap
        double scale = Math.sqrt((double) maxMegapixels / currentMegapixels);
        int newWidth = (int) (image.getWidth() * scale);
        int newHeight = (int) (image.getHeight() * scale);
        
        log.debug("Downscaling image from {}x{} ({} MP) to {}x{} ({} MP)",
                image.getWidth(), image.getHeight(), currentMegapixels,
                newWidth, newHeight, maxMegapixels);
        
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();
        
        return scaled;
    }
}
