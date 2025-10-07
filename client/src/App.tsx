import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {Toaster} from 'sonner';

import {AuthProvider} from '@/contexts/AuthContext';
import {UploadProvider} from '@/contexts/UploadContext';
import {AuthGuard} from '@/components/auth/AuthGuard';
import {ErrorBoundary} from '@/components/ErrorBoundary';
import {AppShell} from '@/components/layout/AppShell';
import {UploadPanel} from '@/components/files/UploadPanel';
import {LoginPage} from '@/pages/LoginPage';
import {FilesPage} from '@/pages/FilesPage';

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            retry: 1,
            refetchOnWindowFocus: false,
            staleTime: 30000,
        },
    },
});

export function App() {
    return (
        <ErrorBoundary>
            <QueryClientProvider client={queryClient}>
                <AuthProvider>
                    <UploadProvider>
                        <BrowserRouter>
                            <Routes>
                                <Route path="/login" element={<LoginPage/>}/>
                                <Route
                                    path="/"
                                    element={
                                        <AuthGuard>
                                            <AppShell>
                                                <FilesPage/>
                                            </AppShell>
                                        </AuthGuard>
                                    }
                                />
                                <Route path="*" element={<Navigate to="/" replace/>}/>
                            </Routes>
                        </BrowserRouter>
                        <UploadPanel/>
                        <Toaster position="top-right"/>
                    </UploadProvider>
                </AuthProvider>
            </QueryClientProvider>
        </ErrorBoundary>
    );
}
