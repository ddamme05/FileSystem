#!/usr/bin/env bash
set -e

echo "🪣 Creating S3 bucket for file system..."
awslocal s3api create-bucket --bucket filesystem-s3 --region us-east-1

echo "✅ LocalStack S3 setup complete!"

