# Security Hardening Guide

This guide documents the security controls, operator checks, and incident-handling expectations that can be supported by the repository **today**.

It is written for the current **pre-release** state of the Substrait Compliance API and related deployment artifacts. It does not claim a managed security operations program, a hosted production service, or a 24x7 response organization.

## Scope

This guide applies to:

- the API module in [`api`](api)
- containerized deployment using [`api/Containerfile`](api/Containerfile)
- local or staging-style deployment using [`api/docker-compose.yml`](api/docker-compose.yml)
- repository-level validation and release workflows that materially affect shipped artifacts

## Current Security Posture

The repository contains meaningful security-related implementation and validation, but operators should distinguish between:

- **verified repository behavior**
- **operator-owned deployment controls**
- **future hardening work not yet claimed as guaranteed**

For the authoritative support boundary, see [`SECURITY.md`](SECURITY.md).

## Verified Repository Controls

### 1. Non-root container execution

The API container image is built to run as a non-root user.

Operator check:

```bash
podman inspect substrait-compliance-api:local --format '{{.Config.User}}'
```

Expected result:

- the configured runtime user is not `root`

### 2. Externalized JWT secret configuration

The API expects JWT secrets from environment configuration rather than source-controlled literals.

Operator checks:

```bash
grep -n "JWT_SECRET" api/docker-compose.yml
grep -n "JWT_SECRET" api/src/main/resources/application.yml
```

Expected result:

- JWT configuration is sourced from environment variables
- no deployment secret is committed into repository configuration

### 3. Health and metrics endpoints for deployment validation

The API exposes health and metrics endpoints that operators can use to validate startup and runtime behavior.

Operator checks:

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/actuator/prometheus > /dev/null
```

### 4. Workflow-based artifact validation

Repository workflows validate build and packaging behavior for claimed SDK and release artifacts.

Relevant workflow files:

- [`.github/workflows/sdk-build-test.yml`](.github/workflows/sdk-build-test.yml)
- [`.github/workflows/release-publish.yml`](.github/workflows/release-publish.yml)

These workflows improve confidence in shipped artifacts, but they do not by themselves create a staffed security operations program.

### 5. Canonical webhook signing path

Webhook payload signing uses canonical JSON serialization before HMAC generation.

Relevant implementation:

- [`api/src/main/java/io/substrait/compliance/api/service/WebhookDeliveryService.java`](api/src/main/java/io/substrait/compliance/api/service/WebhookDeliveryService.java)

## Operator-Owned Controls

The following controls remain deployment responsibilities and must be validated by the operator in each environment:

- TLS certificate provisioning and renewal
- reverse proxy or ingress configuration
- network exposure and firewall policy
- centralized logging and retention
- alert routing and escalation ownership
- backup retention and restore approval
- secret rotation cadence
- access review and credential lifecycle management

## Hardening Checklist

Use this checklist before describing any environment as externally usable.

### Secrets and configuration

- [ ] Generate a unique `JWT_SECRET` outside source control
- [ ] Generate a unique database password outside source control
- [ ] Confirm secrets are not committed to `.env`, screenshots, docs, or logs
- [ ] Confirm environment-specific secrets are not reused across environments

### Runtime and network

- [ ] Run the API behind TLS for any shared environment
- [ ] Restrict direct backend port exposure where possible
- [ ] Confirm only intended ports are reachable
- [ ] Confirm reverse proxy forwards `X-Forwarded-*` headers correctly

### Startup validation

- [ ] Confirm Flyway migrations complete successfully
- [ ] Confirm liveness and readiness endpoints return success
- [ ] Confirm Swagger UI is reachable only through the intended ingress path
- [ ] Confirm metrics endpoint is reachable from the intended scraper path

### Logging and observability

- [ ] Confirm logs can be collected centrally if required by your environment
- [ ] Confirm logs do not expose secrets
- [ ] Confirm repeated failures can be identified from logs or metrics
- [ ] Confirm operator ownership for alert review is documented

### Recovery readiness

- [ ] Perform at least one successful database backup
- [ ] Perform at least one restore rehearsal into a disposable database
- [ ] Document rollback owner and rollback trigger
- [ ] Record the deployed image tag or commit SHA

## Tested Operator Procedures

The following procedures are grounded in repository-supported deployment paths.

## Procedure 1: Secure local or staging startup

### Step 1: Generate secrets

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
export DB_PASSWORD="$(openssl rand -base64 24)"
```

