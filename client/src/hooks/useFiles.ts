import {useQuery} from '@tanstack/react-query';

import {api} from '@/api/client';
import type {FileMetadata, PagedResponse} from '@/types/file';

export function useFiles(page: number, size: number) {
    return useQuery({
        queryKey: ['files', page, size],
        queryFn: () =>
            api.get<PagedResponse<FileMetadata>>(
                `/api/v1/files?page=${page}&size=${size}`
            ),
    });
}

