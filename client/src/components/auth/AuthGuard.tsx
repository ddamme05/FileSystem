import type {ReactNode} from 'react';
import {Navigate} from 'react-router-dom';
import {useAuth} from '@/hooks/useAuth';

export function AuthGuard({children}: { children: ReactNode }) {
    const {token, isLoading} = useAuth();

    // Show loading state while checking localStorage
    if (isLoading) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-gray-50">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
                    <p className="text-gray-600">Loading...</p>
                </div>
            </div>
        );
    }

    // Only redirect to login if we're done loading and there's no token
    if (!token) {
        return <Navigate to="/login" replace/>;
    }

    return <>{children}</>;
}

