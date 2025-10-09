import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react-swc';
import path from 'path';

export default defineConfig(({mode}) => ({
    plugins: [react()],

    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src'),
        },
    },

    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },

    build: {
        target: 'esnext',
        outDir: 'dist',
        sourcemap: mode === 'production' ? 'hidden' : true,

        rollupOptions: {
            output: {
                // Force unique hashes by including timestamp
                entryFileNames: `assets/[name]-[hash]-${Date.now()}.js`,
                chunkFileNames: `assets/[name]-[hash]-${Date.now()}.js`,
                assetFileNames: `assets/[name]-[hash]-${Date.now()}.[ext]`,

                manualChunks: {
                    'react-vendor': ['react', 'react-dom', 'react-router-dom'],
                    'query-vendor': ['@tanstack/react-query'],
                },
            },
        },
    },

    esbuild: {
        drop: mode === 'production' ? ['console', 'debugger'] : [],
    },
}));
