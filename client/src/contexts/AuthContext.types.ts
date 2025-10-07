import {createContext} from 'react';
import type {User} from '@/types/auth';

export interface AuthContextValue {
    user: User | null;
    token: string | null;
    isLoading: boolean;
    login: (token: string, user: User) => void;
    logout: () => void;
    getToken: () => string | null;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

