# File System API

This is a concise, copy-paste friendly reference for the REST API. For interactive docs, use Swagger UI at
`/swagger-ui/index.html`.

## Authentication

All non-auth endpoints require a JWT via the `Authorization: Bearer <token>` header.

### POST /api/v1/auth/register

- Description: Register a new user and receive a JWT
- Request (application/json):

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "secret123"
}
```

- Response 200 (application/json):

```json
{
  "token": "<JWT>",
  "username": "alice",
  "role": "USER"
}
```

- Errors: 400 (validation), 409 (username/email conflict)

### POST /api/v1/auth/login

- Description: Login and receive a JWT
- Request (application/json):

```json
{
  "username": "alice",
  "password": "secret123"
}
```

- Response 200 (application/json): same as register
- Errors: 400 (validation), 401 (bad credentials)

## Files

Base path: `/api/v1/files` (requires JWT)

### POST /api/v1/files/upload

- Description: Upload a file to S3; create metadata.
- Consumes: multipart/form-data
- Form fields:
    - `file`: the file to upload
- Curl:

```bash
curl -X POST "http://localhost:8080/api/v1/files/upload" \
  -H "Authorization: Bearer <JWT>" \
  -H "Accept: application/json" \
  -F "file=@/path/to/local-file.bin"
```

- Response 200 (application/json):

```json
{
  "id": 1,
  "originalFilename": "local-file.bin",
  "storageKey": "<generated-key>",
  "size": 12345,
  "contentType": "application/octet-stream",
  "uploaderUsername": "alice",
  "uploadTimestamp": "2025-01-01T12:00:00Z",
  "updateTimestamp": "2025-01-01T12:00:00Z"
}
```

### GET /api/v1/files/download/{id}

- Description: Get a short-lived presigned URL for your file.
- Curl:

```bash
curl -X GET "http://localhost:8080/api/v1/files/download/1" \
  -H "Authorization: Bearer <JWT>" \
  -H "Accept: application/json"
```

- Response 200 (application/json):

```json
{
  "downloadUrl": "https://s3.amazonaws.com/...signed-url..."
}
```

- Errors: 404 (not found or not your file)

### DELETE /api/v1/files/{id}

- Description: Delete your file and metadata.
- Curl:

```bash
curl -X DELETE "http://localhost:8080/api/v1/files/1" \
  -H "Authorization: Bearer <JWT>"
```

- Response 204 (no content)
- Errors: 404 (not found or not your file)

### GET /api/v1/files

- Description: List your files with pagination (0-based page).
- Query params: `page` (default 0), `size` (default 20)
- Curl:

```bash
curl -X GET "http://localhost:8080/api/v1/files?page=0&size=10" \
  -H "Authorization: Bearer <JWT>" \
  -H "Accept: application/json"
```

- Response 200 (application/json):

```json
{
  "files": [
    {
      "id": 1,
      "originalFilename": "local-file.bin",
      "size": 12345,
      "contentType": "application/octet-stream",
      "uploadTimestamp": "2025-01-01T12:00:00Z"
    }
  ],
  "currentPage": 0,
  "totalPages": 1,
  "totalElements": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

## Error Model

All errors use the same schema:

```json
{
  "timestamp": "2025-01-01T12:00:00",
  "status": 404,
  "error": "Not Found", 
  "message": "FileMetadata not found with id : '1'",
  "path": "uri=/files/download/1"
}
```

- Common statuses: 400 (validation), 401 (unauthenticated), 404 (not found), 409 (duplicate), 500 (server error).

## Security

- Public: `/api/v1/auth/**`, `/actuator/health`, `/actuator/health/**`, `/actuator/info`
- Public (dev only): `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` (disabled in production)
- Authenticated: everything else
- JWT: Bearer token, expiration configurable via `security.jwt.expiration-ms`

## Observability

- **Metrics**: Exported via Micrometer → DogStatsD → Datadog Agent (UDP:8125)
- **Tracing**: Automatic via dd-java-agent → Datadog Agent (APM:8126)
- **Logs**: JSON structured logging with trace correlation
- **Health**: `/actuator/health` and `/actuator/health/{liveness,readiness}` for K8s probes

## Useful Links (Development Only)

- Swagger UI: `http://localhost:8080/swagger-ui/index.html` (disabled in production)
- OpenAPI JSON: `http://localhost:8080/v3/api-docs` (disabled in production)
- Health endpoint: `http://localhost:8080/actuator/health`
- App info: `http://localhost:8080/actuator/info`

