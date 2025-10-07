export interface FileMetadata {
    id: number;
    originalFilename: string;
    size: number;
    contentType: string;
    uploadTimestamp: string;
}

export interface PagedResponse<T> {
    files: T[];
    currentPage: number;
    totalPages: number;
    totalElements: number;
    hasNext: boolean;
    hasPrevious: boolean;
}

