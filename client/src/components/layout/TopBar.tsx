import {Link, useLocation} from 'react-router-dom';
import {useAuth} from '@/hooks/useAuth';
import {FEATURE_FLAGS} from '@/lib/featureFlags';

export function TopBar() {
    const {user, logout} = useAuth();
    const location = useLocation();

    const isActive = (path: string) => location.pathname === path;

    return (
        <header className="border-b border-gray-200 bg-white">
            <div className="container mx-auto flex items-center justify-between px-4 py-4">
                <div className="flex items-center gap-8">
                    <div className="flex items-center gap-4">
                        <h1 className="text-2xl font-bold">File Storage</h1>
                        {FEATURE_FLAGS.SHOW_ENV_BADGE && (
                            <span className="rounded bg-blue-100 px-2 py-1 text-xs text-blue-800">
                  {import.meta.env.MODE}
                </span>
                        )}
                    </div>
                    <nav className="flex items-center gap-6">
                        <Link
                            to="/"
                            className={`text-sm font-medium transition-colors ${
                                isActive('/') 
                                    ? 'text-blue-600' 
                                    : 'text-gray-600 hover:text-gray-900'
                            }`}
                        >
                            Files
                        </Link>
                        <Link
                            to="/search"
                            className={`text-sm font-medium transition-colors ${
                                isActive('/search') 
                                    ? 'text-blue-600' 
                                    : 'text-gray-600 hover:text-gray-900'
                            }`}
                        >
                            Search
                        </Link>
                    </nav>
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

