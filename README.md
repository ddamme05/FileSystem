# File System Service

[![CI](https://img.shields.io/github/actions/workflow/status/ddamme05/File-System/ci.yml?branch=main&label=CI)](https://github.com/ddamme05/File-System/actions/workflows/ci.yml)
[![Architecture](https://img.shields.io/badge/docs-architecture-blue)](architecture.md)
[![API Docs](https://img.shields.io/badge/docs-API%20Docs-blue)](docs/api.md)

## 🚧 Work in Progress 🚧

Welcome to my File System Service project!

### About This Project

This project is my hands-on lab for learning and mastering a variety of modern backend technologies, including:

-   **Java & Spring Boot:** Building a robust REST API.
-   **Spring Data JPA:** Interacting with a PostgreSQL database.
-   **AWS S3:** For scalable, cloud-based file storage.
-   **Spring Security:** Implementing JWT-based authentication and authorization.
-   **Docker:** Containerizing the application for consistent development and deployment.
-   **CI/CD Principles:** Setting up a pipeline for automated builds and testing. (Maybe)

As I learn, I'll be implementing new features and refactoring existing code. The primary goal is education and practical experience, so you'll see the project evolve over time.

## API Docs

- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

Authentication: Click "Authorize" in Swagger and paste `Bearer <JWT>` after obtaining a token via `POST /api/v1/auth/login` or `POST /api/v1/auth/register`.

## Quickstart (Local)

1) Start Postgres

```bash
docker compose up -d postgres-db
```

2) Run the app (Dev)

```bash
./gradlew bootRun
```

Or with Docker Compose (reads `docs/env.example`):

```bash
docker compose up -d --build app
```

3) Test auth and files

- Register: `POST /api/v1/auth/register`
- Login: `POST /api/v1/auth/login`
- Upload: `POST /files/upload` (multipart form, field `file`)
- List: `GET /files`
- Download URL: `GET /files/download/{id}`
- Delete: `DELETE /files/{id}`

Default config is in `src/main/resources/application.yml`. For dev/local, env is loaded via Compose `env_file` only. In production, pass env vars explicitly; the container does not read `.env` files.

More details: see `docs/api.md`.
 
Example environment file: see `docs/env.example`.