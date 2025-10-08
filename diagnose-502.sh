#!/bin/bash
# Diagnostic script for 502 Bad Gateway errors

echo "========================================="
echo "502 Bad Gateway Diagnostics"
echo "========================================="
echo ""

echo "1. Container Status:"
echo "-------------------"
docker compose ps
echo ""

echo "2. Backend Health Check:"
echo "----------------------"
docker compose exec app curl -f http://localhost:8080/actuator/health || echo "Backend health check FAILED"
echo ""

echo "3. Frontend -> Backend Connectivity:"
echo "----------------------------------"
docker compose exec frontend curl -f http://app:8080/actuator/health || echo "Frontend cannot reach backend!"
echo ""

echo "4. Backend Logs (last 30 lines):"
echo "--------------------------------"
docker compose logs --tail=30 app
echo ""

echo "5. Frontend/Nginx Logs (last 30 lines):"
echo "--------------------------------------"
docker compose logs --tail=30 frontend
echo ""

echo "6. Network Inspection:"
echo "--------------------"
docker network inspect file-system_default | grep -A 10 "Containers"
echo ""

echo "7. Backend Environment Check:"
echo "---------------------------"
docker compose exec app env | grep -E "(AWS|SPRING|DD)" | grep -v PASSWORD | grep -v SECRET
echo ""

echo "========================================="
echo "Quick Fixes to Try:"
echo "========================================="
echo "1. Restart containers: docker compose restart"
echo "2. Check backend logs: docker compose logs -f app"
echo "3. Check if backend is responding: docker compose exec app curl http://localhost:8080/actuator/health"
echo "4. Rebuild if needed: docker compose up -d --build --force-recreate"

