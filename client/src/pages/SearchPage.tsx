import { useState } from 'react';
import { SearchBar } from '../components/search/SearchBar';
import { SearchResults } from '../components/search/SearchResults';
import { SearchPagination } from '../components/search/SearchPagination';
import { TextPreviewModal } from '../components/search/TextPreviewModal';
import { useSearch } from '../hooks/useSearch';
import type { SearchCursor } from '../types/search';
import { Loader2 } from 'lucide-react';

export function SearchPage() {
  const [query, setQuery] = useState('');
  const [cursors, setCursors] = useState<SearchCursor[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [previewFileId, setPreviewFileId] = useState<number | null>(null);

  // Get cursor for current page (if not first page)
  const currentCursor = currentPage > 1 ? cursors[currentPage - 2] : undefined;

  const { data, isLoading, error } = useSearch({
    query,
    ...(currentCursor && {
      lastRank: currentCursor.rank,
      lastId: currentCursor.id
    }),
    limit: 20,
    enabled: !!query
  });

  const handleSearch = (newQuery: string) => {
    setQuery(newQuery);
    setCursors([]);
    setCurrentPage(1);
  };

  const handleNextPage = () => {
    if (data && data.nextRank !== null && data.nextId !== null) {
      setCursors(prev => [...prev, { rank: data.nextRank!, id: data.nextId! }]);
      setCurrentPage(prev => prev + 1);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const handlePrevPage = () => {
    if (currentPage > 1) {
      setCursors(prev => prev.slice(0, -1));
      setCurrentPage(prev => prev - 1);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const handleFileClick = (fileId: number) => {
    // Navigate to file detail page or trigger file download
    window.location.href = `/files/${fileId}`;
  };

  return (
    <div className="container mx-auto px-4 py-8 max-w-5xl">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-2">Search Files</h1>
        <p className="text-gray-600">
          Search across all your files using full-text search. Supports phrase search, AND, OR operators.
        </p>
      </div>

      {/* Search bar */}
      <div className="mb-8">
        <SearchBar 
          onSearch={handleSearch} 
          initialQuery={query}
          placeholder="Search for text in your files..."
        />
        {query && (
          <div className="mt-2 text-sm text-gray-600">
            <p className="font-medium">Search tips:</p>
            <ul className="list-disc list-inside mt-1 space-y-1">
              <li>Use quotes for exact phrases: <code className="bg-gray-100 px-1 rounded">"machine learning"</code></li>
              <li>Combine terms with AND: <code className="bg-gray-100 px-1 rounded">cats AND dogs</code></li>
              <li>Find either term with OR: <code className="bg-gray-100 px-1 rounded">cats OR dogs</code></li>
            </ul>
          </div>
        )}
      </div>

      {/* Loading state */}
      {isLoading && (
        <div className="flex flex-col items-center justify-center py-16 gap-4">
          <Loader2 className="animate-spin text-blue-600" size={48} />
          <p className="text-gray-600">Searching...</p>
        </div>
      )}

      {/* Error state */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-800">
          <p className="font-medium">Search failed</p>
          <p className="text-sm mt-1">
            {error instanceof Error ? error.message : 'Unknown error occurred'}
          </p>
        </div>
      )}

      {/* Results */}
      {data && !isLoading && (
        <>
          <div className="mb-6">
            <p className="text-gray-600">
              Found <span className="font-medium">{data.count}</span> result{data.count !== 1 ? 's' : ''}
              {currentPage > 1 && ` (page ${currentPage})`}
            </p>
          </div>

          <SearchResults
            results={data.results}
            onFileClick={handleFileClick}
            onPreviewText={setPreviewFileId}
          />

          {(data.hasMore || currentPage > 1) && (
            <SearchPagination
              hasMore={data.hasMore}
              currentPage={currentPage}
              onNextPage={handleNextPage}
              onPrevPage={handlePrevPage}
              isLoading={isLoading}
              resultCount={data.count}
            />
          )}
        </>
      )}

      {/* Empty state (no query) */}
      {!query && !isLoading && (
        <div className="text-center py-16 text-gray-500">
          <svg 
            className="mx-auto mb-4 opacity-50" 
            width="64" 
            height="64" 
            viewBox="0 0 24 24" 
            fill="none" 
            stroke="currentColor"
            strokeWidth="1.5"
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" />
          </svg>
          <p className="text-lg">Start searching your files</p>
          <p className="text-sm mt-2">Enter a search term above to find text in your uploaded files</p>
        </div>
      )}

      {/* Text preview modal */}
      {previewFileId && (
        <TextPreviewModal
          fileId={previewFileId}
          onClose={() => setPreviewFileId(null)}
        />
      )}
    </div>
  );
}

