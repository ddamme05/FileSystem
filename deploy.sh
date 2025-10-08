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

# Stash EC2-specific changes if they exist
echo ""
echo "Stashing local EC2 changes..."
STASH_NAME="EC2 deployment stash $(date +%Y%m%d_%H%M%S)"
STASH_REF=""

# Only stash files that exist and have changes
STASH_FILES=()
[ -f "docker-compose.yml" ] && git diff --quiet docker-compose.yml || STASH_FILES+=(docker-compose.yml)
[ -f ".env" ] && STASH_FILES+=(.env)

if [ ${#STASH_FILES[@]} -gt 0 ]; then
    git stash push -m "$STASH_NAME" "${STASH_FILES[@]}"
    STASH_REF=$(git rev-parse --short stash@{0})
    echo "Stashed changes as: $STASH_REF"
else
    echo "No local changes to stash"
fi

# Pull latest code
echo ""
echo "Pulling latest code from origin/$CURRENT_BRANCH..."
git pull origin "$CURRENT_BRANCH"

# Restore EC2-specific changes if we stashed them
if [ -n "$STASH_REF" ]; then
    echo ""
    echo "Restoring EC2-specific configurations..."
    git stash apply "stash@{0}"
    echo "Stash applied successfully (kept in stash list for safety)"
fi

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
docker compose -f docker-compose.yml -f docker-compose.prod.yml down

# Rebuild and start
echo ""
echo "Building and starting containers..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

# Wait for health checks
echo ""
echo "Waiting for services to be healthy..."
sleep 5

# Show status
echo ""
echo "Container status:"
docker compose ps

# Show recent logs (non-blocking)
echo ""
echo "Recent logs (showing last 50 lines):"
echo "========================================="
docker compose logs --tail=50
echo ""
echo "Deployment complete! Use 'docker compose logs -f' to follow logs."
