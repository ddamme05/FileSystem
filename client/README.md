# File Storage - React Frontend

w**Stack:** React 19 + TypeScript + Vite + TanStack Query + Tailwind CSS  
**Status:** ✅ Production-Ready with Security Hardening

---

## 🎯 Overview

Modern, responsive file management interface with drag-and-drop upload, real-time progress tracking, and rich file
previews.

**Key Features:**

- Drag-and-drop file upload with duplicate detection
- Real-time upload progress tracking
- Rich file previews (images, PDFs, text, video, audio)
- Client-side filtering and sorting
- Responsive design with Tailwind CSS
- JWT authentication with auto-logout
- Rate limit notifications
- E2E tested with Playwright

**Architecture Details:** See [Frontend Architecture](../docs/FRONTEND_ARCHITECTURE.md)

---

## 🛡️ Security Features

- ✅ **Strict Content Security Policy (CSP)** - Primary XSS defense
- ✅ **Sandboxed Iframes** - Isolated preview content
- ✅ **Referrer Policy** - Prevents S3 URL leaking
- ✅ **Security Headers** - HSTS, X-Frame-Options, X-Content-Type-Options
- ✅ **Rate Limiting UI** - User-friendly rate limit notifications
- ✅ **Auto-Logout** - Automatic logout on 401 (token expiration)
- ✅ **SQL Injection Protection** - Backend uses JPA prepared statements
- ✅ **User Isolation** - Files are private to owner

---

## 🚀 Quick Start

### Prerequisites

- Node.js 20+ (managed via fnm)
- Backend running on port 8080

### Development Server

```bash
# Start backend first (in project root)
docker-compose up -d

# Or run backend locally
./gradlew bootRun

# Start frontend dev server (in client/ directory)
./dev.sh

# Or manually
export PATH="/home/ddamme05/.local/share/fnm:$PATH"
eval "$(fnm env)"
npm install
npm run dev
```

**Access:** http://localhost:3000

### Test Credentials

**Username:** `demouser`  
**Password:** `Demo123!`

(Or register a new user at the login page)

---

## 📦 Available Commands

```bash
# Development
npm run dev              # Start dev server (Vite)
npm run build            # Production build
npm run preview          # Preview production build

# Testing
npm run test             # Run unit tests (Vitest)
npm run test:e2e         # Run E2E tests headless (Playwright)
npm run test:e2e:ui      # Run E2E tests with UI
npm run test:e2e:debug   # Debug E2E tests

# Code Quality
npm run lint             # ESLint check
npm run lint:fix         # ESLint auto-fix
npm run type-check       # TypeScript type checking
```

---

## 🏗️ Architecture

### Component Hierarchy

```
App (Router Root)
├── AuthGuard (Protected Routes)
│   └── AppShell (Layout)
│       ├── TopBar (Navigation)
│       └── FilesPage (Main View)
│           ├── UploadZone (Drag & Drop)
│           ├── FileFilters (Sort/Filter Controls)
│           ├── FilesTable (File List)
│           ├── PaginationBar (Page Navigation)
│           ├── PreviewModal (File Preview)
│           └── DeleteDialog (Confirmation)
├── LoginPage (Public)
├── RegisterPage (Public)
└── UploadPanel (Fixed Position, Global)
```

### State Management

**Global State:**

- **AuthContext:** JWT token, user info, login/logout
- **UploadContext:** Upload queue, progress tracking, error handling

**Server State:**

- **TanStack Query:** Caches file listings, auto-revalidation

See [Frontend Architecture](../docs/FRONTEND_ARCHITECTURE.md) for detailed diagrams.

---

## 📁 Project Structure

```
client/
├── src/
│   ├── main.tsx                 # Entry point
│   ├── App.tsx                  # Router configuration
│   ├── components/              # Reusable components
│   │   ├── auth/
│   │   │   └── AuthGuard.tsx    # Protected route wrapper
│   │   ├── files/               # File management components
│   │   │   ├── FileFilters.tsx  # Sort/filter controls
│   │   │   ├── FilesTable.tsx   # File list display
│   │   │   ├── UploadZone.tsx   # Drag-and-drop upload
│   │   │   ├── UploadPanel.tsx  # Upload progress UI
│   │   │   ├── PreviewModal.tsx # File preview modal
│   │   │   ├── DeleteDialog.tsx # Delete confirmation
│   │   │   ├── DuplicateFileDialog.tsx  # Handle duplicates
│   │   │   └── PaginationBar.tsx        # Pagination
│   │   ├── layout/              # Layout components
│   │   │   ├── AppShell.tsx     # Main layout wrapper
│   │   │   └── TopBar.tsx       # Navigation header
│   │   ├── ErrorBoundary.tsx    # Error boundary
│   │   └── RateLimitToast.tsx   # Rate limit notification
│   ├── pages/                   # Page components
│   │   ├── LoginPage.tsx        # Login UI
│   │   ├── RegisterPage.tsx     # Registration UI
│   │   └── FilesPage.tsx        # Main file manager
│   ├── contexts/                # React contexts
│   │   ├── AuthContext.tsx      # Authentication state
│   │   └── UploadContext.tsx    # Upload queue management
│   ├── hooks/                   # Custom hooks
│   │   ├── useAuth.ts           # Auth operations
│   │   ├── useFiles.ts          # File listing with pagination
│   │   ├── useUploadFile.ts     # File upload with progress
│   │   ├── useDeleteFile.ts     # File deletion
│   │   ├── useDownloadFile.ts   # File download
│   │   └── useUploadContext.ts  # Upload state access
│   ├── api/
│   │   └── client.ts            # HTTP client (auth, errors)
│   ├── types/                   # TypeScript types
│   │   ├── auth.ts              # Auth types
│   │   └── file.ts              # File types
│   ├── lib/                     # Utilities
│   │   ├── formatters.ts        # File size, date formatters
│   │   ├── mimeTypes.ts         # MIME type utilities
│   │   └── featureFlags.ts      # Feature toggles
│   └── index.css                # Global styles + Tailwind
├── public/
│   └── favicon.svg              # Custom folder icon
├── e2e/                         # E2E tests
│   └── app.spec.ts              # Playwright tests
├── index.html                   # Entry HTML with CSP
├── vite.config.ts               # Build configuration
├── tailwind.config.js           # Styling configuration
├── playwright.config.ts         # E2E test configuration
├── Dockerfile                   # Production container
└── nginx.conf                   # Production web server config
```

---

## 🎨 Styling

**Tailwind CSS 3** with custom configuration:

```javascript
// tailwind.config.js
module.exports = {
    content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
    theme: {
        extend: {
            animation: {
                shake: 'shake 0.5s ease-in-out',
            },
            keyframes: {
                shake: {
                    '0%, 100%': {transform: 'translateX(0)'},
                    '10%, 30%, 50%, 70%, 90%': {transform: 'translateX(-10px)'},
                    '20%, 40%, 60%, 80%': {transform: 'translateX(10px)'},
                },
            },
        },
    },
};
```

**Custom Utilities:**

- `.btn-primary` - Primary action button
- `.btn-secondary` - Secondary action button
- `.animate-shake` - Error shake animation

---

## 🔌 API Integration

### API Client (`src/api/client.ts`)

**Features:**

- Automatic JWT token injection
- Request/response correlation IDs
- Global error handling
- Auto-logout on 401
- Rate limit toast on 429
- Type-safe responses

**Usage:**

```typescript
import {api} from '@/api/client';

// GET request
const response = await api.get<PagedFileResponse>('/api/v1/files?page=0&size=20');

// POST request
await api.post<FileDto>('/api/v1/files/upload', formData);

// DELETE request
await api.delete(`/api/v1/files/${fileId}`);
```

**Error Handling:**

```typescript
try {
    await api.post('/api/v1/files/upload', data);
} catch (error) {
    if (error instanceof ApiError) {
        console.error('API Error:', error.status, error.message);
    }
}
```

---

## 🧪 Testing

### E2E Tests (Playwright)

**Coverage:**

- ✅ User registration
- ✅ User login
- ✅ File upload
- ✅ File listing
- ✅ File download
- ✅ File deletion
- ✅ Logout

**Run Tests:**

```bash
# Headless mode
npm run test:e2e

# Interactive UI mode
npm run test:e2e:ui

# Debug mode
npm run test:e2e:debug
```

**Test File:** `e2e/app.spec.ts`

