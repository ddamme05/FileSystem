import {useAuth} from '@/hooks/useAuth';
import {FEATURE_FLAGS} from '@/lib/featureFlags';

export function TopBar() {
    const {user, logout} = useAuth();

    return (
        <header className="border-b border-gray-200 bg-white">
            <div className="container mx-auto flex items-center justify-between px-4 py-4">
                <div className="flex items-center gap-4">
                    <h1 className="text-2xl font-bold">File Storage</h1>
                    {FEATURE_FLAGS.SHOW_ENV_BADGE && (
                        <span className="rounded bg-blue-100 px-2 py-1 text-xs text-blue-800">
              {import.meta.env.MODE}
            </span>
                    )}
                </div>
                <div className="flex items-center gap-4">
                    <span className="text-sm text-gray-600">{user?.username}</span>
                    <button onClick={logout} className="btn-secondary">
                        Sign Out
                    </button>
                </div>
            </div>
        </header>
    );
}

