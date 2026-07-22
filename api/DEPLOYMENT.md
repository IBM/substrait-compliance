# Substrait Compliance API Deployment Guide

This guide documents the currently supported **pre-release** deployment and operator validation flow for the Substrait Compliance API.

It is intentionally limited to procedures that can be executed from the repository as it exists today. It does **not** claim a hosted service, managed SRE coverage, or a production-certified deployment program.

## Deployment Scope

This guide is for:

- local development
- contributor validation
- containerized evaluation
- staging-style operator rehearsal

This guide does **not** currently certify:

- a managed public API service
- a 24x7 operated production environment
- a guaranteed HA deployment topology
- a stable long-term support contract

## Supported Deployment Paths

The repository currently supports these deployment paths:

1. **Local Gradle boot run**
2. **Containerized local/staging deployment with [`api/docker-compose.yml`](api/docker-compose.yml)**
3. **Single-container manual deployment using [`api/Containerfile`](api/Containerfile)**

Kubernetes, systemd, and cloud-specific deployment patterns may be possible for downstream operators, but they are not treated as verified repository runbooks in this guide.

## Prerequisites

### Required

- Java 17 or newer for local execution
- Podman or Docker for container execution
- PostgreSQL 15 or newer
- OpenSSL for secret generation
- curl for health and endpoint checks

### Recommended

- 4 GB RAM available for local containerized evaluation
- a clean shell session for exporting secrets
- a disposable local or staging PostgreSQL instance

## Required Configuration

The API requires externally supplied secrets and database configuration.

### Required Environment Variables

| Variable | Purpose |
|----------|---------|
| `JWT_SECRET` | JWT signing secret; generate outside source control |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password |

### Optional Environment Variables

| Variable | Purpose | Default |
|----------|---------|---------|
| `JWT_EXPIRATION` | Token expiration in milliseconds | `86400000` |
| `RATE_LIMIT_DEFAULT_LIMIT` | Default request limit per window | `1000` |
| `RATE_LIMIT_REFILL_DURATION_MINUTES` | Rate-limit refill window | `60` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `prod` |

## Secret Generation

Generate secrets outside the repository and never commit them.

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
export DB_PASSWORD="$(openssl rand -base64 24)"
```

Minimum operator expectation:

- use unique secrets per environment
- do not reuse local secrets in shared environments
- rotate secrets if they are exposed in logs, shell history, or screenshots

## Deployment Option 1: Local Development Run

Use this path when validating code changes or reproducing API behavior locally.

### Step 1: Start PostgreSQL

Example using Podman:

```bash
podman run -d \
  --name substrait-api-postgres \
  -e POSTGRES_DB=substrait_compliance \
  -e POSTGRES_USER=substrait \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -p 5432:5432 \
  postgres:15-alpine
```

### Step 2: Export application configuration

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/substrait_compliance"
export SPRING_DATASOURCE_USERNAME="substrait"
export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
export JWT_SECRET="$JWT_SECRET"
```

### Step 3: Start the API

```bash
cd api
./gradlew bootRun
```

### Step 4: Verify startup

In a second terminal:

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/swagger-ui.html > /dev/null
```

Expected result:

- health endpoints return success
- Swagger UI is reachable
- Flyway migrations complete during startup
- no secret values appear in logs

## Deployment Option 2: Containerized Evaluation with Compose

Use this path for reproducible local or staging-style evaluation.

### Step 1: Prepare environment file

```bash
cd api
cp .env.example .env
```

Append generated secrets:

```bash
printf '\nJWT_SECRET=%s\n' "$JWT_SECRET" >> .env
printf 'DB_PASSWORD=%s\n' "$DB_PASSWORD" >> .env
```

Review `.env` before startup and confirm it is not tracked by git.

### Step 2: Start services

```bash
docker-compose up -d
```

If using Podman Compose:

```bash
podman-compose up -d
```

### Step 3: Verify containers

```bash
docker-compose ps
docker-compose logs --tail=200 api
docker-compose logs --tail=200 postgres
```

### Step 4: Verify API behavior

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/actuator/prometheus > /dev/null
curl -fsS http://localhost:8080/swagger-ui.html > /dev/null
```

