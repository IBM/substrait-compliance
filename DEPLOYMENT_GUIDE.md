# Deployment Guide - Push to GitHub Enterprise

## Prerequisites

Before pushing to GitHub Enterprise (https://github.ibm.com), ensure you have:

1. ✅ GitHub Enterprise account access
2. ✅ SSH key configured or Personal Access Token
3. ✅ Repository created on GitHub Enterprise (or will be created)

---

## Step-by-Step Deployment

### Step 1: Create Repository on GitHub Enterprise

**Option A: Via Web UI**
1. Go to https://github.ibm.com/rsinha
2. Click "New repository"
3. Repository name: `substrait-compliance` (or your preferred name)
4. Description: "Decentralized Substrait Compliance Framework with Multi-Language SDKs"
5. Visibility: Choose Public or Private
6. **DO NOT** initialize with README, .gitignore, or license (we already have these)
7. Click "Create repository"

**Option B: Via GitHub CLI** (if available)
```bash
gh repo create rsinha/substrait-compliance \
  --description "Decentralized Substrait Compliance Framework" \
  --public  # or --private
```

### Step 2: Configure Git (if not already done)

```bash
cd /Users/rsinha/substrait-compliance-private

# Set your name and email
git config user.name "Your Name"
git config user.email "your.email@ibm.com"
```

### Step 3: Add All Files to Git

```bash
cd /Users/rsinha/substrait-compliance-private

# Add all files
git add .

# Check what will be committed
git status
```

### Step 4: Create Initial Commit

```bash
# Create initial commit
git commit -m "Initial commit: Decentralized Substrait Compliance Framework

- Complete multi-language SDKs (Java, Python, Rust)
- TPC-H test suite with 22 queries
- Example implementations (DuckDB, DataFusion)
- Comprehensive CI/CD with GitHub Actions
- Compliance leaderboard system
- Full documentation and examples"
```

### Step 5: Add Remote Repository

Replace `REPO_NAME` with your actual repository name:

```bash
# Add GitHub Enterprise remote
git remote add origin https://github.ibm.com/rsinha/REPO_NAME.git

# Verify remote
git remote -v
```

### Step 6: Push to GitHub Enterprise

```bash
# Push to main branch
git push -u origin main
```

**If you encounter authentication issues:**

**Option A: HTTPS with Personal Access Token**
```bash
# You'll be prompted for username and password
# Username: your GitHub Enterprise username
# Password: your Personal Access Token (not your password!)

# To create a token:
# 1. Go to https://github.ibm.com/settings/tokens
# 2. Click "Generate new token"
# 3. Select scopes: repo (all), workflow
# 4. Copy the token and use it as password
```

**Option B: SSH (Recommended)**
```bash
# Change remote to SSH
git remote set-url origin git@github.ibm.com:rsinha/REPO_NAME.git

# Push
git push -u origin main
```

---

## Automated Deployment Script

I've created a script to automate this process. Run:

```bash
cd /Users/rsinha/substrait-compliance-private
chmod +x deploy-to-github.sh
./deploy-to-github.sh
```

---

## Post-Deployment Steps

### 1. Enable GitHub Actions

1. Go to your repository on GitHub Enterprise
2. Click "Settings" → "Actions" → "General"
3. Enable "Allow all actions and reusable workflows"
4. Click "Save"

### 2. Configure Secrets (for Release Workflow)

Go to Settings → Secrets and variables → Actions → New repository secret

Add these secrets (optional, only needed for automated releases):
- `MAVEN_USERNAME` - Maven Central username
- `MAVEN_PASSWORD` - Maven Central password
- `SIGNING_KEY` - GPG signing key
- `SIGNING_PASSWORD` - GPG signing password
- `PYPI_API_TOKEN` - PyPI API token
- `CARGO_REGISTRY_TOKEN` - crates.io API token

### 3. Enable GitHub Pages (for Leaderboard)

1. Go to Settings → Pages
2. Source: "GitHub Actions"
3. Click "Save"

### 4. Protect Main Branch (Recommended)

1. Go to Settings → Branches
2. Add branch protection rule for `main`
3. Enable:
   - Require pull request reviews
   - Require status checks to pass
   - Require branches to be up to date

### 5. Add Topics/Tags

1. Go to repository main page
2. Click gear icon next to "About"
3. Add topics: `substrait`, `compliance`, `testing`, `query-engines`, `tpch`

---

## Verify Deployment

After pushing, verify:

1. ✅ All files are visible on GitHub Enterprise
2. ✅ README.md displays correctly
3. ✅ GitHub Actions workflows are visible in "Actions" tab
4. ✅ Directory structure is intact

---

## Troubleshooting

### Issue: Authentication Failed

**Solution:**
```bash
# Use Personal Access Token
# Create token at: https://github.ibm.com/settings/tokens
# Use token as password when prompted
```

### Issue: Large Files

**Solution:**
```bash
# Check file sizes
find . -type f -size +50M

# If you have large files, consider Git LFS
git lfs install
git lfs track "*.bin"
git lfs track "*.csv"
git add .gitattributes
git commit -m "Add Git LFS tracking"
```

### Issue: Permission Denied

**Solution:**
```bash
# Verify SSH key
ssh -T git@github.ibm.com

# Or use HTTPS with token
git remote set-url origin https://github.ibm.com/rsinha/REPO_NAME.git
```

---

## Repository Structure After Deployment

```
substrait-compliance/
├── .github/
│   └── workflows/
│       ├── sdk-build-test.yml
│       ├── release-publish.yml
│       ├── test-suite-validation.yml
│       ├── engine-compliance-template.yml
│       ├── compliance-leaderboard.yml
│       └── README.md
├── sdk/
│   ├── java/
│   ├── python/
│   └── rust/
├── test-suites/
│   └── tpch/
├── examples/
│   ├── duckdb-java/
│   └── datafusion-python/
├── scripts/
│   └── generate_leaderboard.py
├── docs/
├── README.md
├── CI_CD_IMPLEMENTATION.md
├── IMPLEMENTATION_SUMMARY.md
└── DEPLOYMENT_GUIDE.md (this file)
```

---

## Next Steps After Deployment

1. **Share with Team**
   - Add collaborators in Settings → Collaborators
   - Share repository URL

2. **Create First Release**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

3. **Monitor CI/CD**
   - Check Actions tab for workflow runs
   - Review any failures

4. **Update Documentation**
   - Add repository URL to README
   - Update links in documentation

5. **Announce to Community**
   - Share on Substrait mailing list
   - Post on relevant forums

---

## Support

For issues:
- Check GitHub Enterprise documentation
- Contact IBM GitHub Enterprise support
- Review Git documentation

---

Last Updated: 2026-04-01