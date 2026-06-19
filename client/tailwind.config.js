/** @type {import('tailwindcss').Config} */
export default {
    content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
    theme: {
        extend: {
            colors: {
                accent: {DEFAULT: '#5b50e6', weak: '#ecebfd', strong: '#4b40d4'},
                canvas: '#f6f8fb',
                surface: '#ffffff',
                ink: '#161a23',
                muted: '#646c7e',
                faint: '#9aa1b2',
                border: '#e7eaf1',
                line: '#eef1f6',
            },
            boxShadow: {
                card: '0 1px 2px rgba(20,24,40,.04), 0 8px 24px -16px rgba(20,24,40,.22)',
                soft: '0 1px 2px rgba(20,24,40,.05), 0 2px 6px -3px rgba(20,24,40,.12)',
            },
            keyframes: {
                shake: {
                    '0%, 100%': {transform: 'translateX(0)'},
                    '10%, 30%, 50%, 70%, 90%': {transform: 'translateX(-4px)'},
                    '20%, 40%, 60%, 80%': {transform: 'translateX(4px)'},
                }
            },
            animation: {
                shake: 'shake 0.6s ease-in-out',
            }
        },
    },
    plugins: [],
};
