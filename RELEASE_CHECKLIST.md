# Release Checklist

This document provides a comprehensive checklist for preparing and executing releases of the Substrait Compliance Framework.

## Initial Open Source Release (v1.0.0)

### Pre-Launch Checklist

- [x] **Documentation Baseline Prepared**
  - [x] README.md updated to describe the repository as pre-launch and in-progress where appropriate
  - [x] CHANGELOG.md prepared for the planned v1.0.0 release
  - [x] All governance files are present (CONTRIBUTING.md, GOVERNANCE.md, CODE_OF_CONDUCT.md, SECURITY.md)
  - [x] NOTICE file with proper attributions
  - [x] LICENSE file (Apache 2.0)
  - [ ] Replace remaining placeholders in MAINTAINERS.md before launch

- [x] **Security Baseline Prepared**
  - [x] Security scan report is present (SECURITY_SCAN_REPORT.md)
  - [x] Security disclosure process documented
  - [ ] Re-validate current dependency and secret-scanning status immediately before launch
  - [ ] Verify documented security controls against tested runtime behavior before launch

- [ ] **Implementation & Coverage Verification**
  - [x] Function test files and enhancement artifacts are present in the repository
  - [x] TPC-H assets are present in the repository
  - [ ] Confirm exact test-case counts and suite completeness for public claims
  - [ ] Confirm TPC-DS completion status before claiming 99-query support
  - [ ] Verify each SDK's supported feature set and maturity before public launch messaging
  - [ ] Verify all 8 SDKs through CI or documented manual release criteria

- [ ] **Repository Setup**
  - [ ] Transfer/mirror from github.ibm.com to github.com/substrait-io/substrait-compliance
  - [ ] Make repository public
  - [ ] Enable GitHub Discussions
  - [ ] Configure branch protection on main branch
  - [ ] Set up Dependabot for automated dependency updates
  - [ ] Add repository topics/tags for discoverability

- [ ] **Release Preparation**
  - [ ] Create v1.0.0 tag: `git tag -a v1.0.0 -m "Initial public release"`
  - [ ] Push tag to GitHub
  - [ ] Create GitHub Release with CHANGELOG.md content
  - [ ] Verify all CI/CD workflows pass

- [ ] **Community Setup**
  - [ ] Set up project email addresses (security@, conduct@)
  - [ ] Verify mailing list (substrait-dev@googlegroups.com)
  - [ ] Create initial GitHub Discussions categories
  - [ ] Prepare announcement for Substrait channels

- [ ] **Launch Announcement**
  - [ ] Post in GitHub Discussions
  - [ ] Email to substrait-dev@googlegroups.com
  - [ ] Post in Substrait Slack channels
  - [ ] Update Substrait website (if applicable)
  - [ ] Social media announcements (optional)

---

## Pre-Release Preparation (For Future Releases)

### 1. Version Planning

