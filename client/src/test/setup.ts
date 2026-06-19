import '@testing-library/jest-dom';
import {cleanup} from '@testing-library/react';
import {afterEach, beforeEach, vi} from 'vitest';

function createStorageMock(): Storage {
    let store: Record<string, string> = {};

    return {
        get length(): number {
            return Object.keys(store).length;
        },
        getItem(key: string): string | null {
            return Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null;
        },
        setItem(key: string, value: string): void {
            store[String(key)] = String(value);
        },
        removeItem(key: string): void {
            delete store[String(key)];
        },
        clear(): void {
            store = {};
        },
        key(index: number): string | null {
            const keys = Object.keys(store);
            return index >= 0 && index < keys.length ? keys[index] : null;
        },
    } as Storage;
}

function installStorageMocks(): void {
    vi.stubGlobal('localStorage', createStorageMock());
    vi.stubGlobal('sessionStorage', createStorageMock());
}

// Install once at module load so storage is available before any test runs.
installStorageMocks();

beforeEach(() => {
    installStorageMocks();
});

afterEach(() => {
    cleanup();
});
