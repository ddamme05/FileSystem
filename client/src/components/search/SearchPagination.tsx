import { ChevronLeft, ChevronRight } from 'lucide-react';

interface SearchPaginationProps {
  hasMore: boolean;
  currentPage: number;
  onNextPage: () => void;
  onPrevPage: () => void;
  isLoading: boolean;
  resultCount?: number;
}

export function SearchPagination({ 
  hasMore, 
  currentPage, 
  onNextPage, 
  onPrevPage, 
  isLoading,
  resultCount 
}: SearchPaginationProps) {
  return (
    <div className="flex items-center justify-between mt-6 pt-6 border-t">
      <button
        onClick={onPrevPage}
        disabled={currentPage === 1 || isLoading}
        className="flex items-center gap-2 px-4 py-2 border rounded-lg disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 transition"
      >
        <ChevronLeft size={18} />
        <span>Previous</span>
      </button>
      
      <div className="text-gray-600">
        <span className="font-medium">Page {currentPage}</span>
        {resultCount !== undefined && (
          <span className="text-sm ml-2">({resultCount} results)</span>
        )}
      </div>
      
      <button
        onClick={onNextPage}
        disabled={!hasMore || isLoading}
        className="flex items-center gap-2 px-4 py-2 border rounded-lg disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 transition"
      >
        <span>Next</span>
        <ChevronRight size={18} />
      </button>
    </div>
  );
}



