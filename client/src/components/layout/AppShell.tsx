import type {ReactNode} from 'react';
import {TopBar} from './TopBar';

export function AppShell({children}: { children: ReactNode }) {
    return (
        <div className="min-h-screen bg-gray-50">
            <TopBar/>
            <main className="container mx-auto px-4 py-8">{children}</main>
        </div>
    );
}

