# Release Checklist

This document provides a comprehensive checklist for preparing and executing releases of the Substrait Compliance Framework.

## Initial Open Source Release (v1.0.0)

### Pre-Launch Checklist

- [x] **Documentation Complete**
  - [x] README.md updated with accurate statistics (8 SDKs, 4,000+ test cases, 99 TPC-DS queries)
  - [x] CHANGELOG.md prepared with v1.0.0 release notes
  - [x] All governance files in place (CONTRIBUTING.md, GOVERNANCE.md, CODE_OF_CONDUCT.md, SECURITY.md)
  - [x] MAINTAINERS.md populated with Project Lead (Ranjan Sinha)
  - [x] NOTICE file with proper attributions
  - [x] LICENSE file (Apache 2.0)

- [x] **Security Validated**
  - [x] Security scan completed (SECURITY_SCAN_REPORT.md)
  - [x] 0 vulnerabilities found across all SDKs
  - [x] No hardcoded secrets detected
  - [x] Security disclosure process documented

- [x] **Test Coverage Verified**
  - [x] 279 function test files (143 standard + 136 enhanced)
  - [x] ~4,000+ individual test cases
  - [x] TPC-H: 22 queries complete
  - [x] TPC-DS: 99 queries complete
  - [x] All 8 SDKs verified (Java, Python, Rust, Go, C++, TypeScript, C#, Scala)

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

### 5. Security Review

- [ ] Run security scanners (Trivy, Snyk)
- [ ] Review dependency vulnerabilities
- [ ] Check for exposed secrets
- [ ] Verify security headers in API
- [ ] Review authentication/authorization

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

- **Current Release**: Full support with bug fixes and security updates
- **Previous Minor Release**: Security updates only for 6 months
- **Older Releases**: Community support only

## Release Schedule

- **Regular Releases**: Every 6-8 weeks
- **Patch Releases**: As needed for critical bugs
- **Major Releases**: Annually or when breaking changes accumulate

## Contact

For questions about the release process:
- Open a discussion in GitHub Discussions
- Contact maintainers via email
- Ask in #substrait-compliance Slack channel

---

**Last Updated**: May 30, 2026