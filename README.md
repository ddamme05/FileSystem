# File Storage System

A secure, production-ready file storage application with S3 backend, JWT authentication, and comprehensive monitoring.

**Stack:** Spring Boot 3.2 (Java 21) + React 19 + PostgreSQL + AWS S3 + Docker  
**Status:** 🚀 Production-Ready & Fully Dockerized

---

## 🎯 Features

- **Secure Authentication:** JWT-based auth with BCrypt password hashing
- **File Operations:** Upload, download, preview, delete with drag-and-drop
- **Cloud Storage:** AWS S3 backend with presigned URLs
- **Rich Previews:** Images, PDFs, text files, videos, and audio
- **User Isolation:** Every file is private to its owner
- **Rate Limiting:** Protect against abuse with configurable limits
- **Observability:** Datadog APM integration with metrics and traces
- **Production Security:** HTTPS, CSP, security headers, and hardened containers

---

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose
- AWS account with S3 bucket (or use LocalStack for testing)

### One-Command Deployment

```bash
./docker-up.sh
```

This script will:
1. Generate secure secrets (JWT, database password)
2. Build all Docker images (backend, frontend, database)
3. Start all services with health checks
4. Create a demo user account

**Access the application:**
- **Frontend:** http://localhost:3000
- **Backend API:** http://localhost:8080/api
- **API Docs:** http://localhost:8080/swagger-ui.html
- **Database:** localhost:5433 (PostgreSQL)

**Default Credentials:**
- Username: `demouser`
- Password: `Demo123!`

---

## 🏗️ System Architecture

### Component Diagram

```mermaid
graph TB
    subgraph "Client Layer"
        Browser[Web Browser]
        React[React 19 Application]
    end
    
    subgraph "Frontend Components"
        AuthGuard[AuthGuard]
        FilesPage[FilesPage]
        UploadZone[UploadZone]
        FilesTable[FilesTable]
        PreviewModal[PreviewModal]
        UploadPanel[UploadPanel]
    end
    
    subgraph "State Management"
        AuthContext[AuthContext]
        UploadContext[UploadContext]
        TanStackQuery[TanStack Query Cache]
    end
    
    subgraph "API Layer"
        APIClient[API Client]
    end
    
    subgraph "Backend Services"
        FileController[FileController]
        AuthController[AuthController]
        FileService[FileService]
        MetadataService[MetadataService]
        StorageService[S3StorageService]
        UserService[UserService]
    end
    
    subgraph "Security Layer"
        JwtFilter[JWT Auth Filter]
        RateLimitFilter[Rate Limit Filter]
        SecurityConfig[Security Config]
    end
    
    subgraph "Data Layer"
        UserRepo[UserRepository]
        MetadataRepo[MetadataRepository]
        PostgreSQL[(PostgreSQL Database)]
    end
    
    subgraph "External Services"
        S3[(AWS S3 Storage)]
        Datadog[Datadog APM]
    end
    
    Browser --> React
    React --> AuthGuard
    AuthGuard --> FilesPage
    FilesPage --> UploadZone
    FilesPage --> FilesTable
    FilesPage --> PreviewModal
    React --> UploadPanel
    
    AuthGuard --> AuthContext
    UploadZone --> UploadContext
    UploadPanel --> UploadContext
    FilesPage --> TanStackQuery
    
    React --> APIClient
    APIClient --> JwtFilter
    JwtFilter --> RateLimitFilter
    RateLimitFilter --> FileController
    RateLimitFilter --> AuthController
    
    FileController --> FileService
    AuthController --> UserService
    FileService --> MetadataService
    FileService --> StorageService
    
    MetadataService --> MetadataRepo
    UserService --> UserRepo
    MetadataRepo --> PostgreSQL
    UserRepo --> PostgreSQL
    
    StorageService --> S3
    FileService -.->|Metrics| Datadog
    StorageService -.->|Traces| Datadog
```

### Entity-Relationship Diagram

```mermaid
erDiagram
    USER ||--o{ FILE_METADATA : owns
    
    USER {
        bigint id PK
        varchar username UK "Unique, indexed"
        varchar email UK "Unique, indexed"
        varchar password "BCrypt hashed"
        varchar role "USER or ADMIN"
        timestamp created_at
        timestamp updated_at
        boolean account_non_expired
        boolean account_non_locked
        boolean credentials_non_expired
        boolean enabled
    }
    
    FILE_METADATA {
        bigint id PK
        bigint user_id FK "References USER.id"
        varchar original_filename "User's filename"
        varchar storage_key UK "S3 key, UUID-based"
        bigint size "File size in bytes"
        varchar content_type "MIME type"
        timestamp upload_timestamp "Auto-generated"
        timestamp update_timestamp "Auto-updated"
    }
```

### UML Class Diagram

