# Deployment Guide

This guide explains how to contribute to, fork, or push changes to the
[IBM/substrait-compliance](https://github.com/IBM/substrait-compliance) repository on GitHub.

---

## Prerequisites

Before working with this repository, ensure you have:

1. A [GitHub account](https://github.com) with access to the IBM org (for direct contributors)
   or your own account (for forks)
2. Git installed locally (`git --version`)
3. SSH key configured **or** a Personal Access Token for HTTPS authentication

---

## Cloning the Repository

```bash
# SSH (recommended)
git clone git@github.com:IBM/substrait-compliance.git

# HTTPS
git clone https://github.com/IBM/substrait-compliance.git

cd substrait-compliance
```

---

## Setting Up Your Local Environment

```bash
# Configure your identity (if not already set globally)
git config user.name "Your Name"
git config user.email "your.email@example.com"
```

---

## Contributing Changes

All contributions follow the standard GitHub fork-and-pull-request workflow.
See [CONTRIBUTING.md](../CONTRIBUTING.md) for full guidelines.

### Step 1: Fork the Repository (external contributors)

1. Go to [https://github.com/IBM/substrait-compliance](https://github.com/IBM/substrait-compliance)
2. Click **Fork** (top-right)
3. Clone your fork locally:

```bash
git clone git@github.com:<your-username>/substrait-compliance.git
cd substrait-compliance
git remote add upstream git@github.com:IBM/substrait-compliance.git
```

### Step 2: Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
```

### Step 3: Make and Commit Changes

```bash
git add .
git commit -m "feat: describe your change"
```

### Step 4: Push and Open a Pull Request

```bash
# Push to your fork
git push origin feature/your-feature-name
```

Then open a pull request against `IBM/substrait-compliance:main` via the GitHub web UI.

---

## Authentication

### SSH (Recommended)

Generate a key if you don't have one:

```bash
ssh-keygen -t ed25519 -C "your.email@example.com"
```

Add `~/.ssh/id_ed25519.pub` to your GitHub account at
[https://github.com/settings/keys](https://github.com/settings/keys).

Verify:

```bash
ssh -T git@github.com
# Expected: Hi <username>! You've successfully authenticated...
```

### HTTPS with a Personal Access Token

1. Go to [https://github.com/settings/tokens](https://github.com/settings/tokens)
2. Click **Generate new token (classic)**
3. Select scopes: `repo` (all), `workflow`
4. Copy the token — use it as your password when git prompts for credentials

```bash
git remote set-url origin https://github.com/IBM/substrait-compliance.git
# git will prompt for username (your GitHub handle) and password (the token)
```

---

## Maintainer: Pushing Directly to the Repository

Direct pushes to `main` are protected. All changes must go through a pull request.

For maintainers cutting a release, [`release-publish.yml`](../.github/workflows/release-publish.yml)
builds every SDK, packages the test suites and creates the GitHub release. It runs in one of two ways:

- **By hand** (Actions → Release & Publish → Run workflow, with the version, e.g. `0.1.2`): builds
  and creates the release and its `v<version>` tag, without publishing to package registries.
- **By pushing a tag** (`git tag -a v0.1.2 -m "Release v0.1.2" && git push origin v0.1.2`): does
  the same and also publishes the SDKs to Maven Central, PyPI, crates.io, npm and NuGet, using the
  secrets below.

Before either, bump the SDK versions on `main` and date the release's CHANGELOG section.

---

## Post-Push Repository Configuration

After the initial push of a new repository the following settings should be applied
by an IBM org admin at [https://github.com/IBM/substrait-compliance/settings](https://github.com/IBM/substrait-compliance/settings):

### Branch Protection (main)

1. Settings → Branches → Add rule for `main`
2. Enable:
   - Require pull request reviews before merging
   - Require status checks to pass before merging
   - Require branches to be up to date before merging
   - Do not allow force pushes

### GitHub Actions

1. Settings → Actions → General
2. Select **Allow all actions and reusable workflows**
3. Click **Save**

### Repository Secrets (for publishing a release)

Settings → Secrets and variables → Actions → New repository secret. Only a tag-push release uses them.

| Secret | Purpose |
|--------|---------|
| `MAVEN_USERNAME` | Maven Central username |
| `MAVEN_PASSWORD` | Maven Central password |
| `SIGNING_KEY` | GPG signing key |
| `SIGNING_PASSWORD` | GPG signing password |
| `PYPI_API_TOKEN` | PyPI API token |
| `CARGO_REGISTRY_TOKEN` | crates.io API token |
| `NPM_TOKEN` | npm access token |
| `NUGET_API_KEY` | NuGet API key |

### GitHub Discussions

Settings → General → Features → Enable **Discussions**

Suggested initial categories: Announcements, General, Q&A, Show and Tell, Feature Requests.

### GitHub Pages (for Leaderboard)

1. Settings → Pages
2. Source: **GitHub Actions**
3. Click **Save**

### Repository Topics

On the repository main page, click the gear icon next to **About** and add:
`substrait`, `compliance`, `testing`, `query-engine`, `sql`

### Private Vulnerability Reporting

Settings → Security → Enable **Private vulnerability reporting**

---

## Troubleshooting

### Authentication Failed

Ensure you are using a Personal Access Token (not your GitHub password) for HTTPS,
or that your SSH public key is registered at
[https://github.com/settings/keys](https://github.com/settings/keys).

### Large Files

The repository does not use Git LFS: the runners read plans and data files straight from a
checkout. Keep new data files small (the TPC-H data is scale factor 0.01), and check before
committing:

```bash
find . -type f -size +10M -not -path './.git/*'
```

### Permission Denied (SSH)

```bash
# Check your key is loaded
ssh-add -l

# Retest
ssh -T git@github.com
```

---

## Repository Structure

```
substrait-compliance/
├── .github/
│   ├── workflows/         # CI/CD pipelines
│   └── ISSUE_TEMPLATE/    # Bug, feature, and doc templates
├── docs/                  # Documentation
├── examples/              # Engine implementation examples
├── schemas/               # JSON schemas for suite reports and submissions
├── scripts/               # Runners, scoring and utility scripts
├── submissions/           # Leaderboard results submitted by engine teams
├── sdk/                   # Multi-language SDKs (Java, Python, Rust, Go,
│                          #   TypeScript, C#, Scala, C++)
├── test-suites/           # Compliance test cases (TPC-H, functions)
├── CODE_OF_CONDUCT.md
├── CONTRIBUTING.md
├── GOVERNANCE.md
├── LICENSE
├── MAINTAINERS.md
├── NOTICE
├── README.md
└── SECURITY.md
```

---

## Support

- **Questions**: Open a [GitHub Discussion](https://github.com/IBM/substrait-compliance/discussions)
- **Bugs**: Open a [GitHub Issue](https://github.com/IBM/substrait-compliance/issues)
- **Security**: See [SECURITY.md](../SECURITY.md)
- **Contributing**: See [CONTRIBUTING.md](../CONTRIBUTING.md)