### Step 2: Start the supported deployment path

Use one of the procedures in [`api/DEPLOYMENT.md`](api/DEPLOYMENT.md).

### Step 3: Validate startup

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/actuator/prometheus > /dev/null
```

### Step 4: Review logs

Examples:

```bash
docker-compose logs --tail=200 api
podman logs substrait-api --tail 200
```

Validation goal:

- no crash loop
- no migration failure
- no obvious secret leakage in logs

## Procedure 2: Secret exposure response

Use this procedure if a JWT secret, database password, or similar credential is exposed in logs, screenshots, shell history, or committed files.

1. stop sharing the affected environment externally if applicable
2. generate replacement secrets immediately
3. restart the API with the new secrets
4. invalidate or replace any dependent credentials if applicable
5. remove exposed values from tracked files or documentation
6. document the exposure and remediation in the issue or advisory path appropriate to the environment

Replacement example:

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
export DB_PASSWORD="$(openssl rand -base64 24)"
```

Then restart the deployment using the updated values.

## Procedure 3: Failed deployment triage

Use this when a deployment starts but fails health or readiness checks.

### Collect evidence

```bash
curl -v http://localhost:8080/actuator/health
docker-compose logs --tail=200 api
docker-compose logs --tail=200 postgres
```

### Check likely causes

- invalid datasource URL
- wrong database credentials
- Flyway migration failure
- port conflict
- missing `JWT_SECRET`

### Recovery action

- stop the failing deployment
- restore the last known good image or artifact
- rerun health checks
- record the failing image tag or commit SHA for follow-up

## Procedure 4: Backup and restore rehearsal

The repository does not automate managed backups, so operators must prove this path themselves.

### Backup

```bash
pg_dump -U substrait -h localhost substrait_compliance > substrait_compliance_backup.sql
```

### Restore into a disposable database

```bash
createdb substrait_compliance_restore_test
psql -U substrait -h localhost substrait_compliance_restore_test < substrait_compliance_backup.sql
```

Validation goal:

- backup completes successfully
- restore completes successfully
- restored database is usable for inspection or startup rehearsal

## Procedure 5: Dependency and release validation expectations

Before publishing or promoting a release candidate:

- ensure repository validation workflows are green
- treat release artifact smoke-test failures as blockers
- do not claim package publication success unless the relevant workflow completed successfully in hosted CI

This repository can define and harden workflows locally, but hosted publication proof still depends on actual GitHub Actions execution and registry credentials.

## Incident Handling Model

Current incident handling is maintainer-driven and best effort.

### Current ownership

- security triage owner: maintainer of record listed in [`MAINTAINERS.md`](MAINTAINERS.md)
- escalation path: maintainer review through the channels described in [`SECURITY.md`](SECURITY.md)

### Current limitations

This repository does **not** currently claim:

- a separate security team
- a 24x7 on-call rotation
- pager-backed incident response
- guaranteed response SLAs for all time zones

## Logging Guidance

Operators should ensure that logs are useful without leaking secrets.

Recommended checks:

- do not print raw JWT secrets
- do not print database passwords
- avoid copying full authorization headers into logs
- capture enough context to diagnose startup, migration, and webhook failures

Example review commands:

```bash
docker-compose logs api | tail -n 200
podman logs substrait-api --tail 200
```

## TLS and Ingress Guidance

For any shared environment:

- terminate TLS before exposing the API to users
- validate the health endpoint through the same ingress path users will use
- validate Swagger UI through the same ingress path users will use
- restrict direct backend access where possible

Example checks:

```bash
curl -fsS https://your-api-host.example/actuator/health
curl -fsS https://your-api-host.example/swagger-ui.html > /dev/null
```

## What This Guide Does Not Claim

This guide does not claim that the repository currently provides:

- production certification
- managed WAF coverage
- automated secret rotation
- verified Kubernetes hardening manifests
- verified multi-region failover
- staffed SOC or incident command structure

## Related Documents

- [`SECURITY.md`](SECURITY.md)
- [`api/README.md`](api/README.md)
- [`api/DEPLOYMENT.md`](api/DEPLOYMENT.md)
- [`api/API_USAGE.md`](api/API_USAGE.md)
- [`MAINTAINERS.md`](MAINTAINERS.md)

---

**Last Updated**: May 30, 2026  
**Status**: Pre-release hardening guide for operator validation