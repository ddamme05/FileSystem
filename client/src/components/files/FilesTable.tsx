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
            <div className="text-center py-12 text-gray-500">
                <p>No files yet. Upload your first file above!</p>
            </div>
        );
    }

    return (
        <div className="bg-white rounded-lg shadow overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                        Name
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                        Size
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                        Type
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                        Uploaded
                    </th>
                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">
                        Actions
                    </th>
                </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                {files.map((file) => {
                    const isHighlighted = highlightedIds.has(file.id);
                    return (
                        <tr
                            key={file.id}
                            className={`hover:bg-gray-50 transition-colors duration-1000 ${
                                isHighlighted ? 'bg-blue-50' : ''
                            }`}
                        >
                            <td className="px-6 py-4 text-sm font-medium text-gray-900">
                                <button
                                    onClick={() => onPreview(file.id)}
                                    className="text-left hover:text-blue-600 hover:underline transition truncate max-w-xs block"
                                    title={`${file.originalFilename} - Click to preview`}
                                >
                                    {file.originalFilename}
                                </button>
                            </td>
                            <td className="px-6 py-4 text-sm text-gray-500">
                                {formatFileSize(file.size)}
                            </td>
                            <td className="px-6 py-4 text-sm text-gray-500">
                                {formatFileType(file.contentType)}
                            </td>
                            <td className="px-6 py-4 text-sm text-gray-500">
                                {formatRelativeTime(file.uploadTimestamp)}
                            </td>
                            <td className="px-6 py-4 text-sm text-right space-x-2">
                                {isPreviewable(file.contentType) && (
                                    <button
                                        onClick={() => onPreview(file.id)}
                                        aria-label={`Preview ${file.originalFilename}`}
                                        title="Preview"
                                        className="inline-flex items-center text-purple-600 hover:text-purple-800"
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
                                    className="inline-flex items-center text-red-600 hover:text-red-800"
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
