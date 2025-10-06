import { useMutation, useQueryClient } from '@tanstack/react-query';

import { api } from '@/api/client';

export function useDeleteFile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (fileId: number) => api.delete(`/api/v1/files/${fileId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['files'] });
    },
  });
}

