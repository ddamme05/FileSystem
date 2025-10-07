# File Storage - React Frontend

**Stack:** React 19 + TypeScript + Vite + TanStack Query + Tailwind  
**Status:** Phases 0-8 Complete + Security Hardening ✅ (Production-Ready & Secure!)

## 🛡️ Security Features

- ✅ **Strict Content Security Policy (CSP)** - Primary XSS defense
- ✅ **Sandboxed Iframes** - Isolated preview content
- ✅ **Referrer Policy** - No S3 URL leaking
- ✅ **Security Headers** - HSTS, X-Frame-Options, X-Content-Type-Options
- ✅ **Rate Limiting** - Login, upload, and API protection (Nginx-ready)
- ✅ **E2E Testing** - Golden path coverage with Playwright
- ✅ **SQL Injection Protection** - JPA prepared statements (backend)
- ✅ **User Isolation** - Explicit user ID filtering in queries

---

## 🚀 Quick Start

**Prerequisites:** Make sure the Spring Boot backend is running on port 8080!

```bash
# Start backend with Docker (in project root)
docker compose up -d

# Or run locally
./gradlew bootRun

# Start frontend dev server (in client/ directory)
./dev.sh

# Or manually with fnm
export PATH="/home/ddamme05/.local/share/fnm:$PATH"
eval "$(fnm env)"
npm run dev
```

Then open **http://localhost:5173**

### Test Credentials

**Username:** `demouser`  
**Password:** `Demo123!`

(Or register a new user at the login page)

---

## 📦 Available Commands

```bash
# Development
npm run dev                # Start dev server (http://localhost:5173)
npm run build              # Production build
npm run build:check        # Check bundle size (must be < 250 KB)
npm run preview            # Preview production build

# Testing
npm test                   # Run unit tests (Vitest)
npm run test:ui            # Run unit tests with UI
npm run test:e2e           # Run E2E tests (Playwright)
npm run test:e2e:ui        # Run E2E tests with UI
npm run test:e2e:debug     # Debug E2E tests
npm run test:all           # Run all tests (unit + E2E)

# Code Quality
npm run lint               # Run ESLint
npm run format             # Format code with Prettier
```

---

## 🏗️ Project Structure

```
client/
├── src/
│   ├── api/              # API client (crash-proof, handles 204, Retry-After)
│   ├── components/       # React components
│   │   ├── auth/         # Authentication components
│   │   ├── files/        # File management components
│   │   └── layout/       # Layout components
│   ├── contexts/         # React contexts (Auth, etc.)
│   ├── hooks/            # Custom React hooks
│   ├── lib/              # Utilities, feature flags
│   ├── pages/            # Page components
│   ├── types/            # TypeScript type definitions
│   └── test/             # Test setup
├── public/               # Static assets
└── deployment/           # Nginx configs
    └── nginx/
```

---

## ✨ Tech Stack

### Core

- **React 19** - Form Actions, useOptimistic, use() hook
- **TypeScript 5.6** - Strict mode + extra safety flags
- **Vite 6** - Fast dev server, optimized builds

### State & Routing

- **React Router 6.28** - Client-side routing
- **TanStack Query 5.60** - Server state, caching
- **React Context** - Auth state

### UI & Styling

- **Tailwind CSS 3.4** - Utility-first styling
- **Lucide React** - Icons
- **Sonner** - Toast notifications

### File Upload

- **react-dropzone 14.3** - Drag & drop
- **XMLHttpRequest** - Upload progress tracking

### Testing

- **Vitest** - Unit tests
- **Testing Library** - Component tests
- **MSW** - API mocking
- **Playwright** - E2E tests (golden path coverage)

---

## 🔐 Feature Flags

Download authentication is controlled via `src/lib/featureFlags.ts`:

```typescript
ENABLE_COPY_LINK: false  // Disabled for MVP (Option C)
```

**Migration Path:** Option C (MVP) → Option B (tickets) → Option A (cookies)

---

## 🧪 Testing

**Unit Tests:** 6/6 passing ✅  
**E2E Tests:** Golden path coverage ✅

```bash
# Run all tests
npm run test:all

# Or separately
npm test          # Unit tests
npm run test:e2e  # E2E tests
```

### Unit Test Coverage

- ✅ API Client: 204 No Content handling
- ✅ API Client: Empty body handling
- ✅ API Client: text/plain errors
- ✅ API Client: Retry-After (seconds)
- ✅ API Client: Retry-After (HTTP-date)
- ✅ API Client: Request ID correlation

### E2E Test Coverage (Golden Path)

- ✅ Login with test credentials
- ✅ Upload a file
- ✅ Preview the file
- ✅ Download the file
- ✅ Delete the file
- ✅ Logout
- ✅ Upload validation (file too large)
- ✅ Unauthorized access prevention
- ✅ Keyboard accessibility

---

## 🎯 Implementation Status

- [x] **Phase 0: Foundation** ✅
- [x] **Phase 1: Authentication** ✅
- [x] **Phase 2: App Shell** ✅
- [x] **Phase 3: File Upload** ✅
- [x] **Phase 4: File Listing** ✅
- [x] **Phase 5: Pagination** ✅
- [x] **Phase 6: Download** ✅
- [x] **Phase 7: Delete** ✅
- [x] **Phase 8: Testing & Polish** ✅
    - [x] File Preview Modal (images, PDFs, text, video, audio)
    - [x] Search, filter, sort
    - [x] Upload panel (Google Drive style)
    - [x] User isolation fix
- [x] **Security Hardening** ✅
    - [x] Strict CSP headers
    - [x] Sandboxed iframes
    - [x] Referrer policy on media
    - [x] E2E testing (Playwright)
    - [x] Production Nginx config
- [ ] **Phase 9: Deployment** (Next)
    - See `SECURITY_DEPLOYMENT_GUIDE.md` for instructions

---

## 📸 Snapshot Scripts

Generate snapshots for sharing/review:

```bash
# From project root:
./generate_client_code_snapshot.py    # Application code
./generate_client_config_snapshot.py  # Configuration files
```

---

## 🔧 Configuration

**TypeScript:** Strict mode with extra safety flags  
**ESLint:** Modern flat config (ESLint 9)  
**Vite Proxy:** `/api` → `http://localhost:8080`  
**Bundle Target:** < 250 KB gzipped

---

## 🌐 Development

**Dev Server:** http://localhost:5173  
**API Proxy:** http://localhost:8080 (Spring Boot backend)

**Hot Reload:** Enabled  
**Source Maps:** Enabled in dev

---

## 📝 Next Steps

### 🚀 Ready to Deploy?

See **`../SECURITY_DEPLOYMENT_GUIDE.md`** for:

- Complete deployment instructions
- Security testing checklist
- Nginx configuration
- SSL/HTTPS setup with Let's Encrypt
- Monitoring and maintenance

### 🎯 Post-Deployment (Option B)

After deployment, you can enhance the app with:

1. **Download Tickets** - Enable "Copy Link" feature with short-lived tokens
2. **Bulk Operations** - Multi-select and bulk delete
3. **Admin Dashboard** - View all users and system metrics
4. **HttpOnly Cookies** (Option A) - Ultimate security upgrade

See `../FE_IMPLEMENTATION_PLAN.md` for the complete implementation roadmap.

---

**Questions?** All documentation is consolidated in the project root.
