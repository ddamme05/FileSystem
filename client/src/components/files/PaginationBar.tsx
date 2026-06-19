interface PaginationBarProps {
    currentPage: number;
    totalPages: number;
    pageSize: number;
    onPageChange: (page: number) => void;
    onPageSizeChange: (size: number) => void;
}

export function PaginationBar({
                                  currentPage,
                                  totalPages,
                                  pageSize,
                                  onPageChange,
                                  onPageSizeChange,
                              }: PaginationBarProps) {
    const displayPage = currentPage + 1; // API is 0-based, display is 1-based

    return (
        <div className="mt-3 flex items-center justify-between rounded-2xl border border-border bg-surface px-6 py-4 text-muted shadow-soft">
            <div className="flex items-center gap-2">
                <span className="text-sm text-muted">Show</span>
                <select
                    value={pageSize}
                    onChange={(e) => onPageSizeChange(Number(e.target.value))}
                    className="rounded-lg border border-border bg-surface px-2 py-1 text-sm text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
                >
                    <option value={20}>20</option>
                    <option value={50}>50</option>
                    <option value={100}>100</option>
                </select>
                <span className="text-sm text-muted">per page</span>
            </div>

            <div className="flex items-center gap-2">
                <button
                    onClick={() => onPageChange(currentPage - 1)}
                    disabled={currentPage === 0}
                    className="rounded-lg border border-border bg-surface px-3 py-1 text-sm text-ink transition hover:border-accent disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:border-border"
                >
                    « Prev
                </button>

                <span className="text-sm text-muted">
          Page {displayPage} of {totalPages}
        </span>

                <button
                    onClick={() => onPageChange(currentPage + 1)}
                    disabled={currentPage === totalPages - 1}
                    className="rounded-lg border border-border bg-surface px-3 py-1 text-sm text-ink transition hover:border-accent disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:border-border"
                >
                    Next »
                </button>
            </div>
        </div>
    );
}






