#!/bin/bash
# Production deployment script for EC2
# This script pulls latest code and deploys without overwriting EC2-specific configs

set -e  # Exit on error

echo "========================================="
echo "File System Production Deployment"
echo "========================================="
echo ""

# Check if we're in the right directory
if [ ! -f "docker-compose.yml" ]; then
    echo "Error: docker-compose.yml not found. Run this script from the project root."
    exit 1
fi

# Store current branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "Current branch: $CURRENT_BRANCH"

# Stash EC2-specific changes (like modified docker-compose.yml)
echo ""
echo "Stashing local EC2 changes..."
git stash push -m "EC2 deployment stash $(date +%Y%m%d_%H%M%S)" \
    docker-compose.yml \
    docker-compose.override.yml \
    .secrets/ \
    .env || echo "Nothing to stash"

# Pull latest code
echo ""
echo "Pulling latest code from origin/$CURRENT_BRANCH..."
git pull origin "$CURRENT_BRANCH"

# Restore EC2-specific changes
echo ""
echo "Restoring EC2-specific configurations..."
git stash pop || echo "No stash to restore (this is fine)"

# Show what will be deployed
echo ""
echo "Current commit:"
git log -1 --oneline
echo ""

# Ask for confirmation
read -p "Deploy this version? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Deployment cancelled."
    exit 0
fi

# Stop current containers
echo ""
echo "Stopping current containers..."
docker compose down

# Rebuild and start
echo ""
echo "Building and starting containers..."
docker compose up -d --build

# Wait for health checks
echo ""
echo "Waiting for 'app' service to be healthy..."
timeout=120 # Wait for a maximum of 2 minutes
while ! docker compose ps app | grep -q '(healthy)'; do
    sleep 5
    timeout=$((timeout - 5))
    if [ $timeout -le 0 ]; then
        echo "Error: App container did not become healthy in time."
        echo "Dumping last 100 lines of logs:"
        docker compose logs --tail=100 app
        exit 1
    fi
    echo "Still waiting..."
done
echo "App is healthy!"

# Show status
echo ""
echo "Container status:"
docker compose ps

# Show recent logs
echo ""
echo "Recent logs (Ctrl+C to exit):"
echo "========================================="
docker compose logs --tail=50 -f