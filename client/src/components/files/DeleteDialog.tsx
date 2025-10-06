import { useFormStatus } from 'react-dom';

function DeleteButton() {
  const { pending } = useFormStatus();
  return (
    <button
      type="submit"
      disabled={pending}
      className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed"
    >
      {pending ? 'Deleting...' : 'Delete'}
    </button>
  );
}

interface DeleteDialogProps {
  filename: string;
  onConfirm: () => Promise<void>;
  onCancel: () => void;
}

export function DeleteDialog({ filename, onConfirm, onCancel }: DeleteDialogProps) {
  async function handleSubmit() {
    await onConfirm();
  }

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 max-w-md w-full">
        <h3 className="text-lg font-bold mb-4">Delete File?</h3>
        <p className="text-gray-600 mb-6">
          Are you sure you want to delete <strong>{filename}</strong>? This action cannot be
          undone.
        </p>
        <form action={handleSubmit} className="flex gap-3 justify-end">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 border rounded hover:bg-gray-50"
          >
            Cancel
          </button>
          <DeleteButton />
        </form>
      </div>
    </div>
  );
}






