import DOMPurify from 'dompurify';
import { FileText, AlertCircle } from 'lucide-react';
import type { SearchResult } from '../../types/search';
import { formatFileSize, formatRelativeTime } from '../../lib/formatters';

interface SearchResultsProps {
  results: SearchResult[];
  onFileClick: (fileId: number) => void;
  onPreviewText: (fileId: number) => void;
}

export function SearchResults({ results, onFileClick, onPreviewText }: SearchResultsProps) {
  if (results.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        <FileText size={48} className="mx-auto mb-4 opacity-50" />
        <p className="text-lg">No results found</p>
        <p className="text-sm mt-2">Try different keywords or check your spelling</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {results.map((result) => (
        <div 
          key={result.fileId} 
          className="border rounded-lg p-4 hover:shadow-md transition-shadow bg-white"
        >
          {/* Filename and metadata */}
          <div className="flex items-start justify-between mb-2">
            <button
              onClick={() => onFileClick(result.fileId)}
              className="text-lg font-medium text-blue-600 hover:underline text-left flex-1"
            >
              {result.filename}
            </button>
            <span className="text-sm text-gray-500 ml-4 whitespace-nowrap">
              {formatRelativeTime(result.uploadedAt)}
            </span>
          </div>

          {/* Search snippet with highlighting */}
          {result.snippet && (
            <div
              className="text-gray-700 mb-3 leading-relaxed"
              dangerouslySetInnerHTML={{
                __html: DOMPurify.sanitize(result.snippet, {
                  ALLOWED_TAGS: ['mark'],  // Only allow <mark> tags for highlighting
                  ALLOWED_ATTR: []         // No attributes allowed
                })
              }}
            />
          )}

          {/* File metadata footer */}
          <div className="flex items-center gap-4 text-sm text-gray-500 flex-wrap">
            <span>{result.contentType}</span>
            <span>{formatFileSize(result.size)}</span>
            
            {/* OCR confidence indicator */}
            {result.ocrConfidence !== null && (
              <span className="flex items-center gap-1">
                <span>OCR: {(result.ocrConfidence * 100).toFixed(0)}%</span>
                {result.ocrConfidence < 0.7 && (
                <AlertCircle 
                  size={14} 
                  className="text-yellow-600"
                  aria-label="Low confidence - text may contain errors"
                />
                )}
              </span>
            )}
            
            {/* Relevance score */}
            {result.rank !== undefined && (
              <span className="text-gray-400">
                Score: {result.rank.toFixed(2)}
              </span>
            )}
            
            {/* Preview button */}
            <button
              onClick={() => onPreviewText(result.fileId)}
              className="text-blue-600 hover:underline ml-auto"
            >
              Preview Text
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

