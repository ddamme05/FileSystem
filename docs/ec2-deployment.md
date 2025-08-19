# EC2 Deployment with AWS SSM Parameter Store

This document explains how to deploy your application to EC2 using the same secret management pattern as your local development environment.

## Overview

The deployment uses **AWS SSM Parameter Store (Standard tier - FREE)** to store secrets securely, then fetches them at boot time to create the same `./.secrets/` directory structure that your docker-compose.yml expects.

## 1. Store Secrets in AWS SSM Parameter Store

First, store your secrets in SSM using the AWS CLI or Console:

```bash
# Store secrets as SecureString parameters (encrypted with AWS-managed KMS key - FREE)
aws ssm put-parameter --name "/prod/datadog/api_key" --value "YOUR_ACTUAL_DD_API_KEY" --type "SecureString"
aws ssm put-parameter --name "/prod/datadog/site" --value "us5.datadoghq.com" --type "String"
aws ssm put-parameter --name "/prod/database/password" --value "YOUR_STRONG_DB_PASSWORD" --type "SecureString"
aws ssm put-parameter --name "/prod/datadog/postgres_password" --value "YOUR_DD_PG_PASSWORD" --type "SecureString"
aws ssm put-parameter --name "/prod/jwt/secret" --value "YOUR_BASE64_JWT_SECRET" --type "SecureString"
aws ssm put-parameter --name "/prod/aws/s3_bucket" --value "your-production-s3-bucket" --type "String"
aws ssm put-parameter --name "/prod/aws/region" --value "us-east-1" --type "String"
```

## 2. Create IAM Role for EC2

Create an IAM role with this policy to allow the EC2 instance to read secrets:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "ssm:GetParameter",
                "ssm:GetParameters"
            ],
            "Resource": "arn:aws:ssm:*:*:parameter/prod/*"
        }
    ]
}
```

## 3. Launch EC2 Instance

Use this **cloud-init** user data script when launching your EC2 instance (Amazon Linux 2023):

```yaml
#cloud-config
packages:
  - docker
  - docker-compose-plugin
  - awscli
  - git

runcmd:
  # Setup Docker
  - systemctl enable docker
  - systemctl start docker
  - usermod -aG docker ec2-user
  
  # Clone your repository
  - su - ec2-user -c "git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git ~/file-system"
  
  # Create secrets directory
  - su - ec2-user -c "mkdir -p ~/file-system/.secrets"
  
  # Fetch secrets from SSM and write to local files
  - aws ssm get-parameter --name "/prod/datadog/api_key" --with-decryption --query Parameter.Value --output text > /home/ec2-user/file-system/.secrets/dd_api_key
  - aws ssm get-parameter --name "/prod/datadog/site" --query Parameter.Value --output text > /home/ec2-user/file-system/.secrets/dd_site
  - aws ssm get-parameter --name "/prod/database/password" --with-decryption --query Parameter.Value --output text > /home/ec2-user/file-system/.secrets/db_password
  - aws ssm get-parameter --name "/prod/datadog/postgres_password" --with-decryption --query Parameter.Value --output text > /home/ec2-user/file-system/.secrets/dd_pg_password
  - aws ssm get-parameter --name "/prod/jwt/secret" --with-decryption --query Parameter.Value --output text > /home/ec2-user/file-system/.secrets/jwt_secret
  - aws ssm get-parameter --name "/prod/aws/s3_bucket" --query Parameter.Value --output text > /home/ec2-user/file-system/.secrets/aws_s3_bucket
  - aws ssm get-parameter --name "/prod/aws/region" --query Parameter.Value --output text > /home/ec2-user/file-system/.secrets/aws_region
  
  # Set proper permissions on secret files
  - chown -R ec2-user:ec2-user /home/ec2-user/file-system/.secrets
  - chmod -R 600 /home/ec2-user/file-system/.secrets/*
  
  # Build and start the application
  - su - ec2-user -c "cd ~/file-system && docker compose pull"
  - su - ec2-user -c "cd ~/file-system && docker compose up -d"
```

## 4. Security Group Configuration

Configure your EC2 security group to allow:
- **Port 22** (SSH) from your IP
- **Port 8080** (HTTP) from anywhere or your load balancer
- **Port 443** (HTTPS) if using SSL termination

## 5. Benefits of This Approach

### ✅ **FREE**
- SSM Standard parameters: FREE
- AWS-managed KMS encryption: FREE
- No additional infrastructure costs

### ✅ **SECURE** 
- Secrets encrypted at rest in SSM
- Secrets only exist in memory/container filesystems
- No secrets in environment variables or docker inspect
- IAM-controlled access

### ✅ **NO DIFFS**
- Same `docker-compose.yml` file works locally and on EC2
- Same `.secrets/` directory structure everywhere
- No environment-specific configuration

## 6. Testing the Deployment

After EC2 boots:

```bash
# SSH to your instance
ssh ec2-user@YOUR_EC2_IP

# Check that secrets were fetched
ls -la ~/file-system/.secrets/

# Check containers are running
docker compose ps

# Check application logs
docker compose logs app

# Test the application
curl http://localhost:8080/actuator/health
```

## 7. Production Considerations

- **SSL/TLS**: Add a reverse proxy (nginx) or use Application Load Balancer for HTTPS
- **Monitoring**: Your Datadog agent will automatically start monitoring
- **Backups**: Set up automated PostgreSQL backups
- **Updates**: Use blue-green deployments or rolling updates
- **Secrets Rotation**: Update SSM parameters and restart services

## 8. Cost Breakdown

- **SSM Parameter Store Standard**: $0 (up to 10,000 parameters)
- **KMS Encryption**: $0 (AWS-managed keys)
- **Total Secret Management Cost**: **$0/month**
