# GitHub Actions Workflows

This directory contains CI/CD workflows for the Substrait Compliance Framework.

## Workflows

### API Workflows

#### 1. PR Validation (`api-pr-validation.yml`)
**Trigger**: Pull requests to main/develop  
**Purpose**: Validate code changes before merge

**Steps**:
- Checkout code
- Setup Java 11
- Run tests
- Generate coverage report
- Upload to Codecov
- Comment results on PR

**Required Secrets**: None (uses GITHUB_TOKEN)

#### 2. Build and Test (`api-build-test.yml`)
**Trigger**: Push to any branch  
**Purpose**: Continuous validation

**Steps**:
- Build JAR file
- Run all tests
- Generate test reports
- Archive artifacts
- Publish test results

**Required Secrets**: None

#### 3. Container Build (`api-container-build.yml`)
**Trigger**: Push to main/develop, manual  
**Purpose**: Build and publish container images

**Steps**:
- Build multi-platform images (amd64, arm64)
- Push to GitHub Container Registry
- Run Trivy security scan
- Generate SBOM
- Upload security results

**Required Secrets**:
- `GITHUB_TOKEN` (automatic)

**Permissions**:
- `contents: read`
- `packages: write`
- `security-events: write`

#### 4. Deploy to Staging (`api-deploy-staging.yml`)
**Trigger**: After successful container build on develop  
**Purpose**: Auto-deploy to staging environment

**Steps**:
- Configure kubectl
- Update deployment
- Wait for rollout
- Run smoke tests
- Notify on completion

**Required Secrets**:
- `KUBE_CONFIG_STAGING` - Base64-encoded kubeconfig

**Environment**: staging

#### 5. Deploy to Production (`api-deploy-production.yml`)
**Trigger**: Manual with version input  
**Purpose**: Deploy to production with approval

**Steps**:
- Create backup
- Update deployment
- Wait for rollout
- Run comprehensive smoke tests
- Rollback on failure
- Create deployment record

**Required Secrets**:
- `KUBE_CONFIG_PROD` - Base64-encoded kubeconfig

**Environment**: production (requires approval)

#### 6. Release (`api-release.yml`)
**Trigger**: Tag push (v*.*.*), manual  
**Purpose**: Create GitHub releases

**Steps**:
- Build release artifacts
- Generate changelog
- Create GitHub release
- Build and push release container
- Upload JAR to release

**Required Secrets**: None (uses GITHUB_TOKEN)

## Setup Instructions

### 1. Configure Secrets

Navigate to: `Settings > Secrets and variables > Actions`

**Repository Secrets**:
```bash
# Kubernetes configs (base64-encoded)
KUBE_CONFIG_STAGING=<base64-encoded-kubeconfig>
KUBE_CONFIG_PROD=<base64-encoded-kubeconfig>

# Optional: For enhanced features
CODECOV_TOKEN=<codecov-token>
SLACK_WEBHOOK_URL=<slack-webhook-url>
```

**Encoding kubeconfig**:
```bash
cat ~/.kube/config | base64 -w 0
```

### 2. Configure Environments

Navigate to: `Settings > Environments`

**Staging Environment**:
- Name: `staging`
- URL: `https://api-staging.substrait.io`
- Protection rules: None (auto-deploy)

**Production Environment**:
- Name: `production`
- URL: `https://api.substrait.io`
- Protection rules:
  - Required reviewers: 2
  - Wait timer: 0 minutes
  - Deployment branches: main only

### 3. Enable GitHub Container Registry

Navigate to: `Settings > Packages`

- Enable "Inherit access from source repository"
- Set visibility to Public or Private

### 4. Configure Branch Protection

Navigate to: `Settings > Branches`

**Main Branch**:
- Require pull request reviews (2 approvals)
- Require status checks to pass:
  - `validate` (from api-pr-validation)
  - `build` (from api-build-test)
- Require branches to be up to date
- Require conversation resolution
- Do not allow bypassing

**Develop Branch**:
- Require pull request reviews (1 approval)
- Require status checks to pass
- Allow force pushes (for rebasing)

## Usage Examples

### Running Workflows Manually

#### Deploy to Staging
```bash
gh workflow run api-deploy-staging.yml
```

#### Deploy to Production
```bash
gh workflow run api-deploy-production.yml -f version=v1.0.0
```

#### Create Release
```bash
# Tag and push
git tag v1.0.0
git push origin v1.0.0

# Or trigger manually
gh workflow run api-release.yml -f version=v1.0.0
```

### Monitoring Workflows

```bash
# List workflow runs
gh run list --workflow=api-build-test.yml

# View specific run
gh run view <run-id>

# Watch run in real-time
gh run watch <run-id>

# Download artifacts
gh run download <run-id>
```

## Workflow Dependencies

```
PR Validation
    ↓
Build & Test
    ↓
Container Build
    ↓
Deploy Staging (auto on develop)
    ↓
Deploy Production (manual approval)
```

## Troubleshooting

### Workflow Fails on Test Step

**Check**:
1. Test logs in Actions tab
2. Local test execution: `cd api && ./gradlew test`
3. TestContainers Docker access

**Fix**:
- Ensure tests pass locally
- Check for flaky tests
- Verify TestContainers configuration

### Container Build Fails

**Check**:
1. Containerfile syntax
2. Build context includes all files
3. Multi-platform build support

**Fix**:
```bash
# Test locally
podman build -t test -f api/Containerfile .

# Check buildx
docker buildx ls
```

### Deployment Fails

**Check**:
1. Kubernetes cluster connectivity
2. Deployment manifests
3. Image pull permissions

**Fix**:
```bash
# Test kubectl access
kubectl cluster-info
kubectl get pods -n substrait-staging

# Verify image exists
podman pull ghcr.io/org/repo/substrait-compliance-api:develop
```

### Secrets Not Working

**Check**:
1. Secret names match exactly
2. Secrets are set in correct scope (repo/environment)
3. Base64 encoding is correct

**Fix**:
```bash
# Re-encode kubeconfig
cat ~/.kube/config | base64 -w 0 > kubeconfig.b64

# Set secret
gh secret set KUBE_CONFIG_STAGING < kubeconfig.b64
```

## Best Practices

### 1. Keep Workflows DRY
- Use reusable workflows for common tasks
- Extract repeated steps into composite actions
- Use workflow templates

### 2. Secure Secrets
- Never commit secrets to repository
- Use environment-specific secrets
- Rotate secrets regularly
- Use least-privilege access

### 3. Optimize Performance
- Cache dependencies (Gradle, Docker layers)
- Run jobs in parallel when possible
- Use conditional execution
- Limit artifact retention

### 4. Monitor and Alert
- Set up status badges
- Configure Slack/email notifications
- Monitor workflow execution times
- Track failure rates

### 5. Document Changes
- Update this README when adding workflows
- Document required secrets
- Explain workflow triggers
- Provide troubleshooting steps

## Status Badges

Add to your README.md:

```markdown
![API Build](https://github.com/org/repo/actions/workflows/api-build-test.yml/badge.svg)
![Container Build](https://github.com/org/repo/actions/workflows/api-container-build.yml/badge.svg)
![Staging](https://github.com/org/repo/actions/workflows/api-deploy-staging.yml/badge.svg)
```

## Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Workflow Syntax](https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions)
- [GitHub CLI](https://cli.github.com/manual/gh_workflow)
- [Kubernetes Deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)

---

**Last Updated**: 2026-04-16  
**Maintained By**: DevOps Team