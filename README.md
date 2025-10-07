# File Storage System

A secure, production-ready file storage application with S3 backend, built with Spring Boot and React 19.

**Status:** 🐳 **Fully Dockerized & Production-Ready!**

---

## 🚀 Quick Start (Docker - Recommended)

The **entire application** runs in Docker containers. Just one command:

```bash
./docker-up.sh
```

That's it! The script will:

- Create secure secrets (database password, JWT secret)
- Build all Docker images
- Start PostgreSQL, backend, and frontend
- Set up monitoring (optional Datadog)

**Access the application:**

- Frontend: **http://localhost:3000**
- Backend API: http://localhost:8080/api
- Database: localhost:5433

**Test credentials:**

- Username: `demouser`
- Password: `Demo123!`

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Docker Compose Network                   │
│                                                              │
│  ┌──────────────┐      ┌──────────────┐      ┌───────────┐ │
│  │   Frontend   │────▶ │   Backend    │────▶ │ PostgreSQL│ │
│  │   (Nginx +   │      │  (Spring     │      │           │ │
│  │    React 19) │      │   Boot)      │      │           │ │
│  │  Port: 3000  │      │  Port: 8080  │      │Port: 5433 │ │
│  └──────────────┘      └──────────────┘      └───────────┘ │
│         │                      │                            │
│         └──────────────────────┴───────────────┐            │
│                                                 │            │
│                                        ┌────────▼────────┐   │
│                                        │    Datadog      │   │
│                                        │     Agent       │   │
│                                        └─────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### What's Running

- **Frontend** (React 19 + Nginx):
    - Modern SPA with drag-and-drop uploads
    - File preview (images, PDFs, text, video, audio)
    - Search, filter, sort
    - Strict CSP security headers

- **Backend** (Spring Boot + Java 21):
    - RESTful API with JWT authentication
    - S3 file storage with presigned URLs
    - User isolation & rate limiting
    - Datadog APM instrumentation

- **Database** (PostgreSQL 17):
    - Persistent storage for metadata
    - Health checks & backups ready

---

## 📋 Prerequisites

