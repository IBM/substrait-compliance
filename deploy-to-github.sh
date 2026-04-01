#!/bin/bash

# Deployment Script for Substrait Compliance Framework
# Pushes repository to GitHub Enterprise (https://github.ibm.com)

set -e  # Exit on error

echo "🚀 Substrait Compliance Framework - GitHub Enterprise Deployment"
echo "================================================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
GITHUB_ENTERPRISE_URL="github.ibm.com"
GITHUB_USER="rsinha"

# Function to print colored output
print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

# Step 1: Prompt for repository name
echo "Step 1: Repository Configuration"
echo "--------------------------------"
read -p "Enter repository name (default: substrait-compliance): " REPO_NAME
REPO_NAME=${REPO_NAME:-substrait-compliance}
print_info "Repository name: $REPO_NAME"
echo ""

# Step 2: Check if git is initialized
echo "Step 2: Checking Git Status"
echo "---------------------------"
if [ ! -d ".git" ]; then
    print_info "Initializing git repository..."
    git init
    print_success "Git repository initialized"
else
    print_success "Git repository already initialized"
fi
echo ""

# Step 3: Configure git user (if not set)
echo "Step 3: Git Configuration"
echo "------------------------"
if [ -z "$(git config user.name)" ]; then
    read -p "Enter your name: " GIT_NAME
    git config user.name "$GIT_NAME"
    print_success "Git user name set: $GIT_NAME"
else
    print_success "Git user name already set: $(git config user.name)"
fi

if [ -z "$(git config user.email)" ]; then
    read -p "Enter your email: " GIT_EMAIL
    git config user.email "$GIT_EMAIL"
    print_success "Git user email set: $GIT_EMAIL"
else
    print_success "Git user email already set: $(git config user.email)"
fi
echo ""

# Step 4: Add all files
echo "Step 4: Adding Files to Git"
echo "---------------------------"
git add .
print_success "All files added to staging"

# Show what will be committed
echo ""
print_info "Files to be committed:"
git status --short
echo ""

# Step 5: Create initial commit
echo "Step 5: Creating Initial Commit"
echo "-------------------------------"
if git rev-parse HEAD >/dev/null 2>&1; then
    print_info "Repository already has commits"
    read -p "Create a new commit? (y/n): " CREATE_COMMIT
    if [ "$CREATE_COMMIT" = "y" ]; then
        read -p "Enter commit message: " COMMIT_MSG
        git commit -m "$COMMIT_MSG"
        print_success "New commit created"
    fi
else
    git commit -m "Initial commit: Decentralized Substrait Compliance Framework

- Complete multi-language SDKs (Java, Python, Rust)
- TPC-H test suite with 22 queries
- Example implementations (DuckDB, DataFusion)
- Comprehensive CI/CD with GitHub Actions
- Compliance leaderboard system
- Full documentation and examples"
    print_success "Initial commit created"
fi
echo ""

# Step 6: Choose authentication method
echo "Step 6: Authentication Method"
echo "-----------------------------"
echo "Choose authentication method:"
echo "1) HTTPS (Personal Access Token)"
echo "2) SSH (Recommended)"
read -p "Enter choice (1 or 2): " AUTH_METHOD
echo ""

# Step 7: Add remote
echo "Step 7: Adding Remote Repository"
echo "--------------------------------"

if [ "$AUTH_METHOD" = "1" ]; then
    REMOTE_URL="https://${GITHUB_ENTERPRISE_URL}/${GITHUB_USER}/${REPO_NAME}.git"
    print_info "Using HTTPS authentication"
    print_info "You will need a Personal Access Token"
    print_info "Create one at: https://${GITHUB_ENTERPRISE_URL}/settings/tokens"
else
    REMOTE_URL="git@${GITHUB_ENTERPRISE_URL}:${GITHUB_USER}/${REPO_NAME}.git"
    print_info "Using SSH authentication"
    print_info "Make sure your SSH key is configured"
fi

# Check if remote already exists
if git remote | grep -q "^origin$"; then
    print_info "Remote 'origin' already exists"
    CURRENT_URL=$(git remote get-url origin)
    print_info "Current URL: $CURRENT_URL"
    read -p "Update remote URL? (y/n): " UPDATE_REMOTE
    if [ "$UPDATE_REMOTE" = "y" ]; then
        git remote set-url origin "$REMOTE_URL"
        print_success "Remote URL updated"
    fi
else
    git remote add origin "$REMOTE_URL"
    print_success "Remote 'origin' added"
fi

print_info "Remote URL: $REMOTE_URL"
echo ""

# Step 8: Push to GitHub Enterprise
echo "Step 8: Pushing to GitHub Enterprise"
echo "------------------------------------"
print_info "Pushing to: $REMOTE_URL"
echo ""

read -p "Ready to push? (y/n): " READY_TO_PUSH
if [ "$READY_TO_PUSH" = "y" ]; then
    if git push -u origin main; then
        print_success "Successfully pushed to GitHub Enterprise!"
        echo ""
        echo "🎉 Deployment Complete!"
        echo "======================"
        echo ""
        print_info "Repository URL: https://${GITHUB_ENTERPRISE_URL}/${GITHUB_USER}/${REPO_NAME}"
        echo ""
        echo "Next Steps:"
        echo "1. Visit your repository on GitHub Enterprise"
        echo "2. Enable GitHub Actions (Settings → Actions)"
        echo "3. Configure secrets for releases (Settings → Secrets)"
        echo "4. Enable GitHub Pages for leaderboard (Settings → Pages)"
        echo "5. Add collaborators (Settings → Collaborators)"
        echo ""
        print_success "See DEPLOYMENT_GUIDE.md for detailed post-deployment steps"
    else
        print_error "Push failed!"
        echo ""
        echo "Common issues:"
        echo "1. Repository doesn't exist on GitHub Enterprise"
        echo "   → Create it at: https://${GITHUB_ENTERPRISE_URL}/new"
        echo ""
        echo "2. Authentication failed"
        echo "   → For HTTPS: Use Personal Access Token as password"
        echo "   → For SSH: Check SSH key configuration"
        echo ""
        echo "3. Permission denied"
        echo "   → Verify you have write access to the repository"
        echo ""
        exit 1
    fi
else
    print_info "Push cancelled. You can push manually later with:"
    echo "   git push -u origin main"
fi

echo ""
print_success "Script completed!"

# Made with Bob
