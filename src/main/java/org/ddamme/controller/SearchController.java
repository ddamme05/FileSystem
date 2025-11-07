package org.ddamme.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.database.repository.MetadataRepository;
import org.ddamme.dto.SearchResponse;
import org.ddamme.dto.SearchResultDto;
import org.ddamme.exception.InvalidRequestException;
import org.ddamme.service.SearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST API for search operations.
 * Base path: /api/v1/search
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final SearchService searchService;
    private final MetadataRepository metadataRepository;

    /**
     * Full-text search across user's files (simple version, no pagination).
     *
     * @param query Search query (supports phrase search, boolean operators)
     * @return List of matching files with snippets
     *
     * Example queries:
     * - "machine learning"  (phrase search)
     * - cats AND dogs       (boolean AND)
     * - cats OR dogs        (boolean OR)
     * 
     * DEPRECATED: Use searchTextPaginated for stable pagination.
     */
    @GetMapping("/text")
    public ResponseEntity<List<SearchResultDto>> searchText(
            @AuthenticationPrincipal User user,
            @RequestParam("q") String query) {

        if (query == null || query.isBlank()) {
            throw new InvalidRequestException("Search query cannot be empty");
        }

        log.info("Search request: query='{}', userId={}", query, user.getId());

        List<SearchResultDto> results = searchService.searchText(user, query);

        return ResponseEntity.ok(results);
    }
    
    /**
     * Full-text search with keyset pagination (stable cursors, no OFFSET drift).
     * 
     * Keyset pagination prevents duplicate/missing results when new files uploaded mid-pagination.
     * Uses (rank, id) composite cursor for deterministic ordering.
     * 
     * Query Parameters:
     * - q: Search query (required)
     * - lastRank: Rank value from previous page (optional, for pagination)
     * - lastId: ID from previous page (optional, for pagination)
     * - limit: Results per page (optional, default: 20, max: 100)
     * 
     * Response includes:
     * - results: Current page results
     * - nextRank: Rank cursor for next page
     * - nextId: ID cursor for next page
     * - hasMore: True if more results available
     * - count: Results in current page
     * 
     * Example flow:
     * <pre>
     * // First page
     * GET /api/v1/search/text/paginated?q=machine+learning&limit=20
     * Response: { results: [...], nextRank: 0.5, nextId: 123, hasMore: true, count: 20 }
     * 
     * // Second page
     * GET /api/v1/search/text/paginated?q=machine+learning&limit=20&lastRank=0.5&lastId=123
     * Response: { results: [...], nextRank: 0.3, nextId: 456, hasMore: false, count: 15 }
     * </pre>
     * 
     * See: cursor_v2_rationale.md Section "Keyset Pagination" for rationale
     */
    @GetMapping("/text/paginated")
    public ResponseEntity<SearchResponse> searchTextPaginated(
            @AuthenticationPrincipal User user,
            @RequestParam("q") String query,
            @RequestParam(value = "lastRank", required = false) Double lastRank,
            @RequestParam(value = "lastId", required = false) Long lastId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        if (query == null || query.isBlank()) {
            throw new InvalidRequestException("Search query cannot be empty");
        }

        log.info("Paginated search request: query='{}', lastRank={}, lastId={}, limit={}, userId={}", 
                 query, lastRank, lastId, limit, user.getId());

        SearchResponse response = searchService.searchTextWithPagination(user, query, lastRank, lastId, limit);

        return ResponseEntity.ok(response);
    }

    /**
     * Get full extracted text for a file.
     * Note: This could also live in FileController as /api/v1/files/{id}/text
     */
    @GetMapping("/files/{fileId}/text")
    public ResponseEntity<Map<String, Object>> getFileText(
            @AuthenticationPrincipal User user,
            @PathVariable Long fileId) {

        Map<String, Object> result = searchService.getFileText(fileId, user);

        return ResponseEntity.ok(result);
    }
    
    /**
     * Check if a file has extracted text without fetching the full body.
     * 
     * Native HTTP HEAD endpoint with custom headers:
     * - X-Has-Text: true|false (whether file has OCR'd text)
     * - X-Text-Length: <number> (character count, not bytes)
     * - ETag: <hash> (for caching, optional)
     * 
     * Frontend can use this to:
     * - Show "Text available" badge without fetching full text
     * - Decide whether to offer preview/search highlighting
     * - Implement conditional requests with If-None-Match
     * 
     * See: docs/SUPPLIER_PATTERN.md for supplier pattern used in metrics
     * See: cursor_v2_rationale.md Section "HEAD Endpoint" for rationale
     */
    @RequestMapping(
            path = "/files/{fileId}/text",
            method = RequestMethod.HEAD
    )
    public ResponseEntity<Void> headFileText(
            @AuthenticationPrincipal User user,
            @PathVariable Long fileId) {
        
        FileMetadata meta = metadataRepository.findByIdAndUser_Id(fileId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "File not found or access denied"));
        
        boolean hasText = meta.getFileText() != null && !meta.getFileText().isBlank();
        // X-Text-Length is **characters**, not bytes (clarified for client expectations)
        int textChars = hasText ? meta.getFileText().length() : 0;
        
        // Optional ETag for caching (SHA-1 hash for collision resistance)
        if (hasText && meta.getFileText() != null) {
            try {
                var md = java.security.MessageDigest.getInstance("SHA-1");
                byte[] digest = md.digest(meta.getFileText().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String eTag = "\"" + java.util.HexFormat.of().formatHex(digest) + "\"";
                return ResponseEntity.noContent()
                        .header("X-Has-Text", Boolean.toString(hasText))
                        .header("X-Text-Length", Integer.toString(textChars))
                        .eTag(eTag) // Clients can use If-None-Match for 304 Not Modified
                        .build();
            } catch (java.security.NoSuchAlgorithmException e) {
                // Fallback if SHA-1 unavailable (should never happen)
                log.warn("SHA-1 not available for ETag generation", e);
            }
        }
        
        // No text or SHA-1 unavailable - return without ETag
        return ResponseEntity.noContent()
                .header("X-Has-Text", Boolean.toString(hasText))
                .header("X-Text-Length", Integer.toString(textChars))
                .build();
    }
}