### Unit Tests (Vitest)

**Future:** Component and hook unit tests

```bash
npm run test
npm run test:ui   # With UI
```

---

## 🚢 Deployment

### Docker Build

**Multi-stage Dockerfile:**

1. **Build stage:** Install dependencies + build app
2. **Runtime stage:** Nginx serves static files

```bash
# Build image
docker build -t file-system-frontend:latest .

# Run container
docker run -p 3000:80 file-system-frontend:latest
```

### Production Configuration

**Nginx (`nginx.conf`):**

- Serves React app from `/usr/share/nginx/html`
- SPA routing (fallback to `index.html`)
- Health check endpoint at `/health`
- Security headers
- Gzip compression

**Build Output:**

- Minified JavaScript bundles
- CSS extracted and minified
- Assets with cache-busting hashes
- Source maps for debugging

---

## 🔍 Features in Detail

### 1. File Upload

**Drag-and-Drop:**

- Uses `react-dropzone` library
- Visual feedback on drag over
- Multi-file support
- File size validation (10MB max)

**Duplicate Detection:**

- Checks existing files before upload
- Offers "Replace" or "Keep Both" options
- "Keep Both" appends `-1`, `-2`, etc. to filename

**Progress Tracking:**

- Real-time progress via XMLHttpRequest
- Upload panel shows all active/completed uploads
- Cancellable uploads
- Error categorization (too_large, network, forbidden, etc.)

### 2. File Preview

**Supported Formats:**

- **Images:** All formats (JPEG, PNG, GIF, WebP, SVG)
- **PDFs:** Embedded viewer (toolbar hidden)
- **Text:** Plain text, Markdown, CSV, code files
- **Video:** MP4, WebM, MOV (HTML5 video)
- **Audio:** MP3, WAV, OGG (HTML5 audio)

**Features:**

- Keyboard navigation (Arrow keys, Escape)
- Previous/Next for images
- Sandboxed iframes for security
- Presigned URL fetching from backend

### 3. Filtering and Sorting

**Filter Options:**

- Type: All, Image, Video, Audio, Document, Text, Archive, Other
- Search: Case-insensitive substring match

**Sort Options:**

- Field: Name, Size, Type, Upload Date
- Order: Ascending, Descending

**Known Limitation:** Client-side filtering/sorting (only affects current page)

See [Filtering Architecture](../docs/FILTERING_ARCHITECTURE.md) for details.

---

## 🔧 Configuration

### Environment Variables

**Development (`.env`):**

```bash
VITE_API_URL=http://localhost:8080
```

**Production:**

- API URL is relative (proxied by Nginx)
- No environment variables needed

### Feature Flags

**Location:** `src/lib/featureFlags.ts`

```typescript
export const FEATURE_FLAGS = {
    ENABLE_COPY_LINK: false,  // Copy link feature (coming soon)
};
```

---

## 🐛 Troubleshooting

### Issue: Blank page after build

**Solution:** Check Vite build output, ensure `base` path is correct

### Issue: API calls fail with CORS error

**Solution:** Ensure backend CORS is configured for frontend origin

### Issue: Upload shows 413 (Payload Too Large)

**Solution:** Check Nginx `client_max_body_size` setting

### Issue: Preview shows "Failed to load"

**Solution:** Check S3 presigned URL accessibility, verify CORS on S3 bucket

---

## 📚 Further Reading

- **[Frontend Architecture](../docs/FRONTEND_ARCHITECTURE.md)** - Detailed architecture guide
- **[Filtering Architecture](../docs/FILTERING_ARCHITECTURE.md)** - Filter/sort implementation
- **[React 19 Documentation](https://react.dev)**
- **[TanStack Query](https://tanstack.com/query/latest)**
- **[Tailwind CSS](https://tailwindcss.com)**
- **[Vite](https://vitejs.dev)**
- **[Playwright](https://playwright.dev)**

---

## 💡 Future Enhancements

- [ ] Server-side filtering and sorting
- [ ] Copy shareable link feature
- [ ] Folder support
- [ ] Bulk operations (multi-select)
- [ ] Advanced preview (DOCX, XLSX)
- [ ] File versioning UI
- [ ] Dark mode
- [ ] Mobile optimization
- [ ] Offline support (Service Worker)
