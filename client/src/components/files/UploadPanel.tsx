import { useState } from 'react';
import { CheckCircle2, XCircle, X, ChevronDown, ChevronUp, Trash2 } from 'lucide-react';

import { useUploadContext } from '@/hooks/useUploadContext';
import { formatFileSize } from '@/lib/formatters';

export function UploadPanel() {
  const { uploads, removeUpload, clearCompleted } = useUploadContext();
  const [isMinimized, setIsMinimized] = useState(false);

  if (uploads.length === 0) return null;

  const activeUploads = uploads.filter((u) => u.status === 'uploading').length;
  const completedUploads = uploads.filter((u) => u.status === 'success').length;
  const failedUploads = uploads.filter((u) => u.status === 'error').length;

  return (
    <div className="fixed bottom-4 right-4 w-96 bg-white rounded-lg shadow-xl border border-gray-200 z-50">
      {/* Header */}
      <div
        className="flex items-center justify-between px-4 py-3 border-b border-gray-200 cursor-pointer hover:bg-gray-50"
        onClick={() => setIsMinimized(!isMinimized)}
      >
        <div className="flex items-center gap-2">
          <h3 className="font-semibold text-sm">
            {activeUploads > 0 ? (
              <>Uploading {activeUploads} file{activeUploads !== 1 ? 's' : ''}...</>
            ) : (
              <>Upload Complete</>
            )}
          </h3>
          {completedUploads > 0 && (
            <span className="text-xs text-green-600 font-medium">
              {completedUploads} done
            </span>
          )}
          {failedUploads > 0 && (
            <span className="text-xs text-red-600 font-medium">
              {failedUploads} failed
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {activeUploads === 0 && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                clearCompleted();
              }}
              className="text-xs text-gray-500 hover:text-gray-700"
              title="Clear completed"
            >
              <Trash2 size={14} />
            </button>
          )}
          {isMinimized ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
        </div>
      </div>

      {/* Upload List */}
      {!isMinimized && (
        <div className="max-h-96 overflow-y-auto">
          {uploads.map((upload) => (
            <div
              key={upload.id}
              className="px-4 py-3 border-b border-gray-100 last:border-b-0 hover:bg-gray-50"
            >
              <div className="flex items-start gap-3">
                {/* Status Icon */}
                <div className="flex-shrink-0 mt-0.5">
                  {upload.status === 'success' && (
                    <CheckCircle2 className="text-green-500" size={20} />
                  )}
                  {upload.status === 'error' && (
                    <XCircle className="text-red-500" size={20} />
                  )}
                  {upload.status === 'uploading' && (
                    <div className="w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
                  )}
                  {upload.status === 'cancelled' && (
                    <XCircle className="text-gray-400" size={20} />
                  )}
                </div>

                {/* File Info */}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">
                    {upload.file.name}
                  </p>
                  <p className="text-xs text-gray-500 mt-0.5">
                    {formatFileSize(upload.file.size)}
                  </p>

                  {/* Progress Bar */}
                  {upload.status === 'uploading' && (
                    <div className="mt-2">
                      <div className="flex items-center gap-2">
                        <div className="flex-1 bg-gray-200 rounded-full h-1.5">
                          <div
                            className="h-1.5 bg-blue-500 rounded-full transition-all duration-300"
                            style={{ width: `${upload.progress}%` }}
                          />
                        </div>
                        <span className="text-xs text-gray-500 w-10 text-right">
                          {upload.progress.toFixed(0)}%
                        </span>
                      </div>
                    </div>
                  )}

                  {/* Error Message */}
                  {upload.status === 'error' && upload.error && (
                    <p className="text-xs text-red-600 mt-1">{upload.error}</p>
                  )}

                  {/* Success Message */}
                  {upload.status === 'success' && (
                    <p className="text-xs text-green-600 mt-1">Upload complete</p>
                  )}
                </div>

                {/* Action Buttons */}
                <div className="flex-shrink-0">
                  {upload.status === 'uploading' && upload.cancel && (
                    <button
                      onClick={() => {
                        upload.cancel?.();
                      }}
                      className="text-gray-400 hover:text-gray-600"
                      title="Cancel upload"
                    >
                      <X size={16} />
                    </button>
                  )}
                  {(upload.status === 'success' || upload.status === 'error' || upload.status === 'cancelled') && (
                    <button
                      onClick={() => removeUpload(upload.id)}
                      className="text-gray-400 hover:text-gray-600"
                      title="Remove from list"
                    >
                      <X size={16} />
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}






