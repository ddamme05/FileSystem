import { useHasText } from '../../hooks/useSearch';
import { FileText, Loader2, CheckCircle } from 'lucide-react';

interface OcrStatusBadgeProps {
  fileId: number;
}

/**
 * Badge showing OCR text availability status for a file
 * Uses HEAD request for efficient checking (no body transfer)
 * 
 * Example usage:
 * ```tsx
 * <OcrStatusBadge fileId={file.id} />
 * ```
 */
export function OcrStatusBadge({ fileId }: OcrStatusBadgeProps) {
  const { data, isLoading, error } = useHasText(fileId);

  if (isLoading) {
    return (
      <div className="flex items-center gap-1 text-gray-500 text-sm">
        <Loader2 size={16} className="animate-spin" />
        <span>Checking...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center gap-1 text-gray-400 text-sm">
        <FileText size={16} />
        <span>Unknown</span>
      </div>
    );
  }

  if (!data?.hasText) {
    return (
      <div className="flex items-center gap-1 text-gray-400 text-sm">
        <FileText size={16} />
        <span>No text</span>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-1 text-green-600 text-sm">
      <CheckCircle size={16} />
      <span>Text available ({formatTextLength(data.textLength)})</span>
    </div>
  );
}

function formatTextLength(chars: number): string {
  if (chars === 0) return '0 chars';
  if (chars < 1000) return `${chars} char${chars !== 1 ? 's' : ''}`;
  if (chars < 10000) return `${(chars / 1000).toFixed(1)}K`;
  return `${Math.round(chars / 1000)}K`;
}



