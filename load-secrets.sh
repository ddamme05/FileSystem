#!/bin/sh
# POSIX-safe: works in ash/dash/bash/ksh
set -eu  # no -E, no pipefail in POSIX sh

# Set conservative umask for any future files
umask 077

read_secret() {
  _read_secret_file="$1"
  if [ ! -f "$_read_secret_file" ]; then
    echo "Error: Secret file $_read_secret_file not found" >&2
    exit 1
  fi
  # strips both \r and \n if present
  _read_secret_value=$(tr -d '\r\n' < "$_read_secret_file")
  if [ -z "$_read_secret_value" ]; then
    echo "Error: Secret file $_read_secret_file is empty" >&2
    exit 1
  fi
  echo "$_read_secret_value"
}

# Read secrets with validation (as root)
SPRING_DATASOURCE_PASSWORD="$(read_secret /run/secrets/db_password)"
SECURITY_JWT_SECRET="$(read_secret /run/secrets/jwt_secret)"
AWS_S3_BUCKET="$(read_secret /run/secrets/aws_s3_bucket)"
AWS_REGION="$(read_secret /run/secrets/aws_region)"
AWS_ACCESS_KEY_ID="$(read_secret /run/secrets/aws_access_key_id)"
AWS_SECRET_ACCESS_KEY="$(read_secret /run/secrets/aws_secret_access_key)"

# Export all variables
export SPRING_DATASOURCE_PASSWORD
export SECURITY_JWT_SECRET
export AWS_S3_BUCKET
export AWS_REGION
export AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY

# Drop privileges and start Java app with safer fallbacks
if command -v setpriv >/dev/null 2>&1; then
  # Try with --init-groups first, fall back to --clear-groups if unsupported
  if setpriv --reuid 10001 --regid 10001 --init-groups true 2>/dev/null; then
    exec setpriv --reuid 10001 --regid 10001 --init-groups java -jar /app/app.jar "$@"
  else
    exec setpriv --reuid 10001 --regid 10001 --clear-groups java -jar /app/app.jar "$@"
  fi
elif command -v runuser >/dev/null 2>&1; then
  exec runuser -u appuser -- java -jar /app/app.jar "$@"
else
  exec su -s /bin/sh -c 'exec java -jar /app/app.jar "$@"' appuser -- "$@"
fi
