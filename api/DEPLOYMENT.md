# Substrait Compliance API - Deployment Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Configuration](#configuration)
4. [Deployment Options](#deployment-options)
5. [Database Setup](#database-setup)
6. [Security](#security)
7. [Monitoring](#monitoring)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required
- **Java 11+** (for local deployment) OR **Podman/Docker** (for container deployment)
- **PostgreSQL 15+** (or use the provided docker-compose setup)
- **2GB RAM minimum** (4GB recommended)
- **Network access** to PostgreSQL port (5432)

### Optional
- **Podman Compose** or **Docker Compose** for orchestration
- **Nginx** or **Apache** for reverse proxy
- **Prometheus** for metrics collection

---

## Quick Start

### Option 1: Using Docker Compose (Recommended)

```bash
# 1. Navigate to the api directory
cd api

# 2. Copy and configure environment variables
cp .env.example .env
nano .env  # Edit with your values

# 3. Generate JWT secret
export JWT_SECRET=$(openssl rand -base64 32)
echo "JWT_SECRET=$JWT_SECRET" >> .env

# 4. Start all services
docker-compose up -d

# 5. Check logs
docker-compose logs -f api

# 6. Verify health
curl http://localhost:8080/actuator/health
```

### Option 2: Using Podman Compose

```bash
# Same as Docker Compose, but use podman-compose
podman-compose up -d
podman-compose logs -f api
```

### Option 3: Local Development

```bash
# 1. Start PostgreSQL (or use existing instance)
# 2. Configure application.yml
# 3. Build and run
cd api
./gradlew bootRun
```

---

## Configuration

### Environment Variables

#### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `JWT_SECRET` | Secret key for JWT tokens (min 256 bits) | `openssl rand -base64 32` |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/substrait_compliance` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `substrait` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `secure_password` |

#### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_EXPIRATION` | Token expiration in milliseconds | `86400000` (24h) |
| `RATE_LIMIT_DEFAULT_LIMIT` | Max requests per window | `1000` |
| `RATE_LIMIT_REFILL_DURATION_MINUTES` | Rate limit window | `60` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |
| `LOGGING_LEVEL_IO_SUBSTRAIT` | Log level | `INFO` |

### Application Configuration

Edit `api/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:86400000}

rate-limit:
  enabled: true
  default-limit: ${RATE_LIMIT_DEFAULT_LIMIT:1000}
  refill-duration-minutes: ${RATE_LIMIT_REFILL_DURATION_MINUTES:60}
```

---

## Deployment Options

### 1. Container Deployment (Production)

#### Build Container Image

```bash
# Using Podman
cd /path/to/substrait-compliance-private
podman build -t substrait-compliance-api:1.0.0 -f api/Containerfile .

# Using Docker
docker build -t substrait-compliance-api:1.0.0 -f api/Containerfile .
```

#### Run Container

```bash
# Create network
podman network create substrait-network

# Run PostgreSQL
podman run -d \
  --name substrait-db \
  --network substrait-network \
  -e POSTGRES_DB=substrait_compliance \
  -e POSTGRES_USER=substrait \
  -e POSTGRES_PASSWORD=changeme \
  -v substrait_data:/var/lib/postgresql/data \
  -p 5432:5432 \
  postgres:15-alpine

# Run API
podman run -d \
  --name substrait-api \
  --network substrait-network \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://substrait-db:5432/substrait_compliance \
  -e SPRING_DATASOURCE_USERNAME=substrait \
  -e SPRING_DATASOURCE_PASSWORD=changeme \
  -e JWT_SECRET=$(openssl rand -base64 32) \
  -p 8080:8080 \
  substrait-compliance-api:1.0.0
```

### 2. Kubernetes Deployment

Create `k8s-deployment.yaml`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: substrait-api-secrets
type: Opaque
stringData:
  jwt-secret: "your-jwt-secret-here"
  db-password: "your-db-password-here"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: substrait-compliance-api
spec:
  replicas: 3
  selector:
    matchLabels:
      app: substrait-api
  template:
    metadata:
      labels:
        app: substrait-api
    spec:
      containers:
      - name: api
        image: substrait-compliance-api:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres-service:5432/substrait_compliance"
        - name: SPRING_DATASOURCE_USERNAME
          value: "substrait"
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: substrait-api-secrets
              key: db-password
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: substrait-api-secrets
              key: jwt-secret
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
---
apiVersion: v1
kind: Service
metadata:
  name: substrait-api-service
spec:
  selector:
    app: substrait-api
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

Deploy:

```bash
kubectl apply -f k8s-deployment.yaml
kubectl get pods
kubectl logs -f deployment/substrait-compliance-api
```

### 3. Systemd Service (Linux)

Create `/etc/systemd/system/substrait-api.service`:

```ini
[Unit]
Description=Substrait Compliance API
After=network.target postgresql.service

[Service]
Type=simple
User=substrait
WorkingDirectory=/opt/substrait-api
ExecStart=/usr/bin/java -jar /opt/substrait-api/substrait-compliance-api-1.0.0.jar
Restart=on-failure
RestartSec=10

Environment="SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/substrait_compliance"
Environment="SPRING_DATASOURCE_USERNAME=substrait"
Environment="SPRING_DATASOURCE_PASSWORD=changeme"
Environment="JWT_SECRET=your-jwt-secret"

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable substrait-api
sudo systemctl start substrait-api
sudo systemctl status substrait-api
```

---

## Database Setup

### Initial Setup

```bash
# Connect to PostgreSQL
psql -U postgres

# Create database and user
CREATE DATABASE substrait_compliance;
CREATE USER substrait WITH ENCRYPTED PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE substrait_compliance TO substrait;

# Connect to the database
\c substrait_compliance

# Grant schema privileges
GRANT ALL ON SCHEMA public TO substrait;
```

### Flyway Migrations

Migrations run automatically on startup. To run manually:

```bash
cd api
./gradlew flywayMigrate \
  -Pflyway.url=jdbc:postgresql://localhost:5432/substrait_compliance \
  -Pflyway.user=substrait \
  -Pflyway.password=your_password
```

### Backup and Restore

```bash
# Backup
pg_dump -U substrait -h localhost substrait_compliance > backup.sql

# Restore
psql -U substrait -h localhost substrait_compliance < backup.sql
```

---

## Security

### 1. Generate JWT Secret

```bash
# Generate a secure random secret (256 bits minimum)
openssl rand -base64 32

# Or use a longer key
openssl rand -base64 64
```

### 2. SSL/TLS Configuration

#### Using Nginx Reverse Proxy

```nginx
server {
    listen 443 ssl http2;
    server_name api.substrait.io;

    ssl_certificate /etc/ssl/certs/substrait.crt;
    ssl_certificate_key /etc/ssl/private/substrait.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

#### Using Spring Boot SSL

Add to `application.yml`:

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: substrait
```

### 3. Firewall Configuration

```bash
# Allow API port
sudo ufw allow 8080/tcp

# Allow PostgreSQL (if external access needed)
sudo ufw allow 5432/tcp

# Enable firewall
sudo ufw enable
```

### 4. API Key Management

Generate API keys:

```sql
-- Insert API key with write scope
INSERT INTO api_keys (key_hash, name, scopes, active, created_at)
VALUES (
  encode(digest('your-api-key', 'sha256'), 'hex'),
  'Production Client',
  '["read", "write"]'::jsonb,
  true,
  NOW()
);
```

### 5. Rate Limiting

Configure in `application.yml`:

```yaml
rate-limit:
  enabled: true
  default-limit: 1000  # requests per window
  refill-duration-minutes: 60  # window duration
```

---

## Monitoring

### 1. Health Checks

```bash
# Overall health
curl http://localhost:8080/actuator/health

# Liveness probe
curl http://localhost:8080/actuator/health/liveness

# Readiness probe
curl http://localhost:8080/actuator/health/readiness
```

### 2. Metrics

```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Application metrics
curl http://localhost:8080/actuator/metrics
```

### 3. Logging

Configure log levels in `application.yml`:

```yaml
logging:
  level:
    io.substrait: INFO
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
  file:
    name: /var/log/substrait-api/application.log
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

### 4. Prometheus Configuration

Create `prometheus.yml`:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'substrait-api'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

### 5. Grafana Dashboard

Import dashboard JSON or create custom panels:
- Request rate
- Response times
- Error rates
- Cache hit ratio
- Database connection pool
- JVM metrics

---

## Troubleshooting

### Common Issues

#### 1. Database Connection Failed

```bash
# Check PostgreSQL is running
sudo systemctl status postgresql

# Test connection
psql -U substrait -h localhost -d substrait_compliance

# Check logs
docker-compose logs postgres
```

#### 2. JWT Token Invalid

```bash
# Verify JWT_SECRET is set
echo $JWT_SECRET

# Check token expiration
# Tokens expire after JWT_EXPIRATION milliseconds (default 24h)
```

#### 3. Rate Limit Exceeded

```bash
# Check rate limit configuration
curl http://localhost:8080/actuator/configprops | jq '.["rate-limit"]'

# Clear rate limit cache (requires admin access)
# Restart the application or wait for the refill window
```

#### 4. Flyway Migration Failed

```bash
# Check migration status
./gradlew flywayInfo

# Repair failed migration
./gradlew flywayRepair

# Baseline existing database
./gradlew flywayBaseline
```

#### 5. Out of Memory

```bash
# Increase JVM heap size
export JAVA_OPTS="-Xms1g -Xmx2g"

# Or in docker-compose.yml
environment:
  JAVA_OPTS: "-Xms1g -Xmx2g -XX:+UseG1GC"
```

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    io.substrait: DEBUG
    org.springframework.security: DEBUG
    org.springframework.web: DEBUG
```

### Performance Tuning

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
```

---

## Production Checklist

- [ ] Generate secure JWT secret (256+ bits)
- [ ] Configure strong database password
- [ ] Enable SSL/TLS
- [ ] Set up firewall rules
- [ ] Configure rate limiting
- [ ] Set up monitoring and alerting
- [ ] Configure log rotation
- [ ] Set up database backups
- [ ] Test disaster recovery
- [ ] Document API keys and access
- [ ] Configure CORS if needed
- [ ] Set up reverse proxy (Nginx/Apache)
- [ ] Enable health checks
- [ ] Configure resource limits
- [ ] Test under load
- [ ] Set up CI/CD pipeline

---

## Support

For issues and questions:
- GitHub Issues: https://github.com/substrait-io/substrait-compliance
- Documentation: https://substrait.io/compliance
- API Docs: http://localhost:8080/swagger-ui.html

---

*Last Updated: 2026-04-16*