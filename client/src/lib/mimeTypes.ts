/**
 * Maps MIME types to user-friendly file type names
 * Similar to Google Drive's file type display
 */

interface FileTypeInfo {
    label: string;
    category: 'document' | 'spreadsheet' | 'presentation' | 'image' | 'video' | 'audio' | 'archive' | 'code' | 'text' | 'executable' | 'other';
    previewable: boolean;
}

const MIME_TYPE_MAP: Record<string, FileTypeInfo> = {
    // Microsoft Office Documents
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': {
        label: 'Word Document',
        category: 'document',
        previewable: false,
    },
    'application/msword': {
        label: 'Word Document',
        category: 'document',
        previewable: false,
    },
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': {
        label: 'Excel Spreadsheet',
        category: 'spreadsheet',
        previewable: false,
    },
    'application/vnd.ms-excel': {
        label: 'Excel Spreadsheet',
        category: 'spreadsheet',
        previewable: false,
    },
    'application/vnd.openxmlformats-officedocument.presentationml.presentation': {
        label: 'PowerPoint Presentation',
        category: 'presentation',
        previewable: false,
    },
    'application/vnd.ms-powerpoint': {
        label: 'PowerPoint Presentation',
        category: 'presentation',
        previewable: false,
    },

    // Google Workspace
    'application/vnd.google-apps.document': {
        label: 'Google Doc',
        category: 'document',
        previewable: false,
    },
    'application/vnd.google-apps.spreadsheet': {
        label: 'Google Sheet',
        category: 'spreadsheet',
        previewable: false,
    },
    'application/vnd.google-apps.presentation': {
        label: 'Google Slides',
        category: 'presentation',
        previewable: false,
    },

    // PDFs
    'application/pdf': {
        label: 'PDF Document',
        category: 'document',
        previewable: true,
    },

    // Images
    'image/jpeg': {label: 'JPEG Image', category: 'image', previewable: true},
    'image/jpg': {label: 'JPEG Image', category: 'image', previewable: true},
    'image/png': {label: 'PNG Image', category: 'image', previewable: true},
    'image/gif': {label: 'GIF Image', category: 'image', previewable: true},
    'image/webp': {label: 'WebP Image', category: 'image', previewable: true},
    'image/svg+xml': {label: 'SVG Image', category: 'image', previewable: true},
    'image/bmp': {label: 'BMP Image', category: 'image', previewable: true},

    // Videos
    'video/mp4': {label: 'MP4 Video', category: 'video', previewable: true},
    'video/webm': {label: 'WebM Video', category: 'video', previewable: true},
    'video/ogg': {label: 'OGG Video', category: 'video', previewable: true},
    'video/quicktime': {label: 'QuickTime Video', category: 'video', previewable: true},

    // Audio
    'audio/mpeg': {label: 'MP3 Audio', category: 'audio', previewable: true},
    'audio/mp3': {label: 'MP3 Audio', category: 'audio', previewable: true},
    'audio/wav': {label: 'WAV Audio', category: 'audio', previewable: true},
    'audio/ogg': {label: 'OGG Audio', category: 'audio', previewable: true},
    'audio/webm': {label: 'WebM Audio', category: 'audio', previewable: true},

    // Text & Code
    'text/plain': {label: 'Text File', category: 'text', previewable: true},
    'text/html': {label: 'HTML File', category: 'code', previewable: true},
    'text/css': {label: 'CSS File', category: 'code', previewable: true},
    'text/javascript': {label: 'JavaScript File', category: 'code', previewable: true},
    'application/javascript': {label: 'JavaScript File', category: 'code', previewable: true},
    'application/json': {label: 'JSON File', category: 'code', previewable: true},
    'application/xml': {label: 'XML File', category: 'code', previewable: true},
    'text/xml': {label: 'XML File', category: 'code', previewable: true},
    'text/markdown': {label: 'Markdown File', category: 'text', previewable: true},
    'text/csv': {label: 'CSV File', category: 'spreadsheet', previewable: true},

    // Archives
    'application/zip': {label: 'ZIP Archive', category: 'archive', previewable: false},
    'application/x-zip-compressed': {label: 'ZIP Archive', category: 'archive', previewable: false},
    'application/x-rar-compressed': {label: 'RAR Archive', category: 'archive', previewable: false},
    'application/x-7z-compressed': {label: '7Z Archive', category: 'archive', previewable: false},
    'application/gzip': {label: 'GZIP Archive', category: 'archive', previewable: false},
    'application/x-tar': {label: 'TAR Archive', category: 'archive', previewable: false},

    // Executables & Installers
    'application/x-msdownload': {label: 'Executable', category: 'executable', previewable: false},
    'application/x-executable': {label: 'Executable', category: 'executable', previewable: false},
    'application/x-dosexec': {label: 'DOS Executable', category: 'executable', previewable: false},
    'application/x-msi': {label: 'Windows Installer', category: 'executable', previewable: false},
    'application/vnd.microsoft.portable-executable': {
        label: 'Windows Program',
        category: 'executable',
        previewable: false
    },
    'application/x-mach-binary': {label: 'Mac Executable', category: 'executable', previewable: false},
    'application/x-elf': {label: 'Linux Executable', category: 'executable', previewable: false},

    // Generic binary
    'application/octet-stream': {label: 'Binary File', category: 'other', previewable: false},
};

/**
 * Get user-friendly file type information from MIME type
 */
export function getFileTypeInfo(mimeType: string): FileTypeInfo {
    // Exact match
    const exactMatch = MIME_TYPE_MAP[mimeType];
    if (exactMatch) return exactMatch;

    // Generic fallbacks based on MIME type prefix
    if (mimeType.startsWith('image/')) {
        return {label: 'Image', category: 'image', previewable: true};
    }
    if (mimeType.startsWith('video/')) {
        return {label: 'Video', category: 'video', previewable: true};
    }
    if (mimeType.startsWith('audio/')) {
        return {label: 'Audio', category: 'audio', previewable: true};
    }
    if (mimeType.startsWith('text/')) {
        return {label: 'Text File', category: 'text', previewable: true};
    }
    if (mimeType.startsWith('application/')) {
        return {label: 'Application File', category: 'other', previewable: false};
    }

    // Ultimate fallback
    return {label: 'File', category: 'other', previewable: false};
}

/**
 * Get just the user-friendly label
 */
export function formatFileType(mimeType: string): string {
    return getFileTypeInfo(mimeType).label;
}

/**
 * Check if a file type can be previewed
 */
export function isPreviewable(mimeType: string): boolean {
    return getFileTypeInfo(mimeType).previewable;
}