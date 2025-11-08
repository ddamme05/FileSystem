package org.ddamme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.database.repository.MetadataRepository;
import org.ddamme.dto.SearchResponse;
import org.ddamme.dto.SearchResultDto;
import org.ddamme.logging.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Full-text search service using PostgreSQL FTS.
 *
 * Features:
 * - Weighted ranking (filename > text content)
 * - Snippet extraction with ts_headline
 * - Boolean operators (AND, OR)
 * - Phrase search ("exact phrase")
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final MetadataRepository metadataRepository;
    private final MetadataService metadataService;

    /**
     * Search files by text content.
     * Uses PostgreSQL websearch_to_tsquery for user-friendly syntax.
     */
    @Transactional(readOnly = true)
    public List<SearchResultDto> searchText(User user, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        log.debug("Searching for: '{}' (userId={})", query, user.getId());

        List<Object[]> rows = metadataRepository.searchTextRaw(user.getId(), query);
        List<SearchResultDto> dtos = new ArrayList<>(rows.size());

        for (Object[] r : rows) {
            Long id = ((Number) r[0]).longValue();
            String filename = (String) r[1];
            String contentType = (String) r[2];
            Long size = ((Number) r[3]).longValue();
            Instant uploadedAt = convertToInstant(r[4]);
            Double rank = ((Number) r[5]).doubleValue();
            String snippet = (String) r[6];
            Float ocrConfidence = r[7] != null ? ((Number) r[7]).floatValue() : null;

            dtos.add(SearchResultDto.builder()
                    .fileId(id)
                    .filename(filename)
                    .contentType(contentType)
                    .size(size)
                    .uploadedAt(uploadedAt)
                    .snippet(snippet)
                    .ocrConfidence(ocrConfidence)
                    .rank(rank)
                    .build());
        }

        log.info("Search returned {} results for query: '{}' (userId={})",
                dtos.size(), query, user.getId());

        AuditLogger.log("SEARCH_TEXT",
                Map.of("username", user.getUsername(), "query", query, "resultCount", dtos.size()));

        return dtos;
    }

    /**
     * Get full extracted text for a file.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFileText(Long fileId, User user) {
        FileMetadata metadata = metadataService.findOwnedById(user, fileId);

        AuditLogger.log("FILE_TEXT_ACCESS",
                Map.of("username", user.getUsername(), "fileId", fileId, "filename", metadata.getOriginalFilename()));

        return Map.of(
                "fileId", metadata.getId(),
                "filename", metadata.getOriginalFilename(),
                "text", metadata.getFileText() != null ? metadata.getFileText() : "",
                "ocrConfidence", metadata.getOcrConfidence() != null ? metadata.getOcrConfidence() : 0,
                "modelVersion", metadata.getOcrModelVersion() != null ? metadata.getOcrModelVersion() : ""
        );
    }

    /**
     * Search files by text content with keyset pagination (stable cursors).
     * 
     * Keyset pagination prevents OFFSET drift when new results are inserted mid-pagination.
     * Uses (rank, id) composite cursor for deterministic ordering.
     * 
     * @param user Authenticated user
     * @param query Search query (supports phrase search, boolean operators)
     * @param lastRank Rank value of last result from previous page (null for first page)
     * @param lastId ID of last result from previous page (null for first page)
     * @param limit Results per page (default: 20, max: 100)
     * @return SearchResponse with results and pagination metadata
     */
    @Transactional(readOnly = true)
    public SearchResponse searchTextWithPagination(
            User user, String query, Double lastRank, Long lastId, int limit) {
        
        if (query == null || query.isBlank()) {
            return SearchResponse.builder()
                    .results(List.of())
                    .hasMore(false)
                    .count(0)
                    .build();
        }
        
        // Validate and cap limit
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);
        
        // Fetch limit + 1 to detect "has more" without separate count query
        List<Object[]> rows = metadataRepository.searchTextWithCursor(
                user.getId(), query, lastRank, lastId, effectiveLimit + 1);
        
        boolean hasMore = rows.size() > effectiveLimit;
        List<Object[]> pageRows = hasMore ? rows.subList(0, effectiveLimit) : rows;
        
        List<SearchResultDto> dtos = new ArrayList<>(pageRows.size());
        
        for (Object[] r : pageRows) {
            Long id = ((Number) r[0]).longValue();
            String filename = (String) r[1];
            String contentType = (String) r[2];
            Long size = ((Number) r[3]).longValue();
            Instant uploadedAt = convertToInstant(r[4]);
            Double rank = ((Number) r[5]).doubleValue();
            String snippet = (String) r[6];
            Float ocrConfidence = r[7] != null ? ((Number) r[7]).floatValue() : null;

            dtos.add(SearchResultDto.builder()
                    .fileId(id)
                    .filename(filename)
                    .contentType(contentType)
                    .size(size)
                    .uploadedAt(uploadedAt)
                    .snippet(snippet)
                    .ocrConfidence(ocrConfidence)
                    .rank(rank)
                    .build());
        }
        
        // Extract cursor from last result for next page
        Double nextRank = null;
        Long nextId = null;
        if (!dtos.isEmpty()) {
            SearchResultDto lastResult = dtos.get(dtos.size() - 1);
            nextRank = lastResult.getRank();
            nextId = lastResult.getFileId();
        }
        
        log.info("Search returned {} results (hasMore={}) for query: '{}' (userId={})",
                dtos.size(), hasMore, query, user.getId());
        
        AuditLogger.log("SEARCH_TEXT_PAGINATED",
                Map.of("username", user.getUsername(), "query", query, 
                       "resultCount", dtos.size(), "hasMore", hasMore));
        
        return SearchResponse.builder()
                .results(dtos)
                .nextRank(nextRank)
                .nextId(nextId)
                .hasMore(hasMore)
                .count(dtos.size())
                .build();
    }

    /**
     * Check if file has OCR text.
     */
    public boolean hasOcrText(Long fileId) {
        return metadataRepository.findById(fileId)
                .map(m -> m.getFileText() != null && !m.getFileText().isBlank())
                .orElse(false);
    }

    /**
     * Convert JDBC timestamp result to Instant.
     * Handles Timestamp, LocalDateTime, and Instant types.
     */
    private Instant convertToInstant(Object timestamp) {
        if (timestamp == null) {
            return null;
        }
        if (timestamp instanceof Instant instant) {
            return instant;
        }
        if (timestamp instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (timestamp instanceof java.time.LocalDateTime ldt) {
            return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
        }
        throw new IllegalArgumentException("Unexpected timestamp type: " + timestamp.getClass().getName());
    }
}
