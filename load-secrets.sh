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

# Export all variables
export SPRING_DATASOURCE_PASSWORD
export SECURITY_JWT_SECRET

# Note: AWS credentials now come from EC2 instance role via IMDSv2
# AWS_REGION and AWS_S3_BUCKET are set as environment variables in docker-compose.yml

# Drop privileges and start Java app
# Alpine uses BusyBox which doesn't support setpriv --reuid
# Use su-exec (lightweight, designed for containers) or su as fallback
if command -v su-exec >/dev/null 2>&1; then
  # su-exec is the cleanest option for Alpine/Docker
  exec su-exec appuser java -jar /app/app.jar "$@"
elif command -v runuser >/dev/null 2>&1; then
  # Debian/Ubuntu have runuser
  exec runuser -u appuser -- java -jar /app/app.jar "$@"
else
  # Fallback to su (available everywhere)
  exec su -s /bin/sh -c 'exec java -jar /app/app.jar "$@"' appuser -- "$@"
fi
