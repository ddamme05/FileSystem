export function formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
}

export function formatRelativeTime(date: Date | string): string {
    const rtf = new Intl.RelativeTimeFormat('en', {numeric: 'auto'});
    const now = Date.now();
    const then = typeof date === 'string' ? Date.parse(date) : date.getTime();
    const diffMs = now - then;
    const diffSec = Math.round(diffMs / 1000);
    const diffMin = Math.round(diffSec / 60);
    const diffHour = Math.round(diffMin / 60);
    const diffDay = Math.round(diffHour / 24);

    // Use negative values for past times
    if (Math.abs(diffSec) < 60) return rtf.format(-diffSec, 'second');
    if (Math.abs(diffMin) < 60) return rtf.format(-diffMin, 'minute');
    if (Math.abs(diffHour) < 24) return rtf.format(-diffHour, 'hour');
    if (Math.abs(diffDay) < 30) return rtf.format(-diffDay, 'day');

    return new Intl.DateTimeFormat('en', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
    }).format(then);
}
