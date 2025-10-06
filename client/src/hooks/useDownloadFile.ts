import { api } from '@/api/client';

export function useDownloadFile() {
  async function downloadFile(fileId: number): Promise<void> {
    // Option C: GET presigned URL from backend (with auth), then navigate to S3
    // This sends Authorization header to our backend, then navigates to S3
    const response = await api.get<{ downloadUrl: string }>(
      `/api/v1/files/download/${fileId}/redirect`
    );
    
    // Navigate to the S3 presigned URL (no auth needed for S3)
    window.location.href = response.downloadUrl;
  }

  return { downloadFile };
}

