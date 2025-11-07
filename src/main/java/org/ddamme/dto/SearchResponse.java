package org.ddamme.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated search response with keyset cursor for stable pagination.
 * 
 * Keyset Pagination Pattern:
 * - Uses (rank, id) composite cursor for deterministic ordering
 * - No OFFSET drift when new results inserted mid-pagination
 * - Frontend gets cursor values from last result, passes in next request
 * 
 * Example flow:
 * <pre>{@code
 * // First request
 * GET /api/v1/search/text?q=machine+learning
 * Response: { results: [...], nextRank: 0.5, nextId: 123, hasMore: true }
 * 
 * // Second request (next page)
 * GET /api/v1/search/text?q=machine+learning&lastRank=0.5&lastId=123
 * Response: { results: [...], nextRank: 0.3, nextId: 456, hasMore: false }
 * }</pre>
 * 
 * See: cursor_v2_rationale.md Section "Keyset Pagination" for details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    
    /** Search results for current page */
    private List<SearchResultDto> results;
    
    /** Rank value of last result (for next page cursor) */
    private Double nextRank;
    
    /** ID of last result (for next page cursor, tie-breaker) */
    private Long nextId;
    
    /** True if more results available beyond current page */
    private boolean hasMore;
    
    /** Total results returned in this page */
    private int count;
}

