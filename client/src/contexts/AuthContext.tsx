import { useState, useCallback, useEffect } from 'react';
import type { ReactNode } from 'react';
import type { User } from '@/types/auth';
import { useQueryClient } from '@tanstack/react-query';
import { AuthContext } from './AuthContext.types';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const queryClient = useQueryClient();

  // Load auth state from localStorage on mount
  useEffect(() => {
    const stored = localStorage.getItem('auth_token');
    const storedUser = localStorage.getItem('auth_user');
    
    if (stored && storedUser) {
      try {
        setToken(stored);
        setUser(JSON.parse(storedUser));
      } catch {
        // Invalid stored data - clear it
        localStorage.removeItem('auth_token');
        localStorage.removeItem('auth_user');
      }
    }
    
    setIsLoading(false);
  }, []);

  const login = useCallback((newToken: string, newUser: User) => {
    // Clear all cached queries before logging in new user
    queryClient.clear();
    
    localStorage.setItem('auth_token', newToken);
    localStorage.setItem('auth_user', JSON.stringify(newUser));
    setToken(newToken);
    setUser(newUser);
  }, [queryClient]);

  const logout = useCallback(() => {
    // Clear all cached queries on logout to prevent data leakage
    queryClient.clear();
    
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    setToken(null);
    setUser(null);
  }, [queryClient]);

  const getToken = useCallback(() => token, [token]);

  const value = { user, token, isLoading, login, logout, getToken };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

