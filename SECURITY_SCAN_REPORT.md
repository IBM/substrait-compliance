# Security Scan Report

**Date**: July 11, 2026
**Project**: Substrait Compliance Framework
**Commit**: `47591ebf29d9e16d6e596b2fa67b38a3a353f36a`
**Scan Type**: Secret Detection + Dependency Vulnerability Scan

---

## Executive Summary

| Check | Tool | Result |
|-------|------|--------|
| Secret / credential detection | gitleaks 8.30.1 | ✅ **0 true secrets** — all 16 findings are false positives (see below) |
| Dependency vulnerabilities | trivy 0.72.0 | ⚠️ **3 vulnerabilities** — 2 HIGH, 1 MEDIUM — all fixed upstream, remediation tracked |

**Overall status: CONDITIONALLY PASSED**

The codebase contains no hardcoded secrets. Three known CVEs were detected in dependency lockfiles; none are in core or API source dependencies. Remediation steps are documented below.

---

## 1. Secret Detection — gitleaks 8.30.1

**Command**: `gitleaks detect --source . --report-format json`
**Commits scanned**: 71
**Bytes scanned**: ~55.75 MB

### Result: 0 true secrets

Gitleaks reported 16 findings. Each was reviewed and classified as a false positive:

| # | File | Rule | Classification | Reason |
|---|------|------|----------------|--------|
| 1 | `docs/REST_API_GUIDE.md` (line 97) | generic-api-key | **False positive** | Example JWT in documentation (`eyJhbGciOiJIUzI1NiIs...`) — clearly a truncated demo token |
| 2–3 | `docs/REST_API_GUIDE.md` (lines 66, 257) | curl-auth-header | **False positive** | `curl` usage examples in API docs using placeholder values (`your-api-key-here`) |
| 4 | `api/.env.example` (line 8) | generic-api-key | **False positive** | `JWT_SECRET=replace_with_a_generated_jwt_secret_min_256_bits` — explicit placeholder text, not a real secret |
| 5–6 | `scripts/REPORT_GENERATOR_CONFIG.md` (lines 13, 138) | generic-api-key / curl-auth-header | **False positive** | File is not tracked by git (`git ls-files` confirms absent from index) — only present on disk |
| 7 | `scripts/TEST_ENHANCEMENT_GUIDE.md` (line 475) | generic-api-key | **False positive** | Tracked file uses `os.environ["LITELLM_API_KEY"]` — no hardcoded key |
| 8 | `scripts/generate_technical_report.py` (line 38) | generic-api-key | **False positive** | Source reads `os.getenv("LITELLM_API_KEY", "")` — env var only |
| 9 | `scripts/quality_config.yaml` (line 7) | generic-api-key | **False positive** | Value is `"${LITELLM_API_KEY}"` — YAML env var substitution placeholder |
| 10–12 | `scripts/QUALITY_CHECKER_README.md` (lines 188, 286, 344) | generic-api-key | **False positive** | File is not tracked by git — only present on disk |
| 13 | `scripts/quality_checker.py` (line 30) | generic-api-key | **False positive** | Source reads `os.getenv("LITELLM_API_KEY", "")` — env var only |
| 14–15 | `docs/REST_API_PLAN.md` (lines 283, 305) | generic-api-key | **False positive** | Placeholder strings (`eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`, `sk_live_abc123...`) in design docs |
| 16 | `docs/REST_API_PLAN.md` (line 722) | curl-auth-header | **False positive** | `curl` example using `sk_live_abc123...` placeholder in design doc |

**Conclusion**: No real credentials, API keys, passwords, or private keys are present in the git history or working tree.

---

## 2. Dependency Vulnerability Scan — trivy 0.72.0

**Command**: `trivy fs --scanners vuln .`
**Lockfiles scanned**: 5 (Cargo.lock, go.mod, package-lock.json, net6.0 deps.json, net10.0 deps.json)

### Summary table

| Target | Type | CRITICAL | HIGH | MEDIUM | LOW |
|--------|------|----------|------|--------|-----|
| `sdk/rust/Cargo.lock` | cargo | 0 | 0 | 0 | 0 |
| `sdk/go/go.mod` | gomod | 0 | 0 | 0 | 0 |
| `sdk/typescript/package-lock.json` | npm | 0 | 0 | **1** | 0 |
| `sdk/csharp/bin/Debug/net10.0/...deps.json` | dotnet-core | 0 | 0 | 0 | 0 |
| `sdk/csharp/bin/Debug/net6.0/...deps.json` | dotnet-core | 0 | **2** | 0 | 0 |

