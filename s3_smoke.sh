#!/usr/bin/env bash
set -euo pipefail

API="http://localhost:8080"
USER="u1"
EMAIL="u1@example.com"
PASS="secret123"

# 0) Temp file to upload
TMPFILE="$(mktemp)"
echo "hello from $(date)" > "$TMPFILE"

# 1) Register (ignore errors if already exists)
curl -sS -X POST "$API/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" \
  | tee /tmp/reg.json >/dev/null || true

# 2) Token (fallback to login)
TOKEN="$(jq -r '.token // empty' /tmp/reg.json || true)"
if [ -z "${TOKEN}" ] || [ "${TOKEN}" = "null" ]; then
  TOKEN="$(curl -sS -X POST "$API/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" | jq -r '.token')"
fi
echo "TOKEN length: ${#TOKEN}"

# 3) Upload
UPLOAD_JSON="$(curl -sS -X POST "$API/api/v1/files/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$TMPFILE")"
echo "$UPLOAD_JSON" > /tmp/upload.json

# 4) Get file ID
ID="$(echo "$UPLOAD_JSON" | jq -r '.id // .fileId // .file_id // empty')"
if [ -z "$ID" ] || [ "$ID" = "null" ]; then
  LIST_JSON="$(curl -sS -H "Authorization: Bearer $TOKEN" "$API/api/v1/files")"
  echo "$LIST_JSON" > /tmp/list.json
  ID="$(echo "$LIST_JSON" | jq -r '
    if type=="array" then .[0].id
    elif has("files") then .files[0].id
    elif has("data") then .data[0].id
    else .id end
  ')"
fi
echo "File ID: $ID"

# 5) Presigned URL
DL_RESP="$(curl -sS -H "Authorization: Bearer $TOKEN" "$API/api/v1/files/download/$ID")"
echo "$DL_RESP" > /tmp/download_resp.json
URL="$(echo "$DL_RESP" | jq -r '.downloadUrl // .url // .presignedUrl // .presigned_url // empty')"

if [ -z "$URL" ] || [ "$URL" = "null" ]; then
  echo "ERROR: No presigned URL returned. Full response saved to /tmp/download_resp.json" >&2
  exit 1
fi
echo "Presigned URL: $URL"

# 6) Download from S3
curl -fL "$URL" -o /tmp/out.bin
echo "Saved to /tmp/out.bin (size: $(stat -c%s /tmp/out.bin 2>/dev/null || wc -c </tmp/out.bin) bytes)"
echo "First bytes:"
head -c 80 /tmp/out.bin; echo
