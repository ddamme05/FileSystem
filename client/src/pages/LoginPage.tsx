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
        <div className="flex min-h-screen items-center justify-center">
            <div className="card w-full max-w-sm p-8">
                <div className="mb-7 flex flex-col items-center gap-3">
                    <span
                        className="grid h-12 w-12 place-items-center rounded-xl text-white shadow-[0_10px_24px_-8px_rgba(91,80,230,.6)]"
                        style={{background: 'linear-gradient(155deg,#6d63f0,#5b50e6 55%,#4b40d4)'}}
                    >
                        <svg
                            width="26"
                            height="26"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.9"
                        >
                            <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                        </svg>
                    </span>
                    <h2 className="text-2xl font-bold text-ink">File Storage</h2>
                    <p className="text-sm text-muted">Sign in to your files</p>
                </div>
                <form
                    onSubmit={handleLogin}
                    className={`space-y-5 ${hasError ? 'animate-shake' : ''}`}
                >
                    <div>
                        <label htmlFor="username" className="block text-sm font-medium text-ink">
                            Username
                        </label>
                        <input
                            id="username"
                            name="username"
                            type="text"
                            required
                            className="input mt-1.5"
                        />
                    </div>
                    <div>
                        <label htmlFor="password" className="block text-sm font-medium text-ink">
                            Password
                        </label>
                        <input
                            id="password"
                            name="password"
                            type="password"
                            required
                            className="input mt-1.5"
                        />
                    </div>
                    <button type="submit" disabled={isLoading} className="btn-primary w-full">
                        {isLoading ? 'Logging in...' : 'Login'}
                    </button>
                </form>
                <div className="mt-6 text-center">
                    <p className="text-sm text-muted">
                        Don't have an account?{' '}
                        <Link to="/register" className="font-medium text-accent">
                            Sign up
                        </Link>
                    </p>
                </div>
            </div>
        </div>
    );
}