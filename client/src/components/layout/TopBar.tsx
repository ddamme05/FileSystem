import {useState, type FormEvent} from 'react';
import {Link, useLocation, useNavigate} from 'react-router-dom';
import {FolderClosed, Search} from 'lucide-react';
import {useAuth} from '@/hooks/useAuth';
import {FEATURE_FLAGS} from '@/lib/featureFlags';

export function TopBar() {
    const {user, logout} = useAuth();
    const location = useLocation();
    const navigate = useNavigate();
    const [quickFind, setQuickFind] = useState('');

    const isActive = (path: string) => location.pathname === path;

    const handleQuickFind = (e: FormEvent) => {
        e.preventDefault();
        const q = quickFind.trim();
        if (q) {
            navigate('/search?q=' + encodeURIComponent(q));
        }
    };

    return (
        <header className="sticky top-0 z-10 border-b border-border bg-white/70 backdrop-blur">
            <div className="container mx-auto flex flex-wrap items-center justify-between gap-y-2 px-4 py-4">
                <div className="flex min-w-0 items-center gap-8">
                    <div className="flex items-center gap-4">
                        <div className="flex items-center gap-3">
                            <span
                                className="grid h-8 w-8 place-items-center rounded-lg text-white"
                                style={{background: 'linear-gradient(155deg,#6d63f0,#5b50e6 55%,#4b40d4)'}}
                            >
                                <FolderClosed size={17} strokeWidth={2}/>
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
                    <form
                        onSubmit={handleQuickFind}
                        className="relative hidden items-center md:flex"
                        role="search"
                    >
                        <Search
                            size={15}
                            strokeWidth={2}
                            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-faint"
                        />
                        <input
                            type="text"
                            value={quickFind}
                            onChange={(e) => setQuickFind(e.target.value)}
                            placeholder="Quick find…"
                            aria-label="Quick find"
                            className="w-48 rounded-lg border border-border bg-canvas py-1.5 pl-9 pr-3 text-sm text-ink placeholder:text-faint focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent"
                        />
                    </form>
                    <span
                        className="grid h-8 w-8 shrink-0 place-items-center rounded-full text-sm font-bold text-white"
                        style={{background: 'linear-gradient(155deg,#6d63f0,#5b50e6 55%,#4b40d4)'}}
                    >
                        {user?.username?.charAt(0).toUpperCase()}
                    </span>
                    <span className="hidden text-sm text-muted sm:inline">{user?.username}</span>
                    <button onClick={logout} className="btn-secondary">
                        Sign Out
                    </button>
                </div>
            </div>
        </header>
    );
}