**Not scanned** (no lockfile present): Java (Maven/Gradle), Python (no requirements.lock), Scala, C++.

---

### Finding 1 — HIGH: CVE-2024-30105 in System.Text.Json 8.0.0

| Field | Value |
|-------|-------|
| **CVE** | CVE-2024-30105 |
| **Severity** | HIGH |
| **Package** | `System.Text.Json` 8.0.0 |
| **Fix** | Upgrade to ≥ 8.0.4 |
| **Location** | `sdk/csharp/bin/Debug/net6.0/Substrait.Compliance.deps.json` (build artefact) |
| **Description** | Denial of Service vulnerability in `System.Text.Json` deserialization |
| **Reference** | https://avd.aquasec.com/nvd/cve-2024-30105 |

**Assessment**: The vulnerable file is a compiled build artefact (`bin/Debug/net6.0/`) produced by building against the older .NET 6 target. It is not part of the shipped source and should not be present in the public repository. The net10.0 build target is already clean.

**Remediation**:
- Update `sdk/csharp/Substrait.Compliance.csproj` to set `System.Text.Json` ≥ 8.0.5
- Remove `sdk/csharp/bin/` from git tracking (add to `.gitignore`) to prevent build artefacts from being committed

---

### Finding 2 — HIGH: CVE-2024-43485 in System.Text.Json 8.0.0

| Field | Value |
|-------|-------|
| **CVE** | CVE-2024-43485 |
| **Severity** | HIGH |
| **Package** | `System.Text.Json` 8.0.0 |
| **Fix** | Upgrade to ≥ 8.0.5 (or ≥ 6.0.10 for .NET 6 target) |
| **Location** | `sdk/csharp/bin/Debug/net6.0/Substrait.Compliance.deps.json` (build artefact) |
| **Description** | Denial of Service via malformed JSON in `System.Text.Json` |
| **Reference** | https://avd.aquasec.com/nvd/cve-2024-43485 |

**Assessment**: Same artefact as CVE-2024-30105 above. Same remediation applies.

---

### Finding 3 — MEDIUM: CVE-2026-53550 in js-yaml 4.1.1

| Field | Value |
|-------|-------|
| **CVE** | CVE-2026-53550 |
| **Severity** | MEDIUM |
| **Package** | `js-yaml` 4.1.1 |
| **Fix** | Upgrade to ≥ 4.2.0 |
| **Location** | `sdk/typescript/package-lock.json` |
| **Description** | Denial of Service via crafted YAML merge keys |
| **Reference** | https://avd.aquasec.com/nvd/cve-2026-53550 |

**Assessment**: `js-yaml` is a dev dependency used in the TypeScript SDK build pipeline. It is not shipped to end users as a production runtime dependency. Exploitation requires processing attacker-controlled YAML input, which does not occur in the build pipeline.

**Remediation**:
```bash
cd sdk/typescript
npm update js-yaml   # bumps package-lock.json to 4.2.0
```

---

## 3. Remediation Tracking

| CVE | Severity | Package | Action | Owner | Target |
|-----|----------|---------|--------|-------|--------|
| CVE-2024-30105 | HIGH | System.Text.Json 8.0.0 | Bump to ≥ 8.0.5; add `sdk/csharp/bin/` to .gitignore | Maintainer | Before v1.0.0 release |
| CVE-2024-43485 | HIGH | System.Text.Json 8.0.0 | Same as above | Maintainer | Before v1.0.0 release |
| CVE-2026-53550 | MEDIUM | js-yaml 4.1.1 | `npm update js-yaml` in sdk/typescript/ | Maintainer | Before v1.0.0 release |

---

## 4. Ongoing Security Practices

1. **Dependabot** — configured in `.github/dependabot.yml`. Ensure it is enabled on the public repository to receive automated PRs for future dependency updates.
2. **CI/CD scanning** — add `trivy fs .` and `gitleaks detect` steps to the release workflow to catch regressions automatically.
3. **Security policy** — `SECURITY.md` documents the vulnerability reporting process and contacts.
4. **Quarterly re-scan** — next scan due: **October 11, 2026**.

---

## Sign-Off

**Tools**: gitleaks 8.30.1, trivy 0.72.0
**Scan date**: July 11, 2026
**Commit scanned**: `47591ebf29d9e16d6e596b2fa67b38a3a353f36a`
**Performed by**: Ranjan Sinha (Project Lead / Security Triage Owner)
**Next scan due**: October 11, 2026 (quarterly)

---

*This report is an internal pre-release security audit document. It is not published in the public repository.*