- [ ] Determine version number (major.minor.patch)
- [ ] Review [Semantic Versioning](https://semver.org/) guidelines
- [ ] Identify breaking changes (requires major version bump)
- [ ] Document new features (minor version bump)
- [ ] List bug fixes (patch version bump)

### 2. Code Freeze

- [ ] Announce code freeze to maintainers
- [ ] Create release branch: `release/vX.Y.Z`
- [ ] Update version numbers in all relevant files:
  - [ ] `sdk/java/build.gradle`
  - [ ] `sdk/python/setup.py`
  - [ ] `sdk/rust/Cargo.toml`
  - [ ] `sdk/go/go.mod`
  - [ ] `sdk/typescript/package.json`
  - [ ] `sdk/csharp/Substrait.Compliance.csproj`
  - [ ] `sdk/scala/build.sbt`
  - [ ] `api/build.gradle`

### 3. Documentation Updates

- [ ] Update CHANGELOG.md with release notes
- [ ] Review and update README.md
- [ ] Update version references in documentation
- [ ] Check all links are working
- [ ] Update API documentation
- [ ] Review SDK-specific README files

### 4. Testing

- [ ] Run full test suite for all SDKs:
  - [ ] Java: `./gradlew test`
  - [ ] Python: `pytest`
  - [ ] Rust: `cargo test`
  - [ ] Go: `go test ./...`
  - [ ] TypeScript: `npm test`
  - [ ] C++: `cmake --build build --target test`
  - [ ] C#: `dotnet test`
  - [ ] Scala: `sbt test`
- [ ] Run integration tests
- [ ] Test demo system
- [ ] Verify REST API functionality
- [ ] Check CI/CD workflows pass
- [ ] Perform manual smoke testing
- [ ] Archive hosted CI release evidence for this candidate (workflow run URL, artifact manifest, publication evidence artifacts)

### 5. Security Review

- [ ] Run security scanners (Trivy, Snyk)
- [ ] Review dependency vulnerabilities
- [ ] Check for exposed secrets
- [ ] Verify security headers in API
- [ ] Review authentication/authorization

### 5A. Operational Evidence

Record the following before calling any shared environment or release candidate operationally validated:

- [ ] Document deployment owner for the target environment
- [ ] Record rollback owner and rollback trigger
- [ ] Record deployed image tag and commit SHA
- [ ] Perform one backup rehearsal and record command, operator, and timestamp
- [ ] Perform one restore rehearsal into a disposable database and record outcome
- [ ] Perform one incident drill for secret exposure or failed deployment and record remediation steps
- [ ] Store links or paths to the resulting evidence with the release notes or release issue

### 6. Performance Testing

- [ ] Run benchmark suite
- [ ] Compare with previous release
- [ ] Document any performance changes
- [ ] Verify no regressions

## Release Candidate

### 7. Create Release Candidate

- [ ] Tag release candidate: `git tag -a v1.2.0-rc1 -m "Release candidate 1"`
- [ ] Push tag: `git push origin v1.2.0-rc1`
- [ ] Build all SDK packages
- [ ] Build container images
- [ ] Deploy to staging environment

### 8. Release Candidate Testing

- [ ] Community testing period (3-7 days)
- [ ] Announce RC in GitHub Discussions
- [ ] Monitor for issues
- [ ] Collect feedback
- [ ] Fix critical issues if found
- [ ] Create additional RCs if needed

## Final Release

### 9. Prepare Release

- [ ] Merge release branch to main
- [ ] Create release tag: `git tag -a v1.2.0 -m "Release v1.2.0"`
- [ ] Push tag: `git push origin v1.2.0`
- [ ] Build final packages
- [ ] Build and push container images

### 10. Publish Packages

- [ ] Publish Java SDK to Maven Central
- [ ] Publish Python SDK to PyPI
- [ ] Publish Rust SDK to crates.io
- [ ] Publish TypeScript SDK to npm
- [ ] Publish C# SDK to NuGet
- [ ] Publish container images to registries

### 11. Create GitHub Release

- [ ] Go to GitHub Releases page
- [ ] Create new release from tag
- [ ] Copy release notes from CHANGELOG.md
- [ ] Attach binary artifacts if applicable
- [ ] Mark as latest release
- [ ] Publish release

### 12. Update Documentation

- [ ] Update documentation website
- [ ] Update API documentation
- [ ] Update examples with new version
- [ ] Update migration guides if needed

## Post-Release

### 13. Announcements

- [ ] Post announcement in GitHub Discussions
- [ ] Send email to mailing list (substrait-dev@googlegroups.com)
- [ ] Post in Slack (#substrait-compliance)
- [ ] Update Substrait website
- [ ] Social media announcements (if applicable)
- [ ] Blog post (if major release)

### 14. Monitoring

- [ ] Monitor GitHub issues for release-related problems
- [ ] Watch CI/CD for failures
- [ ] Check download statistics
- [ ] Monitor community feedback
- [ ] Prepare hotfix if critical issues found

### 15. Cleanup

- [ ] Delete release branch (if applicable)
- [ ] Archive old release candidates
- [ ] Update project board/milestones
- [ ] Plan next release cycle

## Hotfix Release Process

For critical bug fixes between regular releases:

1. [ ] Create hotfix branch from release tag: `hotfix/vX.Y.Z+1`
2. [ ] Apply minimal fix
3. [ ] Update CHANGELOG.md
4. [ ] Bump patch version
5. [ ] Test thoroughly
6. [ ] Follow steps 9-14 above
7. [ ] Merge hotfix back to main

## Rollback Procedure

If critical issues are discovered after release:

1. [ ] Assess severity and impact
2. [ ] Communicate issue to community
3. [ ] Mark release as problematic in GitHub
4. [ ] Prepare hotfix or rollback
5. [ ] Follow hotfix process above
6. [ ] Post-mortem analysis

## Version Support Policy

This policy is **not active yet for a stable release**. Until the project publishes its first stable release and updates [`SECURITY.md`](SECURITY.md), support remains pre-release and best-effort only.

Planned stable-release policy to confirm before launch:

- [ ] Define the first stable supported-version window
- [ ] Confirm who owns security triage and maintenance for supported releases
- [ ] Confirm whether previous minor releases receive security-only support and for how long
- [ ] Update [`SECURITY.md`](SECURITY.md), [`README.md`](README.md), and release notes to match the final policy exactly

## Release Schedule

- **Regular Releases**: Every 6-8 weeks
- **Patch Releases**: As needed for critical bugs
- **Major Releases**: Annually or when breaking changes accumulate

## Release Blockers Requiring External Execution or Policy Decisions

The following items cannot be truthfully closed by repository edits alone and must be completed by maintainers/operators before calling the project open-source release ready:

- [ ] **Broader repository hygiene resolved**
  - Review the remaining tracked modifications outside the scoped OSS-readiness commits
  - Revert, split, or intentionally land those changes before the release cut
- [ ] **Operator evidence generated in a real environment**
  - Produce backup, restore, rollback, smoke-test, and incident-drill records
  - Store durable evidence with the release issue, release notes, or an agreed evidence location
- [ ] **Stable support policy established**
  - Publish a real supported-version window and maintainer response commitment
  - Update [`SECURITY.md`](SECURITY.md) and related docs to reflect the actual support promise
- [ ] **Release-candidate process executed**
  - Tag and publish an RC
  - Deploy to staging
  - Run community testing, security scanning, and integration validation
  - Promote only after RC evidence is reviewed and accepted

## Contact

For questions about the release process:
- Open a discussion in GitHub Discussions
- Contact maintainers via email
- Ask in #substrait-compliance Slack channel

---

**Last Updated**: May 30, 2026