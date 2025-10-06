#!/bin/bash

# Quick start script for the fully dockerized File Storage application
# This script builds and starts all services (PostgreSQL, Backend, Frontend, Datadog)

set -e

echo "🐳 File Storage - Docker Deployment"
echo "===================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if secrets exist
if [ ! -d ".secrets" ] || [ ! -f ".secrets/db_password" ] || [ ! -f ".secrets/jwt_secret" ]; then
    echo "⚠️  Secrets not found. Creating .secrets directory..."
    mkdir -p .secrets
    
    echo "Please enter a secure database password:"
    read -s db_password
    echo -n "$db_password" > .secrets/db_password
    
    echo ""
    echo "Generating secure JWT secret..."
    openssl rand -base64 64 | tr -d '\n' > .secrets/jwt_secret
    
    echo ""
    echo "Do you want to configure Datadog monitoring? (y/N)"
    read -r use_datadog
    
    if [[ "$use_datadog" =~ ^[Yy]$ ]]; then
        echo "Enter your Datadog API key:"
        read -s dd_api_key
        echo -n "$dd_api_key" > .secrets/dd_api_key
        
        echo ""
        echo "Enter your Datadog site (e.g., datadoghq.com):"
        read dd_site
        echo -n "$dd_site" > .secrets/dd_site
    else
        echo -n "dummy_key" > .secrets/dd_api_key
        echo -n "datadoghq.com" > .secrets/dd_site
    fi
    
    chmod 600 .secrets/*
    echo "✅ Secrets created successfully"
    echo ""
fi

# Check for AWS credentials
if [ ! -f "$HOME/.aws/credentials" ]; then
    echo "⚠️  AWS credentials not found at ~/.aws/credentials"
    echo "File upload/download will not work without AWS S3 credentials."
    echo ""
    echo "Continue anyway? (y/N)"
    read -r continue_without_aws
    
    if [[ ! "$continue_without_aws" =~ ^[Yy]$ ]]; then
        echo "Please configure AWS credentials and run this script again."
        exit 1
    fi
fi

echo "🔨 Building images..."
docker compose build

echo ""
echo "🚀 Starting services..."
docker compose up -d

echo ""
echo "⏳ Waiting for services to be healthy..."
sleep 5

# Wait for services
max_attempts=30
attempt=0

while [ $attempt -lt $max_attempts ]; do
    if docker compose ps | grep -q "unhealthy"; then
        echo "   Still waiting... (attempt $((attempt+1))/$max_attempts)"
        sleep 2
        attempt=$((attempt+1))
    else
        break
    fi
done

echo ""
echo "✅ Services are starting up!"
echo ""
echo "📊 Service Status:"
docker compose ps

echo ""
echo "🌐 Access Points:"
echo "   Frontend:  http://localhost:3000"
echo "   Backend:   http://localhost:8080/api"
echo "   Database:  localhost:5433"
echo ""
echo "🔑 Test Credentials:"
echo "   Username: demouser"
echo "   Password: Demo123!"
echo ""
echo "📝 Useful Commands:"
echo "   View logs:        docker compose logs -f"
echo "   Stop services:    docker compose down"
echo "   Rebuild:          docker compose up -d --build"
echo ""
echo "🎉 All done! Visit http://localhost:3000 to get started."




