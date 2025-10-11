import {AlertTriangle} from 'lucide-react';

interface DuplicateFileDialogProps {
    fileName: string;
    onReplace: () => void;
    onKeepBoth: () => void;
    onCancel: () => void;
}

export function DuplicateFileDialog({fileName, onReplace, onKeepBoth, onCancel}: DuplicateFileDialogProps) {
    return (
        <div
            className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
            onClick={onCancel}
        >
            <div
                className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex items-start gap-3 p-6 border-b border-gray-200">
                    <AlertTriangle className="w-5 h-5 text-amber-500 mt-0.5 flex-shrink-0"/>
                    <div>
                        <h2 className="text-lg font-semibold text-gray-900">File Already Exists</h2>
                        <p className="text-sm text-gray-600 mt-1">
                            You already have a file named <span className="font-medium">{fileName}</span>
                        </p>
                    </div>
                </div>

                <div className="p-6">
                    <p className="text-sm text-gray-700 mb-4">
                        What would you like to do?
                    </p>
                    <div className="space-y-3">
                        <button
                            onClick={onReplace}
                            className="w-full text-left px-4 py-3 border border-gray-200 rounded-lg hover:bg-gray-50 hover:border-gray-300 transition"
                        >
                            <div className="font-medium text-gray-900">Replace</div>
                            <div className="text-xs text-gray-500 mt-0.5">
                                Delete the old file and upload this one
                            </div>
                        </button>
                        <button
                            onClick={onKeepBoth}
                            className="w-full text-left px-4 py-3 border border-gray-200 rounded-lg hover:bg-gray-50 hover:border-gray-300 transition"
                        >
                            <div className="font-medium text-gray-900">Keep Both</div>
                            <div className="text-xs text-gray-500 mt-0.5">
                                Upload with a new name (e.g., "{getNewFileName(fileName)}")
                            </div>
                        </button>
                    </div>
                </div>

                <div className="px-6 py-4 bg-gray-50 rounded-b-lg flex justify-end">
                    <button
                        onClick={onCancel}
                        className="px-4 py-2 text-sm text-gray-700 hover:text-gray-900"
                    >
                        Cancel
                    </button>
                </div>
            </div>
        </div>
    );
}

/**
 * Generate a new filename by adding a number suffix
 * Example: "document.pdf" -> "document-1.pdf"
 */
function getNewFileName(fileName: string): string {
    const lastDot = fileName.lastIndexOf('.');
    if (lastDot === -1) {
        return `${fileName}-1`;
    }
    const name = fileName.substring(0, lastDot);
    const ext = fileName.substring(lastDot);
    return `${name}-1${ext}`;
}