### Step 5: Shut down cleanly

```bash
docker-compose down
```

Add `-v` only if you intentionally want to remove database volumes.

## Deployment Option 3: Manual Container Run

Use this path when validating the built container image directly. The container build in [`api/Containerfile`](api/Containerfile) uses JDK 17 for the builder image and Java 17 for the runtime image to match the application's class version requirements.

### Step 1: Build the image

From the repository root:

```bash
podman build -t substrait-compliance-api:local -f api/Containerfile .
```

Docker equivalent:

```bash
docker build -t substrait-compliance-api:local -f api/Containerfile .
```

### Step 2: Create a network

```bash
podman network create substrait-network
```

### Step 3: Start PostgreSQL

```bash
podman run -d \
  --name substrait-db \
  --network substrait-network \
  -e POSTGRES_DB=substrait_compliance \
  -e POSTGRES_USER=substrait \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  postgres:15-alpine
```

### Step 4: Start the API container

```bash
podman run -d \
  --name substrait-api \
  --network substrait-network \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://substrait-db:5432/substrait_compliance \
  -e SPRING_DATASOURCE_USERNAME=substrait \
  -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  -e JWT_SECRET="$JWT_SECRET" \
  substrait-compliance-api:local
```

### Step 5: Verify runtime behavior

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/prometheus > /dev/null
podman logs substrait-api --tail 200
```

## Operator Evidence Record

Before describing any shared environment as validated, record the following evidence outside ephemeral terminal output:

- deployment owner
- rollback owner
- deployed image tag
- deployed commit SHA
- date and operator for the validation run
- backup rehearsal result
- restore rehearsal result
- smoke-test result
- incident-drill result if one was performed for the release candidate

A minimal evidence record can be captured in Markdown like this:

```md
# Deployment Evidence

- Environment: staging
- Deployment owner: <name>
- Rollback owner: <name>
- Image tag: ghcr.io/ORG/REPO/substrait-compliance-api:<tag>
- Commit SHA: <sha>
- Validation date: YYYY-MM-DD
- Backup rehearsal: passed/failed
- Restore rehearsal: passed/failed
- Smoke test: passed/failed
- Incident drill: passed/failed/not-run
- Notes: <links to logs, tickets, or workflow runs>
```

Store this evidence with the release issue, release notes, or operator ticketing system used for the environment.

## Database Validation

The repository assumes PostgreSQL-backed execution.

### Create database manually if needed

```bash
psql -U postgres <<'SQL'
CREATE DATABASE substrait_compliance;
CREATE USER substrait WITH ENCRYPTED PASSWORD 'replace-me';
GRANT ALL PRIVILEGES ON DATABASE substrait_compliance TO substrait;
SQL
```

### Validate Flyway migrations

From the [`api`](../api) directory:

```bash
./gradlew flywayInfo
```

If you need explicit connection properties:

```bash
./gradlew flywayInfo \
  -Pflyway.url=jdbc:postgresql://localhost:5432/substrait_compliance \
  -Pflyway.user=substrait \
  -Pflyway.password="$DB_PASSWORD"
```

Operator expectation:

- migrations must complete without manual schema edits
- migration failures block deployment promotion
- repair or baseline commands should only be used with documented operator approval

## Reverse Proxy and TLS Expectations

The repository does not provide certificates or a managed ingress stack.

For any shared or internet-reachable deployment, operators should:

- terminate TLS in front of the API
- forward `X-Forwarded-*` headers correctly
- restrict direct access to the backend port where possible
- verify health and Swagger endpoints through the same ingress path users will hit

Example validation commands after proxy setup:

```bash
curl -fsS https://your-api-host.example/actuator/health
curl -fsS https://your-api-host.example/swagger-ui.html > /dev/null
```

## Monitoring and Health Validation

The repository exposes health and metrics endpoints that operators can validate directly.

### Health checks

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
```

### Metrics

