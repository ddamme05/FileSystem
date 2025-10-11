import {type FormEvent, useEffect, useState} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {toast} from 'sonner';
import {useAuth} from '@/hooks/useAuth';
import {api, ApiError} from '@/api/client';
import type {AuthResponse} from '@/types/auth';

export function LoginPage() {
    const {login, token} = useAuth();
    const navigate = useNavigate();
    const [isLoading, setIsLoading] = useState(false);
    const [hasError, setHasError] = useState(false);

    useEffect(() => {
        if (token) {
            navigate('/', {replace: true});
        }
    }, [token, navigate]);

    // Show session expired toast if redirected from 401
    useEffect(() => {
        const reason = sessionStorage.getItem('auth_redirect_reason');
        if (reason === 'expired') {
            toast.error('Session expired. Please log in again.', {duration: 6000});
            sessionStorage.removeItem('auth_redirect_reason');
        }
    }, []);

    async function handleLogin(e: FormEvent<HTMLFormElement>) {
        e.preventDefault();
        setIsLoading(true);

        const formData = new FormData(e.currentTarget);

        try {
            const response = await api.post<AuthResponse>('/api/v1/auth/login', {
                username: formData.get('username'),
                password: formData.get('password'),
            });

            // Convert AuthResponse to User format
            const user = {username: response.username, role: response.role};
            login(response.token, user);
            toast.success('Logged in successfully');
            navigate('/');
        } catch (error) {
            let message = 'Login failed';

            if (error instanceof ApiError) {
                // Map backend error messages to user-friendly text
                if (error.status === 401) {
                    message = 'Invalid username or password. Please try again.';
                } else if (error.status >= 500) {
                    message = 'Server error. Please try again later.';
                } else {
                    message = error.message;
                }
            } else if (error instanceof Error) {
                message = error.message;
            }

            toast.error(message, {
                duration: 8000,
                closeButton: true,
            });

            // Trigger shake animation with cleanup
            setHasError(true);
            const timeoutId = setTimeout(() => setHasError(false), 600);

            // Cleanup if component unmounts
            return () => clearTimeout(timeoutId);
        } finally {
            setIsLoading(false);
        }
    }

    return (
        <div className="flex min-h-screen items-center justify-center bg-gray-50">
            <div className="w-full max-w-md space-y-8 rounded-lg bg-white p-8 shadow">
                <h2 className="text-center text-3xl font-bold">File Storage</h2>
                <form
                    onSubmit={handleLogin}
                    className={`space-y-6 ${hasError ? 'animate-shake' : ''}`}
                >
                    <div>
                        <label htmlFor="username" className="block text-sm font-medium">
                            Username
                        </label>
                        <input
                            id="username"
                            name="username"
                            type="text"
                            required
                            className="mt-1 block w-full rounded border p-2"
                        />
                    </div>
                    <div>
                        <label htmlFor="password" className="block text-sm font-medium">
                            Password
                        </label>
                        <input
                            id="password"
                            name="password"
                            type="password"
                            required
                            className="mt-1 block w-full rounded border p-2"
                        />
                    </div>
                    <button type="submit" disabled={isLoading} className="btn-primary w-full">
                        {isLoading ? 'Logging in...' : 'Login'}
                    </button>
                </form>
                <div className="text-center">
                    <p className="text-sm text-gray-600">
                        Don't have an account?{' '}
                        <Link to="/register" className="font-medium text-blue-600 hover:text-blue-500">
                            Sign up
                        </Link>
                    </p>
                </div>
            </div>
        </div>
    );
}