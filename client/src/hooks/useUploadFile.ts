import {useQueryClient} from '@tanstack/react-query';

import {useAuth} from './useAuth';
import type {FileMetadata} from '@/types/file';

export interface UploadProgress {
    loaded: number;
    total: number;
    percent: number;
}

export interface UploadHandle {
    promise: Promise<FileMetadata>;
    cancel: () => void;
}

export function useUploadFile() {
    const {getToken} = useAuth();
    const queryClient = useQueryClient();

    function uploadFile(
        file: File,
        onProgress?: (progress: UploadProgress) => void
    ): UploadHandle {
        const xhr = new XMLHttpRequest();
        const formData = new FormData();
        formData.append('file', file);

        const promise = new Promise<FileMetadata>((resolve, reject) => {
            xhr.upload.addEventListener('progress', (e) => {
                if (e.lengthComputable && onProgress) {
                    onProgress({
                        loaded: e.loaded,
                        total: e.total,
                        percent: (e.loaded / e.total) * 100,
                    });
                }
            });

            xhr.addEventListener('load', () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    try {
                        const response = JSON.parse(xhr.responseText);
                        queryClient.invalidateQueries({queryKey: ['files']});
                        resolve(response);
                    } catch {
                        reject(new Error('Invalid JSON response'));
                    }
                } else {
                    try {
                        const error = JSON.parse(xhr.responseText);
                        reject(new Error(error.message || 'Upload failed'));
                    } catch {
                        reject(new Error(`Upload failed: ${xhr.status} ${xhr.statusText}`));
                    }
                }
            });

            xhr.addEventListener('error', () => reject(new Error('Network error')));
            xhr.addEventListener('abort', () => reject(new Error('Upload cancelled')));

            // ✅ open() BEFORE setRequestHeader()
            xhr.open('POST', '/api/v1/files/upload');

            // ✅ Set auth header AFTER open()
            const token = getToken();
            if (token) {
                xhr.setRequestHeader('Authorization', `Bearer ${token}`);
            }

            xhr.send(formData);
        });

        // ✅ Return object with promise AND cancel
        return {
            promise,
            cancel: () => xhr.abort(),
        };
    }

    return {uploadFile};
}

