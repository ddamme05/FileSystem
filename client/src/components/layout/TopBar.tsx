import {Link, useLocation} from 'react-router-dom';
import {useAuth} from '@/hooks/useAuth';
import {FEATURE_FLAGS} from '@/lib/featureFlags';

export function TopBar() {
    const {user, logout} = useAuth();
    const location = useLocation();

    const isActive = (path: string) => location.pathname === path;

    return (
        <header className="sticky top-0 z-10 border-b border-border bg-white/70 backdrop-blur">
            <div className="container mx-auto flex items-center justify-between px-4 py-4">
                <div className="flex items-center gap-8">
                    <div className="flex items-center gap-4">
                        <div className="flex items-center gap-3">
                            <span
                                className="grid h-8 w-8 place-items-center rounded-lg text-white"
                                style={{background: 'linear-gradient(155deg,#6d63f0,#5b50e6 55%,#4b40d4)'}}
                            >
                                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                                     strokeWidth="2">
                                    <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                                </svg>
                            </span>
                            <h1 className="text-2xl font-bold">File Storage</h1>
                        </div>
                        {FEATURE_FLAGS.SHOW_ENV_BADGE && (
                            <span className="rounded bg-blue-100 px-2 py-1 text-xs text-blue-800">
                  {import.meta.env.MODE}
                </span>
                        )}
                    </div>
                    <nav className="flex items-center gap-2">
                        <Link
                            to="/"
                            className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                                isActive('/')
                                    ? 'bg-accent-weak text-accent'
                                    : 'text-muted hover:bg-line hover:text-ink'
                            }`}
                        >
                            Files
                        </Link>
                        <Link
                            to="/search"
                            className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                                isActive('/search')
                                    ? 'bg-accent-weak text-accent'
                                    : 'text-muted hover:bg-line hover:text-ink'
                            }`}
                        >
                            Deep Search
                        </Link>
                    </nav>
                </div>
                <div className="flex items-center gap-4">
                    <Link
                        to="/search"
                        className="flex items-center gap-2 rounded-lg border border-border bg-canvas px-3 py-1.5 text-sm text-faint"
                    >
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             strokeWidth="2">
                            <circle cx="11" cy="11" r="7"/>
                            <path d="m20 20-3.5-3.5"/>
                        </svg>
                        Quick find…
                    </Link>
                    <span
                        className="grid h-8 w-8 place-items-center rounded-full text-sm font-bold text-white"
                        style={{background: 'linear-gradient(155deg,#6d63f0,#5b50e6 55%,#4b40d4)'}}
                    >
                        {user?.username?.charAt(0).toUpperCase()}
                    </span>
                    <span className="text-sm text-muted">{user?.username}</span>
                    <button onClick={logout} className="btn-secondary">
                        Sign Out
                    </button>
                </div>
            </div>
        </header>
    );
}
