# Open Source Release Plan — Substrait Compliance Framework

**Version:** v1.0.0  
**Last Updated:** July 2026  
**Owner:** Ranjan Sinha ([@RanjanSinha-GH](https://github.com/RanjanSinha-GH))  
**Status:** 🟡 Pre-Release — Code-complete; external execution steps remain

---

## Current Repository: `github.ibm.com/rsinha/substrait-compliance`  
## Target Public Repository: `github.com/IBM/substrait-compliance`

---

## Executive Summary

The codebase is **feature-complete and documentation-complete** for a v1.0.0 release. All SDKs, test suites, governance documents, and CI/CD workflows are in place. What remains is a set of **external execution steps and policy decisions** that cannot be resolved through code commits alone.

| Area | Status |
|------|--------|
| Legal & Licensing | ✅ Complete |
| Governance Documentation | ✅ Complete |
| SDK Implementation (8 languages) | ✅ Complete |
| Test Suites (TPC-H, TPC-DS, Functions) | ✅ Complete |
| Security Baseline | ✅ Complete (re-scan required before launch) |
| CI/CD Workflows | ✅ Complete |
| Interactive Demo | ✅ Complete |
| REST API (reference implementation) | ✅ Complete (hosted-service decision pending) |
| Documentation Consistency | 🟡 Mostly done — SDK READMEs still use `substrait-io` URLs |
| Repository Transfer (IBM → public) | ❌ Not started |
| Community Infrastructure | ❌ Not started |
| Release Candidate Testing | ❌ Not started |

**Estimated time to launch-ready:** 2–3 weeks with focused effort.

---

## What Is Complete

### ✅ Code & Content

- **8 SDK implementations** — Java, Python, Rust, Go, C++, TypeScript, C#, Scala (all at v1.0.0, verified May 30, 2026)
- **Test suites** — 140 function test files (5,041 assertions across 14 categories), TPC-H (22 queries), TPC-DS (99 queries, 194 plans)
- **Reference examples** — DuckDB (Java + C++), DataFusion (Python + Rust), Velox (C++)
- **REST API** — Spring Boot implementation with JWT auth, report submission, webhooks, leaderboard
- **Interactive demo** — Mock engines, HTML/JS dashboard, shell runners
- **CI/CD workflows** — 13 GitHub Actions workflows for SDK testing, API builds, deployment, and release

### ✅ Governance & Legal

- `LICENSE` — Apache License 2.0
- `NOTICE` — Third-party attributions
- `GOVERNANCE.md` — Meritocratic governance model
- `MAINTAINERS.md` — Named maintainer (Ranjan Sinha) with clear ownership boundaries
- `CONTRIBUTING.md` — Contribution guidelines, coding standards, PR process
- `CODE_OF_CONDUCT.md` — Contributor Covenant
- `SECURITY.md` — Vulnerability reporting, verified vs. operator-dependent controls
- `.github/ISSUE_TEMPLATE/` — Bug, feature, and doc templates
- `.github/PULL_REQUEST_TEMPLATE.md` — Comprehensive PR checklist
- `.github/dependabot.yml` — Automated dependency updates

### ✅ Documentation

- `README.md` — Quick start, architecture, test suites, SDK guide, troubleshooting (1,246 lines)
- `CHANGELOG.md` — Keep a Changelog format
- `ROADMAP.md` — Phased roadmap with completed items marked
- `docs/` — REST API guide, deployment guide, performance benchmarking, SDK verification
- SDK-specific `README.md` files in each `sdk/` subdirectory

### ✅ Security Baseline (as of July 11, 2026)

- 0 hardcoded secrets (gitleaks scan: 16 findings, all confirmed false positives)
- 3 known CVEs in dependency lockfiles — all upstream-fixed, remediation tracked in `SECURITY_SCAN_REPORT.md`
- No secrets in git history
- Container runs as non-root user
- JWT secrets required via environment variables only

---

## What Remains Before Launch

### ❌ BLOCKER 1 — Repository Transfer & Public Access

**Owner:** Repository Administrator  
**Effort:** 1–2 days

| Action | Status |
|--------|--------|
| Mirror/transfer from `github.ibm.com/rsinha/substrait-compliance` to `github.com/IBM/substrait-compliance` | ❌ |
| Make repository public | ❌ |
| Configure branch protection (require PR reviews, status checks on `main`) | ❌ |
| Enable GitHub Discussions | ❌ |
| Add repository topics: `substrait`, `compliance`, `testing`, `query-engine`, `sql` | ❌ |
| Set repository description | ❌ |

**Why it blocks:** Cannot announce an open source project that isn't publicly accessible.

---

### ❌ BLOCKER 2 — Community Infrastructure

**Owner:** Project Lead  
**Effort:** 3–5 days

| Action | Status |
|--------|--------|
| Set up `security@substrait.io` email address | ❌ |
| Set up `conduct@substrait.io` email address | ❌ |
| Verify `substrait-dev@googlegroups.com` mailing list access | ❌ |
| Create Slack `#substrait-compliance` channel | ❌ |
| Create GitHub Discussions categories (Announcements, General, Q&A, Show and Tell, Feature Requests) | ❌ |

**Why it blocks:** Security and conduct email addresses are referenced in `SECURITY.md`, `CODE_OF_CONDUCT.md`, and `MAINTAINERS.md`. These must be operational before public announcement.

---

### ❌ BLOCKER 3 — API Hosting Decision

**Owner:** Project Lead / Maintainers  
**Effort:** 2–3 days

The REST API is a complete reference implementation but its deployment status is undecided. Choose one path and update all documentation accordingly:

**Option A — Reference Implementation Only (Recommended for initial launch):**
- [ ] Confirm "reference implementation only" framing in `README.md`, `api/README.md`, and `OPEN_SOURCE_RELEASE_PLAN.md`
- [ ] Remove any references implying a hosted public service URL
- [ ] Clarify no SLA or on-call commitment

**Option B — Hosted Public Service:**
- [ ] Define hosting environment and operator
- [ ] Complete operational readiness (backup, restore, incident drills — see Section 5A of `RELEASE_CHECKLIST.md`)
- [ ] Document public API endpoint URL
- [ ] Establish support ownership and SLA
- [ ] Set up monitoring and alerting

**Why it blocks:** Current documentation is ambiguous about whether a hosted service is available.

---

### ❌ BLOCKER 4 — Final Pre-Launch Security Re-scan

**Owner:** Project Lead  
**Effort:** 1 day

The last comprehensive security scan was July 11, 2026 (see `SECURITY_SCAN_REPORT.md`). A fresh scan must be run within 7 days of the planned launch date.

| Action | Status |
|--------|--------|
| Re-run `gitleaks detect` (secret scanning) | ❌ |
| Re-run `trivy fs --scanners vuln .` (dependency CVE scan) | ❌ |
| Review and remediate any new HIGH/CRITICAL findings | ❌ |
| Update `SECURITY_SCAN_REPORT.md` with results | ❌ |

---

### 🟡 BLOCKER 5 — Documentation Consistency (Mostly Done)

**Owner:** Contributors  
**Effort:** 1 day

Core governance documents and README have been updated. The following tracked files still contain `substrait-io/substrait-compliance` placeholder URLs that will need to become `IBM/substrait-compliance` (or the actual final public URL) before launch:

| File | Occurrences |
|------|-------------|
| `GOVERNANCE.md` | 1 |
| `api/API_USAGE.md` | 1 |
| `api/README.md` | 1 |
| `docs/API_IMPLEMENTATION.md` | 1 |
| `docs/SUBSTRAIT_COMPLIANCE_FRAMEWORK_GUIDE.md` | 1 |
| `sdk/cpp/README.md` | 3 |
| `sdk/csharp/README.md` | 2 |
| `sdk/go/README.md` | 1 |
| `sdk/scala/README.md` | 1 |
| `sdk/typescript/README.md` | 2 |

Once the public repository URL is confirmed, a single `sed` pass will close this:
```bash
grep -rl "substrait-io/substrait-compliance" . | xargs sed -i 's|substrait-io/substrait-compliance|IBM/substrait-compliance|g'
```

Additional remaining items:
- [ ] `OPEN_SOURCE_RELEASE_REPORT.md` — stale "1792 lines" README reference (now 1,246)
- [ ] `RELEASE_CHECKLIST.md` — mark items completed since last update
- [ ] `api/build.gradle` — version is `1.0.0-SNAPSHOT`; bump to `1.0.0` before release tag

---

## Strongly Recommended Before Launch

### ⚠️ REC 1 — CI/CD Full Build Verification

**Effort:** 1–2 days

Build configurations have been verified statically. Run actual CI builds to confirm every SDK compiles and its tests pass on a clean machine:

| SDK | Command | Status |
|-----|---------|--------|
| Java | `cd sdk/java && ./gradlew build test` | ❌ Not run in CI |
| Python | `cd sdk/python && pip install -e . && pytest` | ❌ Not run in CI |
| Rust | `cd sdk/rust && cargo build --release && cargo test` | ❌ Not run in CI |
| Go | `cd sdk/go && go test ./...` | ❌ Not run in CI |
| TypeScript | `cd sdk/typescript && npm install && npm run build && npm test` | ❌ Not run in CI |
| C++ | `cd sdk/cpp && mkdir build && cmake .. && make && ctest` | ❌ Not run in CI |
| C# | `cd sdk/csharp && dotnet restore && dotnet build && dotnet test` | ❌ Not run in CI |
| Scala | `cd sdk/scala && sbt compile test` | ❌ Not run in CI |

---

### ⚠️ REC 2 — Support Policy

**Effort:** 1 day

Define and document the support commitment before making a public announcement. At minimum, confirm and write down:

- [ ] The first stable supported-version window (e.g., "latest `main` only")
- [ ] Security triage owner and expected response time
- [ ] Whether previous minor releases receive security patches
- [ ] Update `SECURITY.md` to reflect the actual commitment (not just "best effort pre-release")

---

### ⚠️ REC 3 — Release Candidate Testing

**Effort:** 3–7 days

Tag `v1.0.0-rc1`, announce to early adopters, and collect feedback before the final tag:

- [ ] `git tag -a v1.0.0-rc1 -m "Release candidate 1"`
- [ ] `git push origin v1.0.0-rc1`
- [ ] Announce in GitHub Discussions and Substrait Slack
- [ ] Allow 3–7 days for community testing
- [ ] Fix any critical issues found
- [ ] Promote to `v1.0.0` after RC period

---

## Launch Sequence

### Week 1 — Final Validation

1. Make API hosting decision (Blocker 3)
2. Fix remaining URL inconsistencies — SDK READMEs, `GOVERNANCE.md`, `api/` docs (Blocker 5)
3. Bump `api/build.gradle` from `1.0.0-SNAPSHOT` to `1.0.0`
4. Run CI builds for all 8 SDKs (Rec 1)
5. Define and document support policy (Rec 2)

### Week 2 — Infrastructure Setup

1. Execute repository transfer/mirror to `github.com/IBM/substrait-compliance` (Blocker 1)
2. Configure repository: branch protection, topics, description, Discussions (Blocker 1)
3. Verify all email addresses and communication channels operational (Blocker 2)
4. Create GitHub Discussions categories (Blocker 2)

### Week 3 — Release Candidate

1. Re-run security scans within 7 days of launch date (Blocker 4)
2. Tag `v1.0.0-rc1` and announce for community testing (Rec 3)
3. If API is hosted: complete operational evidence (backup/restore/incident drills)
4. Collect RC feedback and fix critical issues

### Launch Day

1. Tag `v1.0.0`:
   ```bash
   git tag -a v1.0.0 -m "Initial public release"
   git push origin v1.0.0
   ```
2. Create GitHub Release with `CHANGELOG.md` content
3. Publish SDK packages (Java → Maven Central, Python → PyPI, Rust → crates.io, TypeScript → npm, C# → NuGet)
4. Make repository public
5. Post announcements:
   - GitHub Discussions
   - `substrait-dev@googlegroups.com`
   - Substrait Slack (`#substrait-compliance`)
   - Substrait website (if applicable)
6. Monitor issues for 48 hours

---

## Open Decisions

| # | Decision | Options | Recommended |
|---|----------|---------|-------------|
| 1 | **API hosting at launch** | (A) Reference impl only / (B) Hosted service | A — simplifies launch scope |
| 2 | **Support commitment** | Best-effort only / Defined SLA | Best-effort explicitly stated |
| 3 | **RC period length** | 3 days / 7 days / skip | 5 days |
| 4 | **Initial SDK package publishing** | Publish all at launch / Java + Python only / none | Java + Python first |

---

## Rollback Plan

If a critical issue is discovered after launch:

1. Mark the GitHub Release as a pre-release
2. Post a notice in GitHub Discussions and Slack
3. Create a `hotfix/v1.0.1` branch from the `v1.0.0` tag
4. Apply minimal fix, update `CHANGELOG.md`, bump version to `1.0.1`
5. Follow the RC process above before re-releasing
6. Document root cause in a post-mortem issue

---

## Contacts & Ownership

| Role | Owner | Contact |
|------|-------|---------|
| Release authority | Ranjan Sinha | GitHub: [@RanjanSinha-GH](https://github.com/RanjanSinha-GH) |
| Security triage | Ranjan Sinha | security@substrait.io (once live) |
| Repository transfer | Repository Administrator | TBD |
| Community setup | Project Lead | TBD |

---

## Supporting Documents

| Document | Purpose |
|----------|---------|
| [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md) | Detailed step-by-step release checklist |
| [`REMAINING_LAUNCH_BLOCKERS.md`](REMAINING_LAUNCH_BLOCKERS.md) | Detailed breakdown of each critical blocker |
| [`OPEN_SOURCE_RELEASE_REPORT.md`](OPEN_SOURCE_RELEASE_REPORT.md) | Full readiness assessment with scoring |
| [`SDK_TEST_SUITE_VERIFICATION.md`](SDK_TEST_SUITE_VERIFICATION.md) | SDK and test suite verification evidence |
| [`SECURITY_SCAN_REPORT.md`](SECURITY_SCAN_REPORT.md) | Latest security scan results |
| [`CHANGELOG.md`](CHANGELOG.md) | Release notes for v1.0.0 |
| [`SECURITY.md`](SECURITY.md) | Security policy and vulnerability reporting |
| [`MAINTAINERS.md`](MAINTAINERS.md) | Maintainer list and responsibilities |

---

*This document is the single source of truth for the v1.0.0 open source launch. Update it as blockers are resolved and decisions are made.*
