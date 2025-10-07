#!/bin/bash
#
# EC2 Initial Setup Script
# Run this after cloning the repository on your EC2 instance
#
# Usage: ./scripts/ec2-setup.sh
#

set -e  # Exit on any error

echo "🚀 File Storage - EC2 Setup Script"
echo "===================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if running on EC2
if [ ! -f /sys/hypervisor/uuid ] && [ ! -d /sys/devices/virtual/dmi/id/ ]; then
    echo -e "${YELLOW}⚠️  Warning: This doesn't appear to be an EC2 instance${NC}"
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed${NC}"
    echo "Please install Docker first:"
    echo "  Amazon Linux: sudo dnf install -y docker && sudo systemctl start docker"
    echo "  Ubuntu: curl -fsSL https://get.docker.com | sudo sh"
    exit 1
fi

# Check if Docker Compose is installed
if ! docker compose version &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not installed${NC}"
    echo "Installing Docker Compose..."
    sudo mkdir -p /usr/local/lib/docker/cli-plugins
    sudo curl -SL https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-linux-x86_64 \
      -o /usr/local/lib/docker/cli-plugins/docker-compose
    sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
    echo -e "${GREEN}✅ Docker Compose installed${NC}"
fi

echo ""
echo "📝 Creating environment files..."
echo ""

# Create .env file if it doesn't exist
if [ ! -f .env ]; then
    echo "Creating .env file..."
    
    # Generate random passwords
    POSTGRES_PASSWORD=$(openssl rand -base64 32 | tr -d "=+/" | cut -c1-32)
    JWT_SECRET=$(openssl rand -base64 48 | tr -d "=+/" | cut -c1-64)
    
    cat > .env << EOF
# Database
POSTGRES_DB=filedb
POSTGRES_USER=fileuser
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}

# JWT
JWT_SECRET=${JWT_SECRET}

# AWS S3
AWS_S3_BUCKET=filesystem-s3
AWS_REGION=us-east-1

# Application
SPRING_PROFILES_ACTIVE=prod
EOF
    
    echo -e "${GREEN}✅ .env file created with random passwords${NC}"
    echo -e "${YELLOW}⚠️  IMPORTANT: Save these credentials somewhere safe!${NC}"
    echo ""
    echo "  PostgreSQL Password: ${POSTGRES_PASSWORD}"
    echo "  JWT Secret: ${JWT_SECRET}"
    echo ""
else
    echo -e "${YELLOW}ℹ️  .env file already exists, skipping...${NC}"
fi

# Create secrets directory
echo ""
echo "📁 Creating secrets directory..."
mkdir -p .secrets

