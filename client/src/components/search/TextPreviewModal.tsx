import { useQuery } from '@tanstack/react-query';
import { X, Copy, Download, Loader2, AlertCircle } from 'lucide-react';
import { useState } from 'react';
import { api } from '../../api/client';

interface TextPreviewModalProps {
  fileId: number;
  onClose: () => void;
}

interface FileTextResponse {
  fileId: number;
  filename: string;
  text: string;
  ocrConfidence: number | null;
  modelVersion: string | null;
}

export function TextPreviewModal({ fileId, onClose }: TextPreviewModalProps) {
  const [copySuccess, setCopySuccess] = useState(false);

  const { data, isLoading, error } = useQuery({
    queryKey: ['fileText', fileId],
    queryFn: async () => {
      return await api.get<FileTextResponse>(`/api/v1/search/files/${fileId}/text`);
    }
  });

  const handleCopy = async () => {
    if (data?.text) {
      try {
        await navigator.clipboard.writeText(data.text);
        setCopySuccess(true);
        setTimeout(() => setCopySuccess(false), 2000);
      } catch (err) {
        console.error('Failed to copy text:', err);
      }
    }
  };

  const handleDownload = () => {
    if (data?.text) {
      const blob = new Blob([data.text], { type: 'text/plain;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${data.filename.replace(/\.[^/.]+$/, '')}.txt`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    }
  };

  // Close on escape key
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      onClose();
    }
  };

  return (
    <div 
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      onClick={onClose}
      onKeyDown={handleKeyDown}
      role="dialog"
      aria-modal="true"
    >
      <div 
        className="bg-white rounded-lg shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-xl font-semibold truncate">
            {data?.filename || 'Loading...'}
          </h2>
          <div className="flex items-center gap-2">
            {data?.text && (
              <>
                <button
                  onClick={handleCopy}
                  className="p-2 hover:bg-gray-100 rounded transition"
                  title="Copy text"
                >
                  {copySuccess ? (
                    <span className="text-green-600 text-sm font-medium">Copied!</span>
                  ) : (
                    <Copy size={20} />
                  )}
                </button>
                <button
                  onClick={handleDownload}
                  className="p-2 hover:bg-gray-100 rounded transition"
                  title="Download as text file"
                >
                  <Download size={20} />
                </button>
              </>
            )}
            <button
              onClick={onClose}
              className="p-2 hover:bg-gray-100 rounded transition"
              title="Close"
            >
              <X size={20} />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {isLoading && (
            <div className="flex flex-col items-center justify-center h-full gap-4">
              <Loader2 className="animate-spin text-blue-600" size={48} />
              <p className="text-gray-600">Loading extracted text...</p>
            </div>
          )}

          {error && (
            <div className="flex flex-col items-center justify-center h-full gap-4 text-red-600">
              <AlertCircle size={48} />
              <p className="font-medium">Failed to load text</p>
              <p className="text-sm text-gray-600">
                {error instanceof Error ? error.message : 'Unknown error'}
              </p>
            </div>
          )}

          {data && (
            <>
              {/* OCR metadata */}
              {data.ocrConfidence !== null && (
                <div className="mb-4 p-3 bg-blue-50 rounded-lg text-sm">
                  <div className="flex items-center justify-between">
                    <span>
                      <span className="font-medium">OCR Confidence:</span>{' '}
                      {(data.ocrConfidence * 100).toFixed(1)}%
                    </span>
                    {data.modelVersion && (
                      <span className="text-gray-600">
                        Model: {data.modelVersion}
                      </span>
                    )}
                  </div>
                  {data.ocrConfidence < 0.7 && (
                    <div className="mt-2 flex items-start gap-2 text-yellow-700">
                      <AlertCircle size={16} className="mt-0.5 flex-shrink-0" />
                      <span>
                        Low confidence - extracted text may contain errors or inaccuracies
                      </span>
                    </div>
                  )}
                </div>
              )}

              {/* Text content */}
              {data.text ? (
                <div className="relative">
                  <pre className="whitespace-pre-wrap font-mono text-sm text-gray-800 leading-relaxed">
                    {data.text}
                  </pre>
                  <div className="mt-4 text-sm text-gray-500 text-right">
                    {data.text.length.toLocaleString()} characters
                  </div>
                </div>
              ) : (
                <div className="text-center text-gray-500 py-12">
                  <FileText size={48} className="mx-auto mb-4 opacity-50" />
                  <p>No text available for this file</p>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

// FileText icon component (if not using lucide-react)
function FileText({ size, className }: { size: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="16" y1="13" x2="8" y2="13" />
      <line x1="16" y1="17" x2="8" y2="17" />
      <polyline points="10 9 9 9 8 9" />
    </svg>
  );
}

