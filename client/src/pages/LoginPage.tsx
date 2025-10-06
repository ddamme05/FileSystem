import { useEffect } from 'react';
import { useFormStatus } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '@/hooks/useAuth';
import { api } from '@/api/client';
import type { AuthResponse } from '@/types/auth';

function SubmitButton() {
  const { pending } = useFormStatus();
  return (
    <button type="submit" disabled={pending} className="btn-primary w-full">
      {pending ? 'Logging in...' : 'Login'}
    </button>
  );
}

export function LoginPage() {
  const { login, token } = useAuth();
  const navigate = useNavigate();

  // Redirect if already logged in
  useEffect(() => {
    if (token) {
      navigate('/', { replace: true });
    }
  }, [token, navigate]);

  async function handleLogin(formData: FormData) {
    try {
      const response = await api.post<AuthResponse>('/api/v1/auth/login', {
        username: formData.get('username'),
        password: formData.get('password'),
      });

      // Convert AuthResponse to User format
      const user = { username: response.username, role: response.role };
      login(response.token, user);
      toast.success('Logged in successfully');
      navigate('/');
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Login failed';
      toast.error(message);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="w-full max-w-md space-y-8 rounded-lg bg-white p-8 shadow">
        <h2 className="text-center text-3xl font-bold">File Storage</h2>
        <form action={handleLogin} className="space-y-6">
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
          <SubmitButton />
        </form>
      </div>
    </div>
  );
}

