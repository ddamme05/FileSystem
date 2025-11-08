#!/bin/sh
# POSIX-safe: works in ash/dash/bash/ksh
set -eu

# Set conservative umask for any future files
umask 077

# Read Docker secret files (must run as root since secrets are root-owned)
if [ -f /run/secrets/db_password ]; then
  SPRING_DATASOURCE_PASSWORD=$(cat /run/secrets/db_password | tr -d '\r\n')
  export SPRING_DATASOURCE_PASSWORD
else
  echo "Error: Secret file /run/secrets/db_password not found" >&2
  exit 1
fi

if [ -f /run/secrets/jwt_secret ]; then
  SECURITY_JWT_SECRET=$(cat /run/secrets/jwt_secret | tr -d '\r\n')
  export SECURITY_JWT_SECRET
else
  echo "Error: Secret file /run/secrets/jwt_secret not found" >&2
  exit 1
fi

# Validate secrets are not empty
if [ -z "$SPRING_DATASOURCE_PASSWORD" ]; then
  echo "Error: db_password secret is empty" >&2
  exit 1
fi

if [ -z "$SECURITY_JWT_SECRET" ]; then
  echo "Error: jwt_secret is empty" >&2
  exit 1
fi

# Note: AWS credentials now come from EC2 instance role via IMDSv2
# AWS_REGION and AWS_S3_BUCKET are set as environment variables in docker-compose.yml

# Source the detected tessdata path (set during Docker build)
# This ensures TESSDATA_PREFIX matches the actual Tesseract version installed
if [ -f /etc/profile.d/tessdata.sh ]; then
  . /etc/profile.d/tessdata.sh
  echo "Loaded TESSDATA_PREFIX from detection: ${TESSDATA_PREFIX}"
else
  echo "Warning: /etc/profile.d/tessdata.sh not found, using default TESSDATA_PREFIX"
fi

# Drop privileges and start Java app
# Ubuntu Jammy uses gosu (installed in Dockerfile)
# JNA temp directory is now set via JAVA_TOOL_OPTIONS in docker-compose.yml
if command -v gosu >/dev/null 2>&1; then
  exec gosu appuser java -jar /app/app.jar "$@"
elif command -v runuser >/dev/null 2>&1; then
  exec runuser -u appuser -- java -jar /app/app.jar "$@"
else
  exec su -s /bin/sh -c 'exec java -jar /app/app.jar "$@"' appuser -- "$@"
fi

