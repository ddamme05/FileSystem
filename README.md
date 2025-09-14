# File System Service

[![Architecture](https://img.shields.io/badge/docs-architecture-blue)](architecture.md)
[![API Docs](https://img.shields.io/badge/docs-API%20Docs-blue)](docs/api.md)

A secure file storage service built with Spring Boot, featuring JWT authentication, AWS S3 storage, and PostgreSQL database with comprehensive observability.

## 🏗️ What's Included

- **REST API** with OpenAPI/Swagger documentation
- **JWT Authentication** with user registration/login
- **File Management** - upload, download (presigned URLs), delete, list
- **AWS S3 Integration** for scalable file storage
- **PostgreSQL Database** with Flyway migrations
- **Security** - file ownership validation, read-only containers
- **Observability** - Datadog APM, metrics, structured logging
- **Testing** - Unit tests + Integration tests with Testcontainers
- **Docker** - Multi-stage builds with security hardening

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Docker & Docker Compose
- (Optional) AWS S3 bucket for production

### 1. Setup Secrets
```bash
# Windows
.\setup-secrets.ps1

# Linux/macOS  
./load-secrets.sh
```

### 2. Start Services
```bash
# Start database only
docker compose up -d postgres-db

# Or start everything (app + db + monitoring)
docker compose up -d --build
```

### 3. Run Application
```bash
# Development mode (local Java)
./gradlew bootRun

# Or use Docker (production-like)
docker compose up -d app
```

##  Docker Commands

```bash
# Build and start all services
docker compose up -d --build

# View logs
docker compose logs -f app
docker compose logs -f postgres-db

# Stop services
docker compose down

# Clean up (removes volumes)
docker compose down -v
```

## 🔨 Gradle Commands

```bash
# Run application locally
./gradlew bootRun

# Run tests
./gradlew test integrationTest

# Build
./gradlew build

# Code formatting
./gradlew spotlessApply

# Test coverage reports
./gradlew test jacocoTestReport              # Unit test coverage only
./gradlew jacocoMergedReport                 # Unit + Integration test coverage
# Coverage reports: build/reports/jacoco/[test|jacocoMergedReport]/html/index.html
```

## 📖 API Documentation

- **Swagger UI:** http://localhost:8080/swagger-ui/index.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs

## 🔧 Configuration

### Profiles
- `dev` (default) - Local development
- `prod` - Production with enhanced security
- `integrationTest` - Test profile with Testcontainers

## 🏥 Health & Monitoring

```bash
# Health check
curl http://localhost:8080/actuator/health

# Metrics (Prometheus format)
curl http://localhost:8080/actuator/prometheus
```

## 🔒 Security Features

- JWT-based authentication with secure key management
- File ownership validation (users can only access their files)
- Read-only containers with dropped capabilities
- Secret management via Docker secrets
- SQL injection prevention with JPA/Hibernate
- CORS protection and security headers

## 🚧 Development Status

This is a learning project focused on modern backend development practices. Features are continuously being added and refined as I explore new technologies and patterns.

**Current Tech Stack:**
- Java 21 + Spring Boot 3.5
- PostgreSQL 17 + Flyway migrations  
- AWS S3 + presigned URLs
- Docker + Docker Compose
- Datadog APM + structured logging
- JUnit 5 + Testcontainers
- Gradle build system
- GitHub Actions CI/CD
