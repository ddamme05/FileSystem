import {v4 as uuidv4} from 'uuid';

export class ApiError extends Error {
    constructor(
        public status: number,
        message: string,
        public path?: string,
        public timestamp?: string,
        public requestId?: string,
        public retryAfter?: number
    ) {
        super(message);
        this.name = 'ApiError';
    }
}

function parseRetryAfter(header: string | null): number {
    if (!header) return 30;

    const seconds = Number(header);
    if (Number.isFinite(seconds)) return Math.max(0, seconds);

    const date = Date.parse(header);
    if (!isNaN(date)) {
        return Math.max(0, Math.ceil((date - Date.now()) / 1000));
    }

    return 30;
}

async function parseError(response: Response): Promise<ApiError> {
    const contentType = response.headers.get('content-type');
    const serverRequestId = response.headers.get('X-Request-ID');
    const retryAfter = response.status === 429
        ? parseRetryAfter(response.headers.get('Retry-After'))
        : undefined;

    if (contentType?.includes('application/json')) {
        try {
            const body = await response.json();
            return new ApiError(
                response.status,
                body.message || 'An error occurred',
                body.path,
                body.timestamp,
                serverRequestId || undefined,
                retryAfter
            );
        } catch {
            // Malformed JSON - fall through
        }
    }

    const text = await response.text();
    return new ApiError(
        response.status,
        text || response.statusText,
        response.url,
        undefined,
        serverRequestId || undefined,
        retryAfter
    );
}

export async function apiRequest<T>(endpoint: string, options?: RequestInit): Promise<T> {
    const token = localStorage.getItem('auth_token');
    const clientRequestId = uuidv4();
    const hasBody = options?.body != null;

    const response = await fetch(endpoint, {
        ...options,
        headers: {
            ...(hasBody ? {'Content-Type': 'application/json'} : {}),
            ...options?.headers,
            ...(token && {Authorization: `Bearer ${token}`}),
            'X-Client-Request-Id': clientRequestId,
        },
    });

    if (!response.ok) {
        // Auto-logout on 401
        if (response.status === 401) {
            localStorage.removeItem('auth_token');
            localStorage.removeItem('auth_user');
            window.location.href = '/login';
        }

        const error = await parseError(response);

        // Show rate limit toast on 429
        if (response.status === 429 && error.retryAfter) {
            // Import toast and RateLimitToast dynamically to avoid circular dependencies
            Promise.all([
                import('sonner'),
                import('@/components/RateLimitToast'),
                import('react')
            ]).then(([{toast}, {RateLimitToast}, React]) => {
                toast.custom(
                    (id) => React.createElement(RateLimitToast, {
                        retryAfter: error.retryAfter!,
                        onDismiss: () => toast.dismiss(id)
                    }),
                    {duration: error.retryAfter! * 1000}
                );
            });
        }

        throw error;
    }

    // ✅ Handle 204 No Content
    if (response.status === 204) {
        return undefined as T;
    }

    // ✅ Check Content-Type
    const contentType = response.headers.get('content-type');
    if (!contentType?.includes('application/json')) {
        const text = await response.text();
        return {message: text || 'Success'} as T;
    }

    // ✅ Handle empty body
    const text = await response.text();
    if (!text || text.trim() === '') {
        return undefined as T;
    }

    try {
        return JSON.parse(text);
    } catch {
        throw new ApiError(response.status, 'Invalid JSON response', endpoint);
    }
}

export const api = {
    get: <T>(url: string) => apiRequest<T>(url, {method: 'GET'}),
    post: <T>(url: string, data?: unknown) =>
        apiRequest<T>(url, {
            method: 'POST',
            ...(data ? {body: JSON.stringify(data)} : {}),
        }),
    put: <T>(url: string, data?: unknown) =>
        apiRequest<T>(url, {
            method: 'PUT',
            ...(data ? {body: JSON.stringify(data)} : {}),
        }),
    delete: <T>(url: string) => apiRequest<T>(url, {method: 'DELETE'}),
};

