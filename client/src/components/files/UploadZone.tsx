import {useState} from 'react';
import {useDropzone} from 'react-dropzone';
import {Upload as UploadIcon} from 'lucide-react';
import {toast} from 'sonner';
import {v4 as uuidv4} from 'uuid';

import {useUploadFile} from '@/hooks/useUploadFile';
import {useUploadContext} from '@/hooks/useUploadContext';
import {useFiles} from '@/hooks/useFiles';
import {useDeleteFile} from '@/hooks/useDeleteFile';
import {DuplicateFileDialog} from './DuplicateFileDialog';
import type {UploadErrorType} from '@/contexts/UploadContext';

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

interface PendingDuplicate {
    file: File;
    existingFileId: number;
}

export function UploadZone() {
    const {uploadFile} = useUploadFile();
    const {addUpload, updateProgress, setUploadSuccess, setUploadError, setUploadCancelled} = useUploadContext();
    const {data: filesData} = useFiles(0, 1000); // Fetch all files for duplicate check
    const deleteMutation = useDeleteFile();

    const [pendingDuplicate, setPendingDuplicate] = useState<PendingDuplicate | null>(null);

    const {getRootProps, getInputProps, isDragActive} = useDropzone({
        onDrop: (files) => files.forEach((file) => handleUpload(file)),
        multiple: true,
    });

    /**
     * Categorize upload error based on error message
     */
    function categorizeError(error: Error): UploadErrorType {
        const msg = error.message.toLowerCase();

        if (msg.includes('too large') || msg.includes('file size') || msg.includes('10 mb')) {
            return 'too_large';
        }
        if (msg.includes('network') || msg.includes('failed to fetch')) {
            return 'network';
        }
        if (msg.includes('forbidden') || msg.includes('unauthorized') || msg.includes('401') || msg.includes('403')) {
            return 'forbidden';
        }
        if (msg.includes('duplicate') || msg.includes('already exists')) {
            return 'duplicate';
        }
        if (msg.includes('invalid') || msg.includes('type')) {
            return 'invalid_type';
        }

        return 'unknown';
    }

    /**
     * Check if file with same name already exists
     */
    function checkDuplicate(fileName: string): number | null {
        if (!filesData?.files) return null;

        const existing = filesData.files.find(
            f => f.originalFilename.toLowerCase() === fileName.toLowerCase()
        );

        return existing ? existing.id : null;
    }

    /**
     * Generate a unique filename by adding a number suffix
     */
    function generateUniqueFileName(originalName: string): string {
        if (!filesData?.files) return originalName;

        const lastDot = originalName.lastIndexOf('.');
        const name = lastDot === -1 ? originalName : originalName.substring(0, lastDot);
        const ext = lastDot === -1 ? '' : originalName.substring(lastDot);

        let counter = 1;
        let newName = originalName;
        const MAX_ATTEMPTS = 9999; // Safeguard against infinite loop

        while (filesData.files.some(f => f.originalFilename.toLowerCase() === newName.toLowerCase()) && counter < MAX_ATTEMPTS) {
            newName = `${name}-${counter}${ext}`;
            counter++;
        }

        return newName;
    }

    async function handleUpload(file: File, skipDuplicateCheck = false) {
        // Client-side validation: file size
        if (file.size === 0) {
            toast.error(`File is empty: ${file.name}`);
            return;
        }

        if (file.size > MAX_FILE_SIZE) {
            toast.error(`File too large: ${file.name} (max 10 MB)`);
            return;
        }

        // Check for duplicates (unless skipping)
        if (!skipDuplicateCheck) {
            const duplicateId = checkDuplicate(file.name);
            if (duplicateId !== null) {
                setPendingDuplicate({file, existingFileId: duplicateId});
                return;
            }
        }

        // Proceed with upload
        await performUpload(file);
    }

    async function performUpload(file: File) {
        const uploadId = uuidv4();

        const upload = uploadFile(file, (progress) => {
            updateProgress(uploadId, progress.percent);
        });

        addUpload(uploadId, file, upload.cancel);

        try {
            const result = await upload.promise;
            setUploadSuccess(uploadId, result.id);
        } catch (error) {
            const isCancelled = (error as Error).message === 'Upload cancelled';
            if (isCancelled) {
                setUploadCancelled(uploadId);
            } else {
                const errorType = categorizeError(error as Error);
                setUploadError(uploadId, (error as Error).message, errorType);
                toast.error(`Failed: ${file.name} - ${(error as Error).message}`);
            }
        }
    }

    async function handleReplace() {
        if (!pendingDuplicate) return;

        try {
            // Delete the old file first
            await deleteMutation.mutateAsync(pendingDuplicate.existingFileId);

            // Upload the new file
            await performUpload(pendingDuplicate.file);

            toast.success(`Replaced ${pendingDuplicate.file.name}`);
        } catch (error) {
            toast.error(`Failed to replace: ${(error as Error).message}`);
        } finally {
            setPendingDuplicate(null);
        }
    }

    async function handleKeepBoth() {
        if (!pendingDuplicate) return;

        // Generate a unique name
        const newFileName = generateUniqueFileName(pendingDuplicate.file.name);

        // Create a new File object with the new name
        const renamedFile = new File([pendingDuplicate.file], newFileName, {
            type: pendingDuplicate.file.type,
            lastModified: pendingDuplicate.file.lastModified,
        });

        // Upload with new name (skip duplicate check since we just generated unique name)
        await performUpload(renamedFile);

        toast.success(`Uploaded as ${newFileName}`);
        setPendingDuplicate(null);
    }

    return (
        <>
            <div
                {...getRootProps()}
                className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition ${
                    isDragActive ? 'border-blue-500 bg-blue-50' : 'border-gray-300 hover:border-gray-400'
                }`}
            >
                <input {...getInputProps()} />
                <UploadIcon className="mx-auto h-12 w-12 text-gray-400"/>
                <p className="mt-2 text-sm text-gray-600">
                    Drag & drop files here, or click to select
                </p>
                <p className="text-xs text-gray-500 mt-1">Max 10 MB per file</p>
            </div>

            {/* Duplicate File Dialog */}
            {pendingDuplicate && (
                <DuplicateFileDialog
                    fileName={pendingDuplicate.file.name}
                    onReplace={handleReplace}
                    onKeepBoth={handleKeepBoth}
                    onCancel={() => setPendingDuplicate(null)}
                />
            )}
        </>
    );
}