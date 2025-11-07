package org.ddamme.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data transfer object for search results.
 * Includes file metadata plus search-specific fields (snippet, rank).
 * 
 * ⚠️ FRONTEND HTML SAFETY WARNING:
 * 
 * The `snippet` field contains HTML markup from PostgreSQL's ts_headline function:
 * - Highlighted search terms are wrapped in {@code <mark>} tags
 * - Example: "This is a <mark>highlighted</mark> snippet"
 * 
 * Frontend MUST sanitize before rendering to prevent XSS attacks:
 * 
 * <pre>{@code
 * // React/TypeScript Example - SAFE ✅
 * import DOMPurify from 'dompurify';
 * 
 * function SearchResult({ result }: { result: SearchResultDto }) {
 *   const sanitizedSnippet = DOMPurify.sanitize(result.snippet, {
 *     ALLOWED_TAGS: ['mark'],  // Only allow <mark> tags
 *     ALLOWED_ATTR: []         // No attributes needed
 *   });
 *   
 *   return (
 *     <div>
 *       <h3>{result.filename}</h3>
 *       <p dangerouslySetInnerHTML={{ __html: sanitizedSnippet }} />
 *     </div>
 *   );
 * }
 * 
 * // Alternative (no highlighting) - SAFE ✅
 * // Note: <mark> tags will be displayed as plain text in React
 * function SearchResult({ result }: { result: SearchResultDto }) {
 *   return (
 *     <div>
 *       <h3>{result.filename}</h3>
 *       <p>{result.snippet}</p>
 *     </div>
 *   );
 * }
 * 
 * // DANGEROUS ❌ - DO NOT USE (raw HTML injection)
 * // <div dangerouslySetInnerHTML={{ __html: result.snippet }} />
 * }</pre>
 * 
 * Recommended library: DOMPurify (https://github.com/cure53/DOMPurify)
 * 
 * See: cursor_v2_rationale.md Section "Snippet HTML Safety" for details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDto {

    private Long fileId;

    private String filename;

    private String contentType;

    private Long size;

    private Instant uploadedAt;

    /**
     * Text snippet showing match context.
     * Contains {@code <mark>} tags for highlighting search terms.
     * 
     * ⚠️ WARNING: Frontend must sanitize with DOMPurify before rendering!
     * See class-level documentation for safe rendering examples.
     */
    private String snippet;

    /** OCR confidence score (0-1) */
    private Float ocrConfidence;

    /** Search relevance rank (higher = more relevant) */
    private Double rank;
}

