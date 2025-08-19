#!/bin/sh
set -Eeuo pipefail

read_secret() {
  # strips both \r and \n if present
  tr -d '\r\n' < "$1"
}

export SPRING_DATASOURCE_PASSWORD="$(read_secret /run/secrets/db_password)"
export SECURITY_JWT_SECRET="$(read_secret /run/secrets/jwt_secret)"
export AWS_S3_BUCKET="$(read_secret /run/secrets/aws_s3_bucket)"
export AWS_REGION="$(read_secret /run/secrets/aws_region)"

exec java -jar /app/app.jar "$@"