```mermaid
classDiagram
    class User {
        -Long id
        -String username
        -String email
        -String password
        -Role role
        -Instant createdAt
        -Instant updatedAt
        +getAuthorities() Collection~GrantedAuthority~
        +getUsername() String
        +getPassword() String
        +isEnabled() boolean
    }
    
    class FileMetadata {
        -Long id
        -User user
        -String originalFilename
        -String storageKey
        -long size
        -String contentType
        -Instant uploadTimestamp
        -Instant updateTimestamp
        +getId() Long
        +getUser() User
        +getOriginalFilename() String
        +getStorageKey() String
    }
    
    class FileController {
        -FileService fileService
        -MetadataService metadataService
        +uploadFile(MultipartFile, User) ResponseEntity~FileDto~
        +getUserFiles(User, int, int) ResponseEntity~PagedFileResponse~
        +downloadFile(Long, User) ResponseEntity~Void~
        +deleteFile(Long, User) ResponseEntity~Void~
    }
    
    class AuthController {
        -UserService userService
        -JwtService jwtService
        -AuthenticationManager authManager
        +register(RegisterRequest) ResponseEntity~AuthResponse~
        +login(LoginRequest) ResponseEntity~AuthResponse~
    }
    
    class FileService {
        -MetadataService metadataService
        -StorageService storageService
        +upload(User, MultipartFile) FileMetadata
        +presignDownloadUrl(User, Long) String
        +presignViewUrl(User, Long) String
        +delete(User, Long) void
    }
    
    class MetadataService {
        <<interface>>
        +save(FileMetadata) FileMetadata
        +findById(Long) FileMetadata
        +findOwnedById(User, Long) FileMetadata
        +deleteById(Long) void
        +findByUser(User, Pageable) Page~FileMetadata~
    }
    
    class StorageService {
        <<interface>>
        +upload(MultipartFile) String
        +upload(MultipartFile, String) String
        +generatePresignedDownloadUrl(String) String
        +generatePresignedViewUrl(String, String) String
        +delete(String) void
    }
    
    class S3StorageService {
        -S3Client s3Client
        -S3Presigner s3Presigner
        -AwsProperties awsProperties
        +upload(MultipartFile) String
        +generatePresignedDownloadUrl(String) String
        +delete(String) void
    }
    
    class UserService {
        <<interface>>
        +registerUser(RegisterRequest) User
    }
    
    class JwtService {
        -String jwtSecretBase64
        -long tokenExpirationMilliseconds
        +generateToken(String, Map) String
        +generateToken(UserDetails) String
        +extractUsername(String) String
        +isTokenValid(String, UserDetails) boolean
    }
    
    class MetadataRepository {
        <<interface>>
        +findByUserOrderByUploadTimestampDesc(User) List~FileMetadata~
        +findByUserIdOrderByUploadTimestampDesc(Long, Pageable) Page~FileMetadata~
        +findByIdAndUserId(Long, Long) Optional~FileMetadata~
    }
    
    class UserRepository {
        <<interface>>
        +findByUsername(String) Optional~User~
        +findByEmail(String) Optional~User~
    }
    
    User "1" --> "0..*" FileMetadata : owns
    FileController --> FileService : uses
    FileController --> MetadataService : uses
    AuthController --> UserService : uses
    AuthController --> JwtService : uses
    FileService --> MetadataService : uses
    FileService --> StorageService : uses
    StorageService <|.. S3StorageService : implements
    MetadataService --> MetadataRepository : uses
    UserService --> UserRepository : uses
    MetadataRepository --> FileMetadata : manages
    UserRepository --> User : manages
```

---

## 🛠️ Technology Stack

### Backend
- **Framework:** Spring Boot 3.2.2
- **Language:** Java 21 (LTS)
- **Database:** PostgreSQL 17
- **ORM:** Spring Data JPA (Hibernate)
- **Security:** Spring Security + JWT
- **Storage:** AWS S3 SDK
- **Observability:** Micrometer + Datadog APM
- **Build Tool:** Gradle 8.5

### Frontend
- **Framework:** React 19
- **Language:** TypeScript 5.3
- **Build Tool:** Vite 5
- **State Management:** TanStack Query v5
- **Styling:** Tailwind CSS 3
- **HTTP Client:** Fetch API with custom wrapper
- **Testing:** Playwright (E2E), Vitest (unit)

### Infrastructure
- **Containerization:** Docker + Docker Compose
- **Web Server:** Nginx (reverse proxy + static serving)
- **Deployment:** AWS EC2 (production) or local Docker
- **DNS:** DuckDNS (free dynamic DNS)
- **SSL:** Let's Encrypt (Certbot)

---

## 📦 Project Structure

