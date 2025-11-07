/**
 * Search types for Full-Text Search API
 * 
 * See: docs/FRONTEND_INTEGRATION_GUIDE.md for complete API documentation
 */

export interface SearchResult {
  fileId: number;
  filename: string;
  contentType: string;
  size: number;
  uploadedAt: string; // ISO 8601 datetime
  snippet: string;    // HTML with <mark> tags - MUST sanitize before rendering
  ocrConfidence: number | null;
  rank: number;       // Relevance score (higher = more relevant)
}

export interface SearchResponse {
  results: SearchResult[];
  nextRank: number | null;   // Cursor for next page (keyset pagination)
  nextId: number | null;     // Cursor for next page (keyset pagination)
  hasMore: boolean;          // More results available?
  count: number;             // Results in current page
}

export interface SearchRequest {
  q: string;          // Search query (required)
  lastRank?: number;  // Cursor from previous page
  lastId?: number;    // Cursor from previous page
  limit?: number;     // Results per page (default: 20, max: 100)
}

export interface FileTextResponse {
  fileId: number;
  filename: string;
  text: string;
  ocrConfidence: number | null;
  modelVersion: string | null;
}

/**
 * Cursor for keyset pagination
 * Maintains stable pagination across concurrent writes
 */
export interface SearchCursor {
  rank: number;
  id: number;
}



