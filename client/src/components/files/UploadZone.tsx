import {useDropzone} from 'react-dropzone';
import {Upload as UploadIcon} from 'lucide-react';
import {toast} from 'sonner';
import {v4 as uuidv4} from 'uuid';

import {useUploadFile} from '@/hooks/useUploadFile';
import {useUploadContext} from '@/hooks/useUploadContext';

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

export function UploadZone() {
    const {uploadFile} = useUploadFile();
    const {addUpload, updateProgress, setUploadSuccess, setUploadError, setUploadCancelled} = useUploadContext();

    const {getRootProps, getInputProps, isDragActive} = useDropzone({
        onDrop: (files) => files.forEach(handleUpload),
        multiple: true,
    });

    async function handleUpload(file: File) {
        // Client-side validation
        if (file.size > MAX_FILE_SIZE) {
            toast.error(`File too large: ${file.name} (max 10 MB)`);
            return;
        }

        // Generate ID upfront so the callback can reference it
        const uploadId = uuidv4();

        const upload = uploadFile(file, (progress) => {
            updateProgress(uploadId, progress.percent);
        });

        // Add to global upload state with the ID
        addUpload(uploadId, file, upload.cancel);

        try {
            const result = await upload.promise;
            setUploadSuccess(uploadId, result.id);
            // Success is shown in the upload panel - no need for toast
        } catch (error) {
            const isCancelled = (error as Error).message === 'Upload cancelled';
            if (isCancelled) {
                setUploadCancelled(uploadId);
            } else {
                setUploadError(uploadId, (error as Error).message);
                toast.error(`Failed: ${file.name} - ${(error as Error).message}`);
            }
        }
    }

    return (
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
    );
}
