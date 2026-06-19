import {useState} from 'react';
import {ArrowDown, ArrowUp, Search, X} from 'lucide-react';

export type SortField = 'name' | 'size' | 'type' | 'uploadTimestamp';
export type SortOrder = 'asc' | 'desc';
export type FileTypeFilter = 'all' | 'image' | 'document' | 'video' | 'audio' | 'archive' | 'text' | 'other';

interface FileFiltersProps {
    sortField: SortField;
    sortOrder: SortOrder;
    typeFilter: FileTypeFilter;
    searchQuery: string;
    onSortChange: (field: SortField) => void;
    onTypeFilterChange: (filter: FileTypeFilter) => void;
    onSearchChange: (query: string) => void;
}

const sortOptions: { label: string; value: SortField }[] = [
    {label: 'Upload Time', value: 'uploadTimestamp'},
    {label: 'Name', value: 'name'},
    {label: 'Size', value: 'size'},
    {label: 'Type', value: 'type'},
];

const typeFilterOptions: { label: string; value: FileTypeFilter }[] = [
    {label: 'All Files', value: 'all'},
    {label: 'Images', value: 'image'},
    {label: 'Documents', value: 'document'},
    {label: 'Videos', value: 'video'},
    {label: 'Audio', value: 'audio'},
    {label: 'Text & Code', value: 'text'},
    {label: 'Archives', value: 'archive'},
    {label: 'Other', value: 'other'},
];

export function FileFilters({
                                sortField,
                                sortOrder,
                                typeFilter,
                                searchQuery,
                                onSortChange,
                                onTypeFilterChange,
                                onSearchChange,
                            }: FileFiltersProps) {
    const [isSearchExpanded, setIsSearchExpanded] = useState(false);

    const toggleSortOrder = () => {
        // This will be called from FilesPage to toggle order
        onSortChange(sortField);
    };

    return (
        <div className="mb-6 flex flex-wrap items-center justify-between gap-4 card p-4">
            {/* Left side: Type filter + Search */}
            <div className="flex items-center gap-3">
                <select
                    id="type-filter"
                    value={typeFilter}
                    onChange={(e) => onTypeFilterChange(e.target.value as FileTypeFilter)}
                    className="rounded-lg border border-border bg-surface py-1.5 pl-3 pr-8 text-sm text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
                    aria-label="Filter by file type"
                >
                    {typeFilterOptions.map((option) => (
                        <option key={option.value} value={option.value}>
                            {option.label}
                        </option>
                    ))}
                </select>

                {/* Expandable search */}
                <div className="flex items-center">
                    {!isSearchExpanded ? (
                        <button
                            onClick={() => setIsSearchExpanded(true)}
                            className="inline-flex items-center gap-1 rounded-lg border border-accent bg-accent px-3 py-1.5 text-sm font-medium text-white transition hover:bg-accent-strong focus:outline-none focus:ring-2 focus:ring-accent/30"
                            aria-label="Open search"
                        >
                            <Search size={16}/>
                            <span>Search</span>
                        </button>
                    ) : (
                        <div className="flex items-center gap-2">
                            <div className="relative">
                                <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-faint"/>
                                <input
                                    type="text"
                                    value={searchQuery}
                                    onChange={(e) => onSearchChange(e.target.value)}
                                    placeholder="Search files..."
                                    className="w-64 rounded-lg border border-border bg-surface py-1.5 pl-10 pr-3 text-sm text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
                                    autoFocus
                                />
                            </div>
                            <button
                                onClick={() => {
                                    setIsSearchExpanded(false);
                                    onSearchChange('');
                                }}
                                className="text-faint hover:text-muted transition"
                                aria-label="Close search"
                            >
                                <X size={18}/>
                            </button>
                        </div>
                    )}
                </div>
            </div>

            {/* Right side: Sort */}
            <div className="flex items-center gap-2">
                <label className="text-sm font-medium text-muted">Sort:</label>
                <select
                    value={sortField}
                    onChange={(e) => onSortChange(e.target.value as SortField)}
                    className="rounded-lg border border-border bg-surface py-1.5 pl-3 pr-8 text-sm text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
                    aria-label="Sort files by"
                >
                    {sortOptions.map((option) => (
                        <option key={option.value} value={option.value}>
                            {option.label}
                        </option>
                    ))}
                </select>
                <button
                    onClick={toggleSortOrder}
                    className="inline-flex items-center rounded-lg border border-border bg-surface p-1.5 text-ink transition hover:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
                    title={`Sort order: ${sortOrder === 'asc' ? 'Ascending' : 'Descending'}`}
                    aria-label={`Toggle sort order (currently ${sortOrder === 'asc' ? 'ascending' : 'descending'})`}
                >
                    {sortOrder === 'asc' ? <ArrowUp size={16}/> : <ArrowDown size={16}/>}
                </button>
            </div>
        </div>
    );
}