- **Docker** 24+ ([Install](https://docs.docker.com/get-docker/))
- **Docker Compose** 2.20+ (included with Docker Desktop)
- **AWS Account** with S3 bucket configured
- **(Optional)** Datadog account for monitoring

---

## 🔧 Manual Setup (Development)

### Backend Only

```bash
# Start database and backend
docker compose up -d postgres-db app

# Access at http://localhost:8080
```

### Frontend Only (Development Mode)

```bash
# Ensure backend is running first
docker compose up -d postgres-db app

# Start frontend dev server
cd client
./dev.sh

# Access at http://localhost:5173
```

---

## 📚 Documentation

- **`DOCKER_DEPLOYMENT.md`** - Complete Docker deployment guide
- **`SECURITY_DEPLOYMENT_GUIDE.md`** - Production deployment & security
- **`FE_IMPLEMENTATION_PLAN.md`** - Frontend architecture & phases
- **`PROJECT_STATUS.md`** - Current status & roadmap
- **`client/README.md`** - Frontend-specific documentation

---

## 🛡️ Security Features

- ✅ **XSS Protection** - Strict Content Security Policy (CSP)
- ✅ **SQL Injection Protection** - Parameterized queries (JPA)
- ✅ **User Isolation** - Explicit user ID filtering
- ✅ **Rate Limiting** - Login, upload, and API throttling
- ✅ **Secure Previews** - Sandboxed iframes, no referrer leakage
- ✅ **HTTPS-Ready** - Full SSL/TLS configuration
- ✅ **Container Security** - Read-only filesystems, minimal capabilities
- ✅ **Secrets Management** - Docker secrets, no env vars

See `SECURITY_DEPLOYMENT_GUIDE.md` for the complete security audit checklist.

---

## 🧪 Testing

### End-to-End Tests

```bash
cd client

# Install Playwright browsers (first time only)
npx playwright install

# Run E2E tests
npm run test:e2e

# Run with UI
npm run test:e2e:ui
```

### Unit Tests

```bash
# Backend tests
./gradlew test

# Frontend tests
cd client && npm test
```

---

## 🚀 Deployment

### Step 1: Docker (You Are Here)

✅ Everything is dockerized and ready to go!

### Step 2: Production Server

Follow `SECURITY_DEPLOYMENT_GUIDE.md` for:

- Setting up a production server
- Configuring Nginx reverse proxy
- SSL/TLS with Let's Encrypt
- Monitoring and backups

### Step 3: CI/CD (Optional)

- GitHub Actions for automated builds
- Automated security scans
- Deployment to container registry

---

## 🔄 Common Commands

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# View logs
docker compose logs -f

# Rebuild after code changes
docker compose up -d --build

# Database backup
docker compose exec postgres-db pg_dump -U user file_system_db > backup.sql

# Shell into containers
docker compose exec frontend sh
docker compose exec app sh
docker compose exec postgres-db psql -U user -d file_system_db
```

---

## 📊 Features

### Current (MVP - Production Ready)

- ✅ User registration & JWT authentication
- ✅ File upload (drag & drop, 10MB limit)
- ✅ File download with presigned S3 URLs
- ✅ File preview (images, PDFs, text, video, audio)
- ✅ File deletion with confirmation
- ✅ Search, filter, and sort
- ✅ Pagination (20/50/100 items per page)
- ✅ Upload progress tracking (Google Drive-style panel)
- ✅ Rate limiting (Nginx-level)
- ✅ Responsive mobile design
- ✅ Keyboard accessibility (WCAG 2.1 AA)

### Roadmap (Post-Deployment)

- [ ] **Download Tickets** - Shareable 60-second links
- [ ] **Bulk Operations** - Multi-select & bulk delete
- [ ] **Admin Dashboard** - User management, system metrics
- [ ] **HttpOnly Cookies** - Ultimate auth security upgrade
- [ ] **Advanced Search** - Server-side filtering by date/size
- [ ] **User Profiles** - Change password, storage usage

---

## 🎯 Tech Stack

### Frontend

- **React 19** - Form Actions, `use()`, `useOptimistic()`
- **TypeScript** - Strict mode with extra safety flags
- **Vite** - Fast builds, HMR
- **TanStack Query** - Server state & caching
- **Tailwind CSS** - Utility-first styling
- **Playwright** - E2E testing

### Backend

- **Spring Boot 3.4** - REST API framework
- **Java 21** - Virtual threads, pattern matching
- **PostgreSQL 17** - Primary database
- **AWS S3** - File storage
- **Datadog** - APM & observability

### Infrastructure

- **Docker** - Containerization
- **Nginx** - Reverse proxy & static file serving
- **Let's Encrypt** - Free SSL/TLS certificates

---

## 📈 Performance

- **Frontend Bundle:** 93.26 KB gzipped (63% under 250 KB budget)
- **Lighthouse Score:** Performance > 90, Accessibility > 95
- **Docker Build:** ~2 minutes for full stack
- **Cold Start:** ~30 seconds for all services

---

## 🆘 Troubleshooting

### Services Won't Start

```bash
# Check Docker is running
docker info

# Check logs
docker compose logs

# Remove old containers and try again
docker compose down -v
docker compose up -d --build
```

### Frontend Shows "Network Error"

```bash
# Ensure backend is healthy
docker compose ps
curl http://localhost:8080/actuator/health

# Check nginx config
docker compose exec frontend nginx -t
```

### File Upload Fails

```bash
# Verify AWS credentials
docker compose exec app ls -la /aws

# Check S3 permissions
docker compose logs app | grep S3
```

See `DOCKER_DEPLOYMENT.md` for complete troubleshooting guide.

---

## 🤝 Contributing

This is a learning project, but feedback is welcome!

1. Check out `FE_IMPLEMENTATION_PLAN.md` for architecture
2. Run tests: `npm run test:all` (frontend) and `./gradlew test` (backend)
3. Follow security best practices from `SECURITY_DEPLOYMENT_GUIDE.md`

---

## 📝 License

This project is for educational purposes.

---

## 🎉 Quick Links

- **Start Application:** `./docker-up.sh`
- **Access Frontend:** http://localhost:3000
- **View Logs:** `docker compose logs -f`
- **Run Tests:** `cd client && npm run test:e2e`
- **Deployment Guide:** `DOCKER_DEPLOYMENT.md`
- **Security Guide:** `SECURITY_DEPLOYMENT_GUIDE.md`

---

**Built with ❤️ using Docker, Spring Boot, and React 19**
