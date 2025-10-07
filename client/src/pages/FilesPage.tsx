import { useState, useMemo } from 'react';
import { toast } from 'sonner';

import { useFiles } from '@/hooks/useFiles';
import { useDownloadFile } from '@/hooks/useDownloadFile';
import { useDeleteFile } from '@/hooks/useDeleteFile';
import { UploadZone } from '@/components/files/UploadZone';
import { FilesTable } from '@/components/files/FilesTable';
import { FileFilters, type SortField, type SortOrder, type FileTypeFilter } from '@/components/files/FileFilters';
import { PaginationBar } from '@/components/files/PaginationBar';
import { DeleteDialog } from '@/components/files/DeleteDialog';
import { PreviewModal } from '@/components/files/PreviewModal';
import { getFileTypeInfo } from '@/lib/mimeTypes';

export function FilesPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [deleteTarget, setDeleteTarget] = useState<{ id: number; name: string } | null>(null);
  const [previewFileId, setPreviewFileId] = useState<number | null>(null);
  
  // Filter & Sort state
  const [sortField, setSortField] = useState<SortField>('uploadTimestamp');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');
  const [typeFilter, setTypeFilter] = useState<FileTypeFilter>('all');
  const [searchQuery, setSearchQuery] = useState('');

  const { data, isLoading, error } = useFiles(page, size);
  const { downloadFile } = useDownloadFile();
  const deleteMutation = useDeleteFile();

  // ⚠️ ARCHITECTURE NOTE: Client-side filtering/sorting
  // Currently filtering and sorting happen CLIENT-SIDE on the fetched page slice.
  // This can cause UX issues:
  //   - Page counts may appear wrong (filtering after pagination)
  //   - Items can "shuffle" across page boundaries
  //   - Search only finds items on current page
  // 
  // FUTURE IMPROVEMENT: Move these operations to the backend
  //   - Update useFiles(page, size, sortField, sortOrder, typeFilter, searchQuery)
  //   - Pass as query params: ?page=0&size=20&sort=name&order=asc&type=image&q=vacation
  //   - Backend returns filtered/sorted slice + correct totalElements/totalPages
  //   - Provides proper pagination UX and enables search across all files
  const filteredAndSortedFiles = useMemo(() => {
    let result = [...(data?.files || [])];

    // Apply search filter
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      result = result.filter((file) =>
        file.originalFilename.toLowerCase().includes(query)
      );
    }

    // Apply type filter
    if (typeFilter !== 'all') {
      result = result.filter((file) => {
        const typeInfo = getFileTypeInfo(file.contentType);
        // Handle combined "text" filter (text + code)
        if (typeFilter === 'text') {
          return typeInfo.category === 'text' || typeInfo.category === 'code';
        }
        return typeInfo.category === typeFilter;
      });
    }

    // Apply sorting
    result.sort((a, b) => {
      let comparison = 0;

      switch (sortField) {
        case 'name':
          comparison = a.originalFilename.localeCompare(b.originalFilename);
          break;
        case 'size':
          comparison = a.size - b.size;
          break;
        case 'type':
          comparison = a.contentType.localeCompare(b.contentType);
          break;
        case 'uploadTimestamp':
          comparison = new Date(a.uploadTimestamp).getTime() - new Date(b.uploadTimestamp).getTime();
          break;
      }

      return sortOrder === 'asc' ? comparison : -comparison;
    });

    return result;
  }, [data?.files, searchQuery, typeFilter, sortField, sortOrder]);

  const fileToPreview = filteredAndSortedFiles.find((f) => f.id === previewFileId) || null;

  const handleSortChange = (field: SortField) => {
    if (field === sortField) {
      // Toggle order if clicking same field
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      // Default to desc for new field (newest/largest first)
      setSortField(field);
      setSortOrder(field === 'uploadTimestamp' ? 'desc' : 'asc');
    }
  };

  async function handleDownload(fileId: number) {
    try {
      await downloadFile(fileId);
    } catch (error: unknown) {
      toast.error(error instanceof Error ? error.message : 'Failed to download file');
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;

    try {
      await deleteMutation.mutateAsync(deleteTarget.id);
      toast.success('File deleted');
      setDeleteTarget(null);
    } catch (error) {
      toast.error((error as Error).message);
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-gray-500">Loading...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-red-500">Error: {(error as Error).message}</div>
      </div>
    );
  }

  return (
    <>
      <div className="space-y-8">
        <section>
          <h2 className="text-2xl font-bold mb-4">Upload Files</h2>
          <UploadZone />
        </section>

        <section>
          <h2 className="text-2xl font-bold mb-4">Your Files</h2>
          
          <FileFilters
            sortField={sortField}
            sortOrder={sortOrder}
            typeFilter={typeFilter}
            searchQuery={searchQuery}
            onSortChange={handleSortChange}
            onTypeFilterChange={setTypeFilter}
            onSearchChange={setSearchQuery}
          />

          <div className="rounded-lg shadow">
            <FilesTable
              files={filteredAndSortedFiles}
              onPreview={(id) => setPreviewFileId(id)}
              onDownload={handleDownload}
              onDelete={(id) => {
                const file = filteredAndSortedFiles.find((f) => f.id === id);
                if (file) setDeleteTarget({ id, name: file.originalFilename });
              }}
              onCopyLink={() => {
                toast.info('Copy link feature coming soon!');
              }}
            />
            {data && data.totalPages > 0 && (
              <PaginationBar
                currentPage={data.currentPage}
                totalPages={data.totalPages}
                pageSize={size}
                onPageChange={setPage}
                onPageSizeChange={(newSize) => {
                  setSize(newSize);
                  setPage(0);
                }}
              />
            )}
          </div>
        </section>
      </div>

      {deleteTarget && (
        <DeleteDialog
          filename={deleteTarget.name}
          onConfirm={handleDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      )}

      {fileToPreview && (
        <PreviewModal
          file={fileToPreview}
          files={filteredAndSortedFiles}
          onClose={() => setPreviewFileId(null)}
          onDownload={handleDownload}
          onCopyLink={() => toast.info('Copy link feature coming soon!')}
          onNavigate={(id) => setPreviewFileId(id)}
        />
      )}
    </>
  );
}
