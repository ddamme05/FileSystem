import {use} from 'react';

import {UploadContext} from '@/contexts/UploadContext';

export function useUploadContext() {
    const context = use(UploadContext);
    if (!context) {
        throw new Error('useUploadContext must be used within UploadProvider');
    }
    return context;
}






