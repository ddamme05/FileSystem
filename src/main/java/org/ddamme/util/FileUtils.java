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
    public static String rfc5987Encode(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            int ch = b & 0xFF;
            boolean unreserved =
                    (ch >= '0' && ch <= '9')
                            || (ch >= 'A' && ch <= 'Z')
                            || (ch >= 'a' && ch <= 'z')
                            || ch == '-'
                            || ch == '_'
                            || ch == '.'
                            || ch == '~';
            if (unreserved) {
                sb.append((char) ch);
            } else {
                sb.append('%').append(String.format("%02X", ch));
            }
        }
        return sb.toString();
    }
}