```
File-System/
├── src/                          # Backend (Spring Boot)
│   └── main/java/org/ddamme/
│       ├── controller/           # REST API endpoints
│       ├── service/              # Business logic
│       ├── database/             # JPA entities & repositories
│       ├── security/             # JWT, filters, rate limiting
│       ├── dto/                  # Data transfer objects
│       └── exception/            # Custom exceptions
├── client/                       # Frontend (React)
│   ├── src/
│   │   ├── components/           # React components
│   │   ├── pages/                # Page components
│   │   ├── hooks/                # Custom React hooks
│   │   ├── contexts/             # React contexts
│   │   └── api/                  # API client
│   └── public/                   # Static assets
├── deployment/                   # Production configs
│   └── nginx/                    # Nginx reverse proxy config
├── docs/                         # Architecture documentation
├── .secrets/                     # Secret files (git-ignored)
├── docker-compose.yml            # Multi-container orchestration
├── Dockerfile                    # Backend container image
└── docker-up.sh                  # One-command startup script
```

---

## 🔒 Security Features

### Authentication & Authorization
- JWT tokens with 24-hour expiration
- BCrypt password hashing (strength 12)
- User isolation (files are private to owner)
- Auto-logout on token expiration

### API Security
- Rate limiting (Bucket4j + Caffeine cache)
- CORS configuration
- Request correlation IDs
- Audit logging for security events

### Container Security
- Read-only filesystems
- Dropped capabilities
- Non-root users
- Security profiles (no-new-privileges)

### Frontend Security
- Content Security Policy (CSP)
- Sandboxed iframes for previews
- Referrer policy (no S3 URL leaking)
- HTTPS enforcement in production

---

## 🚢 Deployment Options

### Option 1: Local Development (Docker Compose)

```bash
# Start all services
./docker-up.sh

# Or manually
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

### Option 2: Production on AWS EC2

See deployment guides in `docs/` directory for complete instructions.

**High-level steps:**
1. Launch EC2 instance (t3.medium recommended)
2. Install Docker & Docker Compose
3. Setup DuckDNS domain
4. Clone repository and configure secrets
5. Deploy with `docker-compose up -d`
6. Setup Nginx reverse proxy
7. Install SSL certificate (Let's Encrypt)

**Estimated Cost:** ~$43/month (EC2 + S3 + data transfer)

---

## 🧪 Testing

### Backend Tests
```bash
./gradlew test
```

### Frontend E2E Tests
```bash
cd client
npm run test:e2e          # Headless
npm run test:e2e:ui       # With UI
```

### Frontend Unit Tests
```bash
cd client
npm run test
```

---

## 📊 Monitoring

### Datadog Integration (Optional)

The application includes Datadog APM integration for production monitoring:

**Metrics Tracked:**
- HTTP request rates and latency
- Database query performance
- S3 operation metrics
- JVM metrics (heap, GC, threads)
- Custom business metrics (uploads, downloads, errors)

**Setup:**
1. Sign up for Datadog
2. Add API key to `.secrets/dd_api_key`
3. Restart services

**Remove Datadog:**
Comment out the `datadog` service in `docker-compose.yml` if not needed.

---

## 🔧 Configuration

### Environment Variables

**Backend:**
- `SPRING_DATASOURCE_URL` - PostgreSQL connection string
- `AWS_S3_BUCKET_NAME` - S3 bucket name
- `AWS_REGION` - AWS region (default: us-east-1)
- `SECURITY_JWT_SECRET` - JWT signing secret (base64)
- `SPRING_PROFILES_ACTIVE` - Active profile (dev/prod)

**Frontend:**
- Configured via `vite.config.ts`
- API URL: `http://localhost:8080` (dev), reverse-proxied in production

### Secret Files

Located in `.secrets/` directory (git-ignored):
- `db_password` - PostgreSQL password
- `jwt_secret` - JWT signing secret (base64-encoded)
- `aws_access_key_id` - AWS access key (or use IAM role)
- `aws_secret_access_key` - AWS secret key (or use IAM role)
- `dd_api_key` - Datadog API key (optional)
- `dd_site` - Datadog site (optional)

**Generate secrets:**
```bash
# Database password
openssl rand -base64 32 > .secrets/db_password

# JWT secret (256-bit)
openssl rand -base64 32 > .secrets/jwt_secret
```

---

## 🐛 Troubleshooting

### Backend won't start
```bash
# Check logs
docker-compose logs app

# Verify secrets exist
ls -la .secrets/

# Rebuild if needed
docker-compose build app
```

### Frontend shows 401 errors
- Check that backend is running on port 8080
- Verify JWT token in browser localStorage
- Check browser console for CORS errors

### Files won't upload
- Verify S3 bucket exists and is accessible
- Check AWS credentials in `.secrets/`
- Ensure file size is under 10MB limit
- Review backend logs: `docker-compose logs app`

### Database connection failed
- Check PostgreSQL is running: `docker-compose ps`
- Verify password in `.secrets/db_password`
- Check connection string in environment

---

## 📈 Performance

**Benchmarks from Datadog(t3.small EC2):**
- File upload (1MB): ~500ms
- File list (20 items): ~30ms
- File download (presigned URL generation): ~20ms

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Built with Spring Boot, React, and AWS**