```bash
curl -fsS http://localhost:8080/actuator/prometheus > /dev/null
curl -fsS http://localhost:8080/actuator/metrics > /dev/null
```

Minimum operator validation before calling an environment “ready”:

- liveness succeeds after startup
- readiness succeeds after migrations complete
- metrics endpoint is reachable from the intended scraper path
- logs can be collected without exposing secrets

## Operator Validation Runbook

Use this checklist for every environment rehearsal.

### Pre-start checks

- [ ] Generate fresh environment secrets
- [ ] Confirm PostgreSQL is reachable
- [ ] Confirm target port availability
- [ ] Confirm `.env` or exported shell variables are not committed
- [ ] Confirm the deployment path matches one of the supported paths in this guide

### Startup checks

- [ ] Start PostgreSQL
- [ ] Start API process or container
- [ ] Confirm Flyway migrations complete
- [ ] Confirm no immediate crash loop or restart loop
- [ ] Confirm health endpoints return success

### Post-start checks

- [ ] Confirm Swagger UI is reachable
- [ ] Confirm Prometheus endpoint is reachable
- [ ] Confirm logs are readable and do not expose secrets
- [ ] Confirm reverse proxy or ingress path works if used
- [ ] Record image tag or commit SHA used for the deployment

### Promotion gate

Do not describe an environment as production-ready unless you have also documented:

- operator owner
- escalation path
- secret rotation procedure
- backup and restore procedure
- rollback procedure

## Rollback Runbook

If a deployment fails validation:

1. stop routing traffic to the failing instance if applicable
2. capture logs from the failing API process and PostgreSQL
3. record the image tag, commit SHA, and migration state
4. redeploy the last known good image or restart the last known good local artifact
5. rerun health and metrics checks
6. document the failure before attempting another promotion

Example container rollback pattern:

```bash
podman stop substrait-api
podman rm substrait-api
podman run -d \
  --name substrait-api \
  --network substrait-network \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://substrait-db:5432/substrait_compliance \
  -e SPRING_DATASOURCE_USERNAME=substrait \
  -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  -e JWT_SECRET="$JWT_SECRET" \
  substrait-compliance-api:<last-known-good-tag>
```

## Backup and Restore Expectations

The repository does not automate managed backups. Operators must own this procedure.

### Backup example

```bash
pg_dump -U substrait -h localhost substrait_compliance > substrait_compliance_backup.sql
```

### Restore example

```bash
psql -U substrait -h localhost substrait_compliance < substrait_compliance_backup.sql
```

Minimum expectation before external use:

- perform at least one successful backup
- perform at least one restore rehearsal into a disposable database
- document who owns backup retention and restore approval

## Troubleshooting

### Database connection failure

```bash
psql -U substrait -h localhost -d substrait_compliance
docker-compose logs postgres
```

Check:

- hostname and port
- username/password
- PostgreSQL container or service status

### Migration failure

```bash
cd api
./gradlew flywayInfo
```

Check:

- whether the target schema was modified manually
- whether the database user has required privileges
- whether the migration history table is consistent

### API fails health checks

```bash
docker-compose logs api
curl -v http://localhost:8080/actuator/health
```

Check:

- startup exceptions
- datasource configuration
- port conflicts
- readiness delay during migration

### Metrics endpoint unavailable

```bash
curl -v http://localhost:8080/actuator/prometheus
```

Check:

- actuator exposure configuration
- reverse proxy path handling
- local firewall or container port mapping

## Current Limitations

This deployment guide intentionally does not claim:

- verified Kubernetes manifests
- verified systemd packaging
- managed certificate lifecycle
- automated secret rotation
- staffed incident response
- hosted SLA or uptime guarantees

## Related Documents

- [`api/README.md`](README.md)
- [`api/API_USAGE.md`](API_USAGE.md)
- [`SECURITY.md`](../SECURITY.md)
- [`docs/SECURITY_HARDENING_GUIDE.md`](../docs/SECURITY_HARDENING_GUIDE.md)

---

**Last Updated**: May 30, 2026