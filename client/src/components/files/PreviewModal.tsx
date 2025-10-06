import { useEffect, useState } from 'react';
import { X, Download, Link as LinkIcon, FileX, ChevronLeft, ChevronRight } from 'lucide-react';
import type { FileMetadata } from '@/types/file';
import { getFileTypeInfo } from '@/lib/mimeTypes';
import { FEATURE_FLAGS } from '@/lib/featureFlags';

interface PreviewModalProps {
  file: FileMetadata;
  files: FileMetadata[];
  onClose: () => void;
  onDownload: (id: number) => void;
  onCopyLink: (id: number) => void;
  onNavigate?: (id: number) => void;
}

export function PreviewModal({
  file,
  files,
  onClose,
  onDownload,
  onCopyLink,
  onNavigate,
}: PreviewModalProps) {
  const [presignedUrl, setPresignedUrl] = useState<string>('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const typeInfo = getFileTypeInfo(file.contentType);
  const isImage = file.contentType.startsWith('image/');
  const isPdf = file.contentType === 'application/pdf';
  const isText = file.contentType.startsWith('text/');
  const isVideo = file.contentType.startsWith('video/');
  const isAudio = file.contentType.startsWith('audio/');

  // Find previewable files for navigation (images only for MVP)
  const previewableFiles = files.filter((f) => f.contentType.startsWith('image/'));
  const currentIndex = previewableFiles.findIndex((f) => f.id === file.id);
  const canNavigate = isImage && previewableFiles.length > 1;
  const hasPrevious = canNavigate && currentIndex > 0;
  const hasNext = canNavigate && currentIndex < previewableFiles.length - 1;

  // View endpoint (inline Content-Disposition)
  const viewUrl = `/api/v1/files/view/${file.id}/redirect`;

  // Fetch presigned URL with auth, then use it for preview
  useEffect(() => {
    setIsLoading(true);
    setError(null);
    setPresignedUrl('');

    const token = localStorage.getItem('auth_token');
    if (!token) {
      setError('Not authenticated');
      setIsLoading(false);
      return;
    }

    // Fetch JSON response with presigned URL
    fetch(viewUrl, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    })
      .then(async (res) => {
        if (!res.ok) {
          throw new Error(`Failed to load file (${res.status})`);
        }
        const data = await res.json();
        const s3Url = data.downloadUrl;
        
        if (!s3Url) {
          throw new Error('No presigned URL received');
        }

        console.log('File type:', file.contentType, 'S3 URL:', s3Url);

        // Set the presigned URL - browser will handle rendering
        setPresignedUrl(s3Url);
      })
      .catch((err) => {
        console.error('Preview error:', err);
        setError(err.message || 'Failed to load file content');
      })
      .finally(() => setIsLoading(false));
  }, [file.id, viewUrl, file.contentType]);

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      } else if (e.key === 'ArrowLeft' && hasPrevious && onNavigate) {
        onNavigate(previewableFiles[currentIndex - 1]!.id);
      } else if (e.key === 'ArrowRight' && hasNext && onNavigate) {
        onNavigate(previewableFiles[currentIndex + 1]!.id);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose, hasPrevious, hasNext, currentIndex, previewableFiles, onNavigate]);

  const renderPreview = () => {
    if (error) {
      return (
        <div className="flex items-center justify-center h-96 text-red-500">
          <div className="text-center">
            <FileX size={48} className="mx-auto mb-4" />
            <p>{error}</p>
          </div>
        </div>
      );
    }

    if (isLoading) {
      return (
        <div className="flex items-center justify-center h-96">
          <div className="text-gray-500">Loading preview...</div>
        </div>
      );
    }

    if (isImage && presignedUrl) {
      return (
        <div className="relative flex items-center justify-center bg-gray-100 rounded-lg" style={{ minHeight: '400px' }}>
          <img
            src={presignedUrl}
            alt={file.originalFilename}
            className="max-h-[70vh] max-w-full object-contain rounded"
            referrerPolicy="no-referrer"
            onError={() => setError('Failed to load image')}
          />
          
          {/* Navigation arrows for images */}
          {canNavigate && hasPrevious && (
            <button
              onClick={() => onNavigate?.(previewableFiles[currentIndex - 1]!.id)}
              className="absolute left-4 top-1/2 -translate-y-1/2 bg-black bg-opacity-50 text-white p-3 rounded-full hover:bg-opacity-75 transition"
              title="Previous image"
            >
              <ChevronLeft size={24} />
            </button>
          )}
          
          {canNavigate && hasNext && (
            <button
              onClick={() => onNavigate?.(previewableFiles[currentIndex + 1]!.id)}
              className="absolute right-4 top-1/2 -translate-y-1/2 bg-black bg-opacity-50 text-white p-3 rounded-full hover:bg-opacity-75 transition"
              title="Next image"
            >
              <ChevronRight size={24} />
            </button>
          )}
        </div>
      );
    }

    if (isPdf && presignedUrl) {
      // Add parameters to hide all PDF viewer UI (like Google Drive)
      // toolbar=0: hide toolbar, navpanes=0: hide sidebar, scrollbar=1: keep scrollbar
      const separator = presignedUrl.includes('#') ? '&' : '#';
      const pdfUrl = `${presignedUrl}${separator}toolbar=0&navpanes=0&scrollbar=1`;
      
      return (
        <div className="flex flex-col h-full bg-gray-100">
          <iframe
            src={pdfUrl}
            title={file.originalFilename}
            className="w-full h-full border-0 rounded"
            style={{ minHeight: '70vh' }}
            referrerPolicy="no-referrer"
            onError={() => setError('Failed to load PDF')}
          />
        </div>
      );
    }

    if (isText && presignedUrl) {
      // Use iframe for text files - let the browser handle rendering
      return (
        <div className="flex flex-col h-full bg-gray-100">
          <iframe
            src={presignedUrl}
            title={file.originalFilename}
            className="w-full h-full border-0 rounded bg-white"
            style={{ minHeight: '70vh' }}
            sandbox=""
            referrerPolicy="no-referrer"
            onError={() => setError('Failed to load text file')}
          />
        </div>
      );
    }

    if (isVideo && presignedUrl) {
      return (
        <video
          src={presignedUrl}
          controls
          className="w-full rounded-lg"
          style={{ maxHeight: '70vh' }}
          controlsList="nodownload"
          onError={() => setError('Failed to load video')}
        >
          Your browser does not support video playback.
        </video>
      );
    }

    if (isAudio && presignedUrl) {
      return (
        <div className="flex items-center justify-center h-96">
          <audio
            src={presignedUrl}
            controls
            className="w-full max-w-md"
            controlsList="nodownload"
            onError={() => setError('Failed to load audio')}
          >
            Your browser does not support audio playback.
          </audio>
        </div>
      );
    }

    // Unsupported file type
    return (
      <div className="flex flex-col items-center justify-center h-96 text-gray-600">
        <FileX size={64} className="mb-4 text-gray-400" />
        <h3 className="text-lg font-semibold mb-2">No preview available</h3>
        <p className="text-sm text-gray-500">
          We don't support previewing <strong>{typeInfo.label}</strong> files yet.
        </p>
        <p className="text-xs text-gray-400 mt-2">
          Use the Download button below to view this file.
        </p>
      </div>
    );
  };

  return (
    <div
      className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-lg shadow-2xl w-full max-w-5xl flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b px-6 py-4">
          <div className="flex-1 min-w-0">
            <h2 className="text-lg font-semibold truncate" title={file.originalFilename}>
              {file.originalFilename}
            </h2>
            <p className="text-sm text-gray-500">{typeInfo.label}</p>
          </div>
          <button
            onClick={onClose}
            className="ml-4 text-gray-400 hover:text-gray-600 transition"
            title="Close (Esc)"
          >
            <X size={24} />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-auto p-6">
          {renderPreview()}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between border-t px-6 py-4 bg-gray-50">
          <div className="text-sm text-gray-500">
            {canNavigate && (
              <span>
                {currentIndex + 1} of {previewableFiles.length} images
              </span>
            )}
          </div>
          <div className="flex gap-3">
            {FEATURE_FLAGS.ENABLE_COPY_LINK && (
              <button
                onClick={() => onCopyLink(file.id)}
                className="btn-secondary flex items-center gap-2"
              >
                <LinkIcon size={16} />
                Copy Link
              </button>
            )}
            <button
              onClick={() => onDownload(file.id)}
              className="btn-primary flex items-center gap-2"
            >
              <Download size={16} />
              Download
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

