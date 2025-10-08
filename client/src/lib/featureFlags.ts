export const FEATURE_FLAGS = {
    // Copy Link button (disabled for MVP, Option C)
    ENABLE_COPY_LINK: false as boolean,

    // Ticket expiry when enabled (Option B)
    DOWNLOAD_TICKET_EXPIRY_SECONDS: 60 as number,

    // Show env badge
    SHOW_ENV_BADGE: false as boolean,
} as const;

export function getDownloadApproach(): 'option-a' | 'option-b' | 'option-c' {
    if (!FEATURE_FLAGS.ENABLE_COPY_LINK) return 'option-c';
    // Future: detect cookies vs tickets
    return 'option-b';
}

export function getCopyLinkTooltip(): string {
    const approach = getDownloadApproach();
    if (approach === 'option-c') return 'Copy link not available in MVP';
    if (approach === 'option-b')
        return `Copy link (expires in ${FEATURE_FLAGS.DOWNLOAD_TICKET_EXPIRY_SECONDS}s)`;
    return 'Copy shareable link';
}
