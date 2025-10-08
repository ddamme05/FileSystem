#!/bin/bash
# Diagnostic script for S3 upload issues on EC2

echo "========================================="
echo "S3 Upload Diagnostics for EC2"
echo "========================================="
echo ""

echo "1. EC2 Instance Metadata (IMDSv2) Check:"
echo "---------------------------------------"
TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600" -s)
if [ -n "$TOKEN" ]; then
    echo "✓ IMDSv2 token obtained successfully"
    echo ""
    echo "Instance IAM Role:"
    curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/iam/security-credentials/
    echo ""
    echo ""
    echo "IAM Role Credentials (checking if available):"
    ROLE_NAME=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/iam/security-credentials/)
    if [ -n "$ROLE_NAME" ]; then
        curl -H "X-aws-ec2-metadata-token: $TOKEN" -s "http://169.254.169.254/latest/meta-data/iam/security-credentials/$ROLE_NAME" | grep -E "(Code|Type|AccessKeyId)" | head -3
    else
        echo "✗ No IAM role attached to this instance!"
    fi
else
    echo "✗ Failed to get IMDSv2 token"
fi
echo ""

echo "2. Docker Container AWS Environment:"
echo "-----------------------------------"
docker compose exec app env | grep -E "^AWS" | sort
echo ""

echo "3. Backend Container IMDSv2 Test:"
echo "--------------------------------"
echo "Testing if container can reach EC2 metadata service..."
docker compose exec app sh -c 'TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600" -s -m 5 2>&1); if [ -n "$TOKEN" ] && [ "$TOKEN" != "curl"* ]; then echo "✓ Container can access IMDSv2"; curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/iam/security-credentials/ 2>&1; else echo "✗ Container cannot access IMDSv2 (hop limit issue)"; echo "Error: $TOKEN"; fi'
echo ""

echo "4. S3 Bucket Access Test from Container:"
echo "---------------------------------------"
docker compose exec app sh -c 'aws s3 ls s3://${AWS_S3_BUCKET}/ 2>&1 | head -10 || echo "S3 access failed"'
echo ""

echo "5. Backend Application Logs (S3 related):"
echo "----------------------------------------"
docker compose logs app | grep -i -E "(s3|aws|credential|upload)" | tail -20
echo ""

echo "6. IMDSv2 Hop Limit Check:"
echo "-------------------------"
INSTANCE_ID=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/instance-id)
if [ -n "$INSTANCE_ID" ]; then
    echo "Instance ID: $INSTANCE_ID"
    echo "Current hop limit:"
    aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --query 'Reservations[0].Instances[0].MetadataOptions.HttpPutResponseHopLimit' 2>&1 || echo "Cannot query (aws cli might not be configured)"
fi
echo ""

echo "========================================="
echo "Common Fixes:"
echo "========================================="
echo ""
echo "1. If container cannot access IMDSv2, increase hop limit:"
echo "   aws ec2 modify-instance-metadata-options --instance-id $INSTANCE_ID --http-put-response-hop-limit 2"
echo ""
echo "2. If no IAM role attached, attach one with S3 permissions:"
echo "   - Go to EC2 Console → Instance → Actions → Security → Modify IAM role"
echo "   - Attach a role with AmazonS3FullAccess or custom S3 policy"
echo ""
echo "3. If credentials are expired/invalid, restart containers:"
echo "   docker compose -f docker-compose.yml -f docker-compose.prod.yml restart app"
echo ""
echo "4. Check backend logs for detailed error:"
echo "   docker compose logs -f app"

