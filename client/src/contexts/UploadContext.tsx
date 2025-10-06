import { createContext, useState, useCallback } from 'react';
import type { ReactNode } from 'react';

export interface UploadItem {
  id: string;
  file: File;
  progress: number;
  status: 'uploading' | 'success' | 'error' | 'cancelled';
  error?: string;
  cancel?: () => void;
  fileId?: number; // Backend file ID when upload completes
}

interface UploadContextValue {
  uploads: UploadItem[];
  addUpload: (id: string, file: File, cancel: () => void) => void;
  updateProgress: (id: string, progress: number) => void;
  setUploadSuccess: (id: string, fileId: number) => void;
  setUploadError: (id: string, error: string) => void;
  setUploadCancelled: (id: string) => void;
  removeUpload: (id: string) => void;
  clearCompleted: () => void;
}

export const UploadContext = createContext<UploadContextValue | null>(null);

export function UploadProvider({ children }: { children: ReactNode }) {
  const [uploads, setUploads] = useState<UploadItem[]>([]);

  const addUpload = useCallback((id: string, file: File, cancel: () => void): void => {
    const upload: UploadItem = {
      id,
      file,
      progress: 0,
      status: 'uploading',
      cancel,
    };
    setUploads((prev) => [upload, ...prev]); // Add to beginning
  }, []);

  const updateProgress = useCallback((id: string, progress: number) => {
    setUploads((prev) =>
      prev.map((u) => (u.id === id ? { ...u, progress } : u))
    );
  }, []);

  const setUploadSuccess = useCallback((id: string, fileId: number) => {
    setUploads((prev) =>
      prev.map((u) =>
        u.id === id ? { ...u, status: 'success', progress: 100, fileId } : u
      )
    );
  }, []);

  const setUploadError = useCallback((id: string, error: string) => {
    setUploads((prev) =>
      prev.map((u) => (u.id === id ? { ...u, status: 'error', error } : u))
    );
  }, []);

  const setUploadCancelled = useCallback((id: string) => {
    setUploads((prev) =>
      prev.map((u) => (u.id === id ? { ...u, status: 'cancelled' } : u))
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

