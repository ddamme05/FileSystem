# File System API

This is a concise, copy-paste friendly reference for the REST API. For interactive docs, use Swagger UI at `/swagger-ui/index.html`.

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

Base path: `/files` (requires JWT)

### POST /files/upload
- Description: Upload a file to S3; create metadata.
- Consumes: multipart/form-data
- Form fields:
  - `file`: the file to upload
- Curl:
```bash
curl -X POST "http://localhost:8080/files/upload" \
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

### GET /files/download/{id}
- Description: Get a short-lived presigned URL for your file.
- Curl:
```bash
curl -X GET "http://localhost:8080/files/download/1" \
  -H "Authorization: Bearer <JWT>" \
  -H "Accept: application/json"
```
- Response 200 (application/json):
```json
{
  "downloadUrl": "https://s3.amazonaws.com/...signed-url..."
}
```
- Errors: 403 (not your file), 404 (unknown id)

### DELETE /files/{id}
- Description: Delete your file and metadata.
- Curl:
```bash
curl -X DELETE "http://localhost:8080/files/1" \
  -H "Authorization: Bearer <JWT>"
```
- Response 204 (no content)
- Errors: 403 (not your file), 404 (unknown id)

### GET /files
- Description: List your files with pagination (0-based page).
- Query params: `page` (default 0), `size` (default 20)
- Curl:
```bash
curl -X GET "http://localhost:8080/files?page=0&size=10" \
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
  "timestamp": "01-01-2025 12:00:00 PM",
  "status": 403,
  "error": "Forbidden",
  "message": "You can only access your own files",
  "path": "uri=/files/download/1"
}
```
- Common statuses: 400 (validation), 401 (unauthenticated), 403 (ownership), 404 (not found), 409 (duplicate), 500 (server error).

## Security
- Public: `/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`, `/actuator/health`
- Authenticated: everything else
- JWT: Bearer token, expiration configurable via `security.jwt.expiration-ms`

## Useful Links
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

