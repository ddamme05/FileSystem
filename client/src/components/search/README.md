# Search Components

Full-text search components for the File Storage Platform.

## Components

### SearchBar
Search input with submit handling and clear button.

**Usage:**
```tsx
<SearchBar 
  onSearch={(query) => console.log(query)} 
  placeholder="Search files..."
  initialQuery="previous search"
/>
```

### SearchResults
Displays search results with safe HTML snippet rendering.

**Features:**
- DOMPurify sanitization for XSS protection
- OCR confidence indicators
- File metadata display
- Preview button

**Usage:**
```tsx
<SearchResults
  results={data.results}
  onFileClick={(id) => navigate(`/files/${id}`)}
  onPreviewText={(id) => setPreviewId(id)}
/>
```

### SearchPagination
Keyset pagination controls for stable navigation.

**Usage:**
```tsx
<SearchPagination
  hasMore={data.hasMore}
  currentPage={currentPage}
  onNextPage={handleNext}
  onPrevPage={handlePrev}
  isLoading={isLoading}
  resultCount={data.count}
/>
```

### TextPreviewModal
Modal for viewing full extracted text with copy/download actions.

**Features:**
- Full-screen text preview
- Copy to clipboard
- Download as .txt file
- OCR confidence display
- Keyboard navigation (ESC to close)

**Usage:**
```tsx
{previewId && (
  <TextPreviewModal
    fileId={previewId}
    onClose={() => setPreviewId(null)}
  />
)}
```

## Security

**CRITICAL:** Search snippets contain HTML `<mark>` tags for highlighting. Always use DOMPurify:

```tsx
import DOMPurify from 'dompurify';

// ✅ SAFE
<div dangerouslySetInnerHTML={{
  __html: DOMPurify.sanitize(snippet, {
    ALLOWED_TAGS: ['mark'],
    ALLOWED_ATTR: []
  })
}} />

// ❌ UNSAFE - XSS vulnerability
<div dangerouslySetInnerHTML={{ __html: snippet }} />
```

## Pagination Strategy

Uses **keyset pagination** (cursor-based) for stability:

```tsx
const [cursors, setCursors] = useState<SearchCursor[]>([]);
const [currentPage, setCurrentPage] = useState(1);

// Get cursor for current page
const cursor = currentPage > 1 ? cursors[currentPage - 2] : undefined;

// Fetch results
const { data } = useSearch({
  query,
  lastRank: cursor?.rank,
  lastId: cursor?.id,
  limit: 20
});

// Navigate to next page
const handleNext = () => {
  if (data?.nextRank && data?.nextId) {
    setCursors(prev => [...prev, { rank: data.nextRank!, id: data.nextId! }]);
    setCurrentPage(prev => prev + 1);
  }
};
```

**Benefits:**
- No duplicate results when new files uploaded mid-pagination
- No missing results from concurrent writes
- Stable page boundaries

## Testing

Tests use Vitest + React Testing Library:

```bash
# Run tests
npm test

# Watch mode
npm run test:watch

# Coverage
npm run test:coverage
```

See `__tests__/SearchBar.test.tsx` for examples.

## Complete Example

See `src/pages/SearchPage.tsx` for a full implementation.

## Related Documentation

- Backend API: `docs/FRONTEND_INTEGRATION_GUIDE.md`
- E2E Testing: `docs/E2E_TESTING_GUIDE.md`
- Architecture: `cursor_v2_rationale.md`



