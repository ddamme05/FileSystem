import {useEffect} from 'react';
import {useFormStatus} from 'react-dom';
import {Link, useNavigate} from 'react-router-dom';
import {toast} from 'sonner';
import {useAuth} from '@/hooks/useAuth';
import {api} from '@/api/client';
import type {AuthResponse} from '@/types/auth';

function SubmitButton() {
    const {pending} = useFormStatus();
    return (
        <button type="submit" disabled={pending} className="btn-primary w-full">
            {pending ? 'Creating account...' : 'Sign Up'}
        </button>
    );
}

export function RegisterPage() {
    const {login, token} = useAuth();
    const navigate = useNavigate();

    // Redirect if already logged in
    useEffect(() => {
        if (token) {
            navigate('/', {replace: true});
        }
    }, [token, navigate]);

    async function handleRegister(formData: FormData) {
        const password = formData.get('password') as string;
        const confirmPassword = formData.get('confirmPassword') as string;

        // Validate passwords match
        if (password !== confirmPassword) {
            toast.error('Passwords do not match');
            return;
        }

        // Validate password strength (basic check)
        if (password.length < 8) {
            toast.error('Password must be at least 8 characters long');
            return;
        }

        try {
            const response = await api.post<AuthResponse>('/api/v1/auth/register', {
                username: formData.get('username'),
                email: formData.get('email'),
                password: password,
            });

            // Convert AuthResponse to User format
            const user = {username: response.username, role: response.role};
            login(response.token, user);
            toast.success('Account created successfully');
            navigate('/');
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Registration failed';
            toast.error(message);
        }
    }

    return (
        <div className="flex min-h-screen items-center justify-center bg-gray-50">
            <div className="w-full max-w-md space-y-8 rounded-lg bg-white p-8 shadow">
                <div>
                    <h2 className="text-center text-3xl font-bold">Create Account</h2>
                    <p className="mt-2 text-center text-sm text-gray-600">
                        Join File Storage to manage your files
                    </p>
                </div>
                <form action={handleRegister} className="space-y-6">
                    <div>
                        <label htmlFor="username" className="block text-sm font-medium">
                            Username
                        </label>
                        <input
                            id="username"
                            name="username"
                            type="text"
                            required
                            autoComplete="username"
                            className="mt-1 block w-full rounded border p-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                        />
                    </div>
                    <div>
                        <label htmlFor="email" className="block text-sm font-medium">
                            Email
                        </label>
                        <input
                            id="email"
                            name="email"
                            type="email"
                            required
                            autoComplete="email"
                            className="mt-1 block w-full rounded border p-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
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
                            autoComplete="new-password"
                            minLength={8}
                            className="mt-1 block w-full rounded border p-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                        />
                        <p className="mt-1 text-xs text-gray-500">
                            Must be at least 8 characters long
                        </p>
                    </div>
                    <div>
                        <label htmlFor="confirmPassword" className="block text-sm font-medium">
                            Confirm Password
                        </label>
                        <input
                            id="confirmPassword"
                            name="confirmPassword"
                            type="password"
                            required
                            autoComplete="new-password"
                            minLength={8}
                            className="mt-1 block w-full rounded border p-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                        />
                    </div>
                    <SubmitButton/>
                </form>
                <div className="text-center">
                    <p className="text-sm text-gray-600">
                        Already have an account?{' '}
                        <Link to="/login" className="font-medium text-blue-600 hover:text-blue-500">
                            Sign in
                        </Link>
                    </p>
                </div>
            </div>
        </div>
    );
}

