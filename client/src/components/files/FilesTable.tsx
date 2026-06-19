import {Download, Eye, Link as LinkIcon, Trash2} from 'lucide-react';
import {useEffect, useState} from 'react';

import type {FileMetadata} from '@/types/file';
import {FEATURE_FLAGS, getCopyLinkTooltip} from '@/lib/featureFlags';
import {formatFileSize, formatRelativeTime} from '@/lib/formatters';
import {formatFileType, isPreviewable} from '@/lib/mimeTypes';
import {useUploadContext} from '@/hooks/useUploadContext';

interface FilesTableProps {
    files: FileMetadata[];
    onPreview: (id: number) => void;
    onDownload: (id: number) => void;
    onDelete: (id: number) => void;
    onCopyLink: (id: number) => void;
}

export function FilesTable({files, onPreview, onDownload, onDelete, onCopyLink}: FilesTableProps) {
    const {uploads} = useUploadContext();
    const [highlightedIds, setHighlightedIds] = useState<Set<number>>(new Set());

    // Track newly uploaded files and highlight them
    useEffect(() => {
        const newFileIds = uploads
            .filter((upload) => upload.status === 'success' && upload.fileId)
            .map((upload) => upload.fileId!);

        if (newFileIds.length > 0) {
            setHighlightedIds(new Set(newFileIds));

            // Remove highlight after 3 seconds
            const timer = setTimeout(() => {
                setHighlightedIds(new Set());
            }, 3000);

            return () => clearTimeout(timer);
        }
    }, [uploads]);
    if (files.length === 0) {
        return (
            <div className="text-center py-12 text-muted">
                <p>No files yet. Upload your first file above!</p>
            </div>
        );
    }

    return (
        <div className="card overflow-hidden">
            <table className="min-w-full">
                <thead className="bg-canvas">
                <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-faint uppercase">
                        Name
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-faint uppercase">
                        Size
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-faint uppercase">
                        Type
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-faint uppercase">
                        Uploaded
                    </th>
                    <th className="px-6 py-3 text-right text-xs font-medium text-faint uppercase">
                        Actions
                    </th>
                </tr>
                </thead>
                <tbody>
                {files.map((file) => {
                    const isHighlighted = highlightedIds.has(file.id);
                    return (
                        <tr
                            key={file.id}
                            className={`border-t border-line hover:bg-canvas transition-colors duration-1000 ${
                                isHighlighted ? 'bg-accent-weak' : ''
                            }`}
                        >
                            <td className="px-6 py-4 text-sm font-medium text-ink">
                                <button
                                    onClick={() => onPreview(file.id)}
                                    className="text-left text-ink font-medium hover:text-accent hover:underline transition truncate max-w-xs block"
                                    title={`${file.originalFilename} - Click to preview`}
                                >
                                    {file.originalFilename}
                                </button>
                            </td>
                            <td className="px-6 py-4 text-sm text-muted">
                                {formatFileSize(file.size)}
                            </td>
                            <td className="px-6 py-4 text-sm text-muted">
                                {formatFileType(file.contentType)}
                            </td>
                            <td className="px-6 py-4 text-sm text-muted">
                                {formatRelativeTime(file.uploadTimestamp)}
                            </td>
                            <td className="px-6 py-4 text-sm text-right space-x-2">
                                {isPreviewable(file.contentType) && (
                                    <button
                                        onClick={() => onPreview(file.id)}
                                        aria-label={`Preview ${file.originalFilename}`}
                                        title="Preview"
                                        className="inline-flex items-center text-accent hover:text-accent-strong"
                                    >
                                        <Eye size={16}/>
                                    </button>
                                )}
                                <button
                                    onClick={() => onDownload(file.id)}
                                    aria-label={`Download ${file.originalFilename}`}
                                    title="Download"
                                    className="inline-flex items-center text-blue-600 hover:text-blue-800"
                                >
                                    <Download size={16}/>
                                </button>

                                {/* Copy Link - hidden in MVP (Option C) */}
                                {FEATURE_FLAGS.ENABLE_COPY_LINK && (
                                    <button
                                        onClick={() => onCopyLink(file.id)}
                                        aria-label={`Copy link for ${file.originalFilename}`}
                                        title={getCopyLinkTooltip()}
                                        className="inline-flex items-center text-green-600 hover:text-green-800"
                                    >
                                        <LinkIcon size={16}/>
                                    </button>
                                )}

                                <button
                                    onClick={() => onDelete(file.id)}
                                    aria-label={`Delete ${file.originalFilename}`}
                                    title="Delete"
                                    className="inline-flex items-center text-red-500 hover:text-red-700"
                                >
                                    <Trash2 size={16}/>
                                </button>
                            </td>
                        </tr>
                    );
                })}
                </tbody>
            </table>
        </div>
    );
}
