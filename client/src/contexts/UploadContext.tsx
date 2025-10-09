/* eslint-disable react-refresh/only-export-components */
import type {ReactNode} from 'react';
import {createContext, useCallback, useEffect, useState} from 'react';

export type UploadErrorType = 'too_large' | 'network' | 'forbidden' | 'duplicate' | 'invalid_type' | 'unknown';

export interface UploadItem {
    id: string;
    file: File;
    progress: number;
    status: 'uploading' | 'success' | 'error' | 'cancelled';
    error?: string;
    errorType?: UploadErrorType;
    cancel?: () => void;
    fileId?: number;
    timestamp: number; // When upload was added
}

interface UploadContextValue {
    uploads: UploadItem[];
    addUpload: (id: string, file: File, cancel: () => void) => void;
    updateProgress: (id: string, progress: number) => void;
    setUploadSuccess: (id: string, fileId: number) => void;
    setUploadError: (id: string, error: string, errorType?: UploadErrorType) => void;
    setUploadCancelled: (id: string) => void;
    removeUpload: (id: string) => void;
    clearCompleted: () => void;
}

export const UploadContext = createContext<UploadContextValue | null>(null);

const AUTO_CLEAR_DELAY = 30000; // 30 seconds

export function UploadProvider({children}: { children: ReactNode }) {
    const [uploads, setUploads] = useState<UploadItem[]>([]);

    // Auto-clear completed uploads after 30 seconds
    useEffect(() => {
        const interval = setInterval(() => {
            const now = Date.now();
            setUploads((prev) => 
                prev.filter((upload) => {
                    // Keep if still uploading
                    if (upload.status === 'uploading') return true;
                    
                    // Keep if completed/failed within last 30 seconds
                    const age = now - upload.timestamp;
                    return age < AUTO_CLEAR_DELAY;
                })
            );
        }, 5000); // Check every 5 seconds

        return () => clearInterval(interval);
    }, []);

    const addUpload = useCallback((id: string, file: File, cancel: () => void): void => {
        const upload: UploadItem = {
            id,
            file,
            progress: 0,
            status: 'uploading',
            cancel,
            timestamp: Date.now(),
        };
        setUploads((prev) => [upload, ...prev]); // Add to beginning
    }, []);

    const updateProgress = useCallback((id: string, progress: number) => {
        setUploads((prev) =>
            prev.map((u) => (u.id === id ? {...u, progress} : u))
        );
    }, []);

    const setUploadSuccess = useCallback((id: string, fileId: number) => {
        setUploads((prev) =>
            prev.map((u) =>
                u.id === id ? {...u, status: 'success', progress: 100, fileId, timestamp: Date.now()} : u
            )
        );
    }, []);

    const setUploadError = useCallback((id: string, error: string, errorType: UploadErrorType = 'unknown') => {
        setUploads((prev) =>
            prev.map((u) => (u.id === id ? {...u, status: 'error', error, errorType, timestamp: Date.now()} : u))
        );
    }, []);

    const setUploadCancelled = useCallback((id: string) => {
        setUploads((prev) =>
            prev.map((u) => (u.id === id ? {...u, status: 'cancelled', timestamp: Date.now()} : u))
        );
    }, []);

    const removeUpload = useCallback((id: string) => {
        setUploads((prev) => prev.filter((u) => u.id !== id));
    }, []);

    const clearCompleted = useCallback(() => {
        setUploads((prev) =>
            prev.filter((u) => u.status === 'uploading')
        );
    }, []);

    const value = {
        uploads,
        addUpload,
        updateProgress,
        setUploadSuccess,
        setUploadError,
        setUploadCancelled,
        removeUpload,
        clearCompleted,
    };

    return <UploadContext.Provider value={value}>{children}</UploadContext.Provider>;
}