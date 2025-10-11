package org.ddamme.util;

import java.nio.charset.StandardCharsets;

/**
 * Utility class for file operations and content type handling.
 */
public final class FileUtils {

    private FileUtils() {
        // Utility class
    }

    /**
     * Returns a safe content type, defaulting to application/octet-stream if null or blank. This
     * ensures consistent behavior across storage services.
     */
    public static String getContentTypeOrDefault(String contentType) {
        return (contentType == null || contentType.isBlank())
                ? "application/octet-stream"
                : contentType;
    }

    /**
     * Sanitizes a filename by replacing non-alphanumeric characters (except dots, hyphens,
     * underscores). This helps prevent issues with storage keys and file system operations.
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        return filename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }

    /**
     * RFC 5987 compliant percent-encoding for filename* parameter. Unlike URLEncoder, this doesn't
     * encode spaces as '+' and follows the RFC 5987 spec.
     */
    public static String rfc5987Encode(String inputString) {
        byte[] bytes = inputString.getBytes(StandardCharsets.UTF_8);
        StringBuilder encodedResult = new StringBuilder();

        for (byte byteValue : bytes) {
            // Convert signed byte to unsigned int (0-255 range)
            int codePoint = byteValue & 0xFF;

            // RFC 5987 unreserved characters: alphanumeric and -._~
            boolean unreserved =
                    (codePoint >= '0' && codePoint <= '9')
                            || (codePoint >= 'A' && codePoint <= 'Z')
                            || (codePoint >= 'a' && codePoint <= 'z')
                            || codePoint == '-'
                            || codePoint == '_'
                            || codePoint == '.'
                            || codePoint == '~';

            if (unreserved) {
                // Safe characters can be used directly
                encodedResult.append((char) codePoint);
            } else {
                // All other characters must be percent-encoded as %HH
                encodedResult.append('%').append(String.format("%02X", codePoint));
            }
        }

        return encodedResult.toString();
    }
}
