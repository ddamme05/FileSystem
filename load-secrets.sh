#!/bin/sh
set -Eeuo pipefail

# Load secrets from files and export as environment variables, then start the app

read_secret() {
  # strips both \r and \n if present
  tr -d '\r\n' < "$1"
}

export SPRING_DATASOURCE_PASSWORD=$(read_secret /run/secrets/db_password)
export SECURITY_JWT_SECRET=$(read_secret /run/secrets/jwt_secret)
export AWS_S3_BUCKET=$(read_secret /run/secrets/aws_s3_bucket)
export AWS_REGION=$(read_secret /run/secrets/aws_region)
export AWS_ACCESS_KEY_ID=$(read_secret /run/secrets/aws_access_key_id)
export AWS_SECRET_ACCESS_KEY=$(read_secret /run/secrets/aws_secret_access_key)

# Start the application with proper signal handling
exec java -jar /app/app.jar "$@"
