#!/bin/bash
# Deployment script for Search Engine on Beget VPS (or any Ubuntu VPS)

set -e

echo "========================================="
echo "Search Engine Deployment Script"
echo "========================================="
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo "❌ Error: .env file not found!"
    echo "Please create .env file from .env.example"
    echo "Run: cp .env.example .env"
    echo "Then edit .env with your configuration"
    exit 1
fi

# Load environment variables
source .env

# Check required environment variables
required_vars=("MYSQL_ROOT_PASSWORD" "MYSQL_DATABASE" "MYSQL_USER" "MYSQL_PASSWORD" "INDEXING_SITES")
for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "❌ Error: $var is not set in .env file"
        exit 1
    fi
done

echo "✅ Environment variables loaded"
echo ""

# Stop existing containers
echo "🛑 Stopping existing containers..."
docker-compose down

# Pull latest images
echo "📥 Pulling latest base images..."
docker-compose pull mysql nginx certbot

# Build application
echo "🔨 Building application..."
docker-compose build app

# Start services
echo "🚀 Starting services..."
docker-compose up -d

# Wait for services to be healthy
echo "⏳ Waiting for services to be healthy..."
sleep 10

# Check if MySQL is healthy
echo "Checking MySQL health..."
for i in {1..30}; do
    if docker-compose exec -T mysql mysqladmin ping -h localhost -u root -p"$MYSQL_ROOT_PASSWORD" &> /dev/null; then
        echo "✅ MySQL is healthy"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ MySQL failed to start"
        docker-compose logs mysql
        exit 1
    fi
    sleep 2
done

# Check if application is healthy
echo "Checking application health..."
for i in {1..60}; do
    if curl -f http://localhost:8080/actuator/health &> /dev/null; then
        echo "✅ Application is healthy"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "❌ Application failed to start"
        docker-compose logs app
        exit 1
    fi
    sleep 3
done

echo ""
echo "========================================="
echo "✅ Deployment completed successfully!"
echo "========================================="
echo ""
echo "Application is running at:"
echo "  - HTTP: http://localhost:8080"
echo ""
echo "To view logs: docker-compose logs -f"
echo "To stop: docker-compose down"
echo "To restart: docker-compose restart"
echo ""
