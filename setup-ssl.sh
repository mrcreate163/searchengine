#!/bin/bash
# SSL Certificate Setup Script for Search Engine
# This script obtains SSL certificates from Let's Encrypt

set -e

echo "========================================="
echo "SSL Certificate Setup Script"
echo "========================================="
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo "❌ Error: .env file not found!"
    exit 1
fi

# Load environment variables
source .env

# Check required variables
if [ -z "$DOMAIN_NAME" ] || [ -z "$SSL_EMAIL" ]; then
    echo "❌ Error: DOMAIN_NAME and SSL_EMAIL must be set in .env file"
    exit 1
fi

echo "Domain: $DOMAIN_NAME"
echo "Email: $SSL_EMAIL"
echo ""

# Create directories if they don't exist
mkdir -p ./certbot/conf
mkdir -p ./certbot/www

# Check if certificates already exist
if [ -d "./certbot/conf/live/$DOMAIN_NAME" ]; then
    echo "⚠️  Certificates already exist for $DOMAIN_NAME"
    read -p "Do you want to renew them? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Exiting..."
        exit 0
    fi
fi

# Ensure nginx is running without SSL first
echo "📝 Setting up initial nginx configuration..."
cat > ./nginx/conf.d/searchengine.conf << 'EOF'
server {
    listen 80;
    listen [::]:80;
    server_name DOMAIN_PLACEHOLDER;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

# Replace domain placeholder
sed -i "s/DOMAIN_PLACEHOLDER/$DOMAIN_NAME www.$DOMAIN_NAME/g" ./nginx/conf.d/searchengine.conf

# Restart nginx
echo "🔄 Restarting nginx..."
docker-compose restart nginx
sleep 5

# Obtain certificate
echo "🔒 Obtaining SSL certificate from Let's Encrypt..."
docker-compose run --rm certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email "$SSL_EMAIL" \
    --agree-tos \
    --no-eff-email \
    -d "$DOMAIN_NAME" \
    -d "www.$DOMAIN_NAME"

if [ $? -eq 0 ]; then
    echo "✅ Certificate obtained successfully!"
    
    # Now update nginx config with SSL
    echo "📝 Updating nginx configuration with SSL..."
    cat > ./nginx/conf.d/searchengine.conf << EOF
# HTTP configuration - redirects to HTTPS
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN_NAME www.$DOMAIN_NAME;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://\$host\$request_uri;
    }
}

# HTTPS configuration
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name $DOMAIN_NAME www.$DOMAIN_NAME;

    ssl_certificate /etc/letsencrypt/live/$DOMAIN_NAME/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN_NAME/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    client_max_body_size 10M;

    location / {
        proxy_pass http://app:8080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    location /health {
        proxy_pass http://app:8080/actuator/health;
        access_log off;
    }

    location ~* \.(jpg|jpeg|png|gif|ico|css|js|svg|woff|woff2|ttf|eot)\$ {
        proxy_pass http://app:8080;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
EOF

    # Reload nginx
    echo "🔄 Reloading nginx with SSL configuration..."
    docker-compose exec nginx nginx -s reload
    
    echo ""
    echo "========================================="
    echo "✅ SSL Certificate setup completed!"
    echo "========================================="
    echo ""
    echo "Your site is now accessible via HTTPS:"
    echo "  https://$DOMAIN_NAME"
    echo "  https://www.$DOMAIN_NAME"
    echo ""
    echo "Certificate will auto-renew every 12 hours"
else
    echo "❌ Failed to obtain certificate"
    exit 1
fi
