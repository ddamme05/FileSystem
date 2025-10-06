import { use } from 'react';
import { AuthContext } from '@/contexts/AuthContext.types';

export function useAuth() {
  const context = use(AuthContext); // React 19 use() API
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}

