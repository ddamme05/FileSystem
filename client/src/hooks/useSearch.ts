import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { SearchResponse } from '../types/search';

interface UseSearchOptions {
  query: string;
  lastRank?: number;
  lastId?: number;
  limit?: number;
  enabled?: boolean;
}

/**
 * Hook for full-text search with keyset pagination
 * 
 * Example usage:
 * ```tsx
 * const { data, isLoading, error } = useSearch({
 *   query: 'machine learning',
 *   lastRank: cursors[page - 2]?.rank,
 *   lastId: cursors[page - 2]?.id,
 *   limit: 20
 * });
 * ```
 */
export function useSearch({ query, lastRank, lastId, limit = 20, enabled = true }: UseSearchOptions) {
  return useQuery({
    queryKey: ['search', query, lastRank, lastId, limit],
    queryFn: async () => {
      if (!query?.trim()) {
        return {
          results: [],
          nextRank: null,
          nextId: null,
          hasMore: false,
          count: 0
        };
      }

      const params = new URLSearchParams();
      params.append('q', query.trim());
      params.append('limit', limit.toString());
      
      // Add cursor for pagination (if not first page)
      if (lastRank !== undefined && lastId !== undefined) {
        params.append('lastRank', lastRank.toString());
        params.append('lastId', lastId.toString());
      }

      return await api.get<SearchResponse>(`/api/v1/search/text/paginated?${params.toString()}`);
    },
    enabled: enabled && !!query?.trim(),
    staleTime: 30000, // Consider results fresh for 30 seconds
    gcTime: 5 * 60 * 1000 // Keep in cache for 5 minutes
  });
}

/**
 * Hook to check if a file has extracted text (HEAD request)
 * 
 * Example usage:
 * ```tsx
 * const { data: hasText, isLoading } = useHasText(fileId);
 * ```
 */
export function useHasText(fileId: number) {
  return useQuery({
    queryKey: ['hasText', fileId],
    queryFn: async () => {
      const response = await api.head(`/api/v1/search/files/${fileId}/text`);
      
      return {
        hasText: response.headers.get('x-has-text') === 'true',
        textLength: parseInt(response.headers.get('x-text-length') || '0'),
        eTag: response.headers.get('etag')
      };
    },
    staleTime: 60000, // Cache for 1 minute
    gcTime: 10 * 60 * 1000 // Keep in cache for 10 minutes
  });
}