# Create secrets from .env
if [ ! -f .secrets/db_password ]; then
    source .env
    echo -n "${POSTGRES_PASSWORD}" > .secrets/db_password
    echo -n "${JWT_SECRET}" > .secrets/jwt_secret
    chmod 600 .secrets/*
    echo -e "${GREEN}✅ Docker secrets created${NC}"
else
    echo -e "${YELLOW}ℹ️  Secrets already exist, skipping...${NC}"
fi

# Check for AWS credentials
echo ""
echo "🔑 Checking AWS credentials..."
if [ ! -d ~/.aws ] || [ ! -f ~/.aws/credentials ]; then
    echo -e "${YELLOW}⚠️  AWS credentials not found${NC}"
    echo ""
    echo "Choose AWS authentication method:"
    echo "  1) IAM Instance Role (Recommended for EC2)"
    echo "  2) AWS Credentials File"
    echo ""
    read -p "Enter choice (1 or 2): " -n 1 -r AWS_CHOICE
    echo ""
    
    if [[ $AWS_CHOICE == "1" ]]; then
        echo ""
        echo "To use IAM Instance Role:"
        echo "  1. Go to AWS Console → IAM → Roles → Create Role"
        echo "  2. Select EC2 → Attach AmazonS3FullAccess policy"
        echo "  3. Name it 'FileStorageEC2Role'"
        echo "  4. EC2 → Select Instance → Actions → Security → Modify IAM role"
        echo "  5. Select 'FileStorageEC2Role'"
        echo ""
        echo "Then, remove AWS_PROFILE from docker-compose.yml"
        echo ""
        read -p "Press Enter when done..."
    else
        echo ""
        echo "Setting up AWS credentials file..."
        mkdir -p ~/.aws
        
        read -p "Enter AWS Access Key ID: " AWS_ACCESS_KEY_ID
        read -p "Enter AWS Secret Access Key: " AWS_SECRET_ACCESS_KEY
        read -p "Enter AWS Region [us-east-1]: " AWS_REGION
        AWS_REGION=${AWS_REGION:-us-east-1}
        
        cat > ~/.aws/credentials << EOF
[file-system-app-role]
aws_access_key_id = ${AWS_ACCESS_KEY_ID}
aws_secret_access_key = ${AWS_SECRET_ACCESS_KEY}
EOF
        
        cat > ~/.aws/config << EOF
[profile file-system-app-role]
region = ${AWS_REGION}
output = json
EOF
        
        chmod 600 ~/.aws/credentials
        chmod 600 ~/.aws/config
        echo -e "${GREEN}✅ AWS credentials configured${NC}"
    fi
else
    echo -e "${GREEN}✅ AWS credentials already configured${NC}"
fi

# Setup swap for free-tier instances
echo ""
echo "💾 Checking swap space..."
SWAP_SIZE=$(free -m | awk '/Swap:/ {print $2}')
if [ "$SWAP_SIZE" -eq 0 ]; then
    echo -e "${YELLOW}⚠️  No swap detected${NC}"
    read -p "Create 1GB swap file? (Recommended for t2.micro/t3.small) (Y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Nn]$ ]]; then
        echo "Creating swap file..."
        sudo fallocate -l 1G /swapfile
        sudo chmod 600 /swapfile
        sudo mkswap /swapfile
        sudo swapon /swapfile
        echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
        echo -e "${GREEN}✅ Swap space created and enabled${NC}"
        free -h
    fi
else
    echo -e "${GREEN}✅ Swap already configured (${SWAP_SIZE}MB)${NC}"
fi

# Note about multi-stage Docker build
echo ""
echo "ℹ️  Build Strategy:"
echo "   This project uses a multi-stage Dockerfile that builds the JAR"
echo "   inside Docker. No local Gradle build is needed unless you want"
echo "   to test locally before Docker."
echo ""
read -p "Build locally with Gradle? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🔨 Building Spring Boot application..."
    ./gradlew clean bootJar
    echo -e "${GREEN}✅ Application built${NC}"
else
    echo -e "${YELLOW}ℹ️  Skipping local build (Docker will build during 'docker compose up')${NC}"
fi

# Summary
echo ""
echo "=================================="
echo "✅ Setup Complete!"
echo "=================================="
echo ""
echo "Next steps:"
echo ""
echo "1. If using EC2 IAM role for S3, edit docker-compose.yml and comment out:"
echo "   - AWS_PROFILE, AWS_EC2_METADATA_DISABLED"
echo "   - volumes: \${HOME}/.aws:/aws:ro"
echo ""
echo "2. Start services (builds with multi-stage Dockerfile):"
echo "   docker compose up -d --build"
echo ""
echo "3. Watch logs (use service names):"
echo "   docker compose logs -f app"
echo "   docker compose logs -f frontend"
echo "   docker compose logs -f postgres-db"
echo ""
echo "4. Check status (wait for 'healthy' status):"
echo "   docker compose ps"
echo ""
echo "5. Test access:"
echo "   curl http://localhost/health"
echo "   curl http://localhost:8080/actuator/health"
echo ""
echo "6. Access from browser:"
echo "   http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)"
echo ""
echo "   (If you changed port mapping to 3000:80, use :3000 in URL)"
echo ""
echo "7. After HTTP works, set up SSL:"
echo "   See docs/SSL_SETUP_GUIDE.md"
echo ""
echo "=================================="

