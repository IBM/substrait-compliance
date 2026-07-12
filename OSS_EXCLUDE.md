# Open Source Exclusion Manifest

This document is the authoritative human-readable list of files and folders
that exist in the **private** repository
(`github.ibm.com/rsinha/substrait-compliance`) but must **not** be pushed to
the **public** open source repository
(`github.com/IBM/substrait-compliance`).

The companion machine-readable file is [`.oss-gitignore`](.oss-gitignore).

---

## How exclusions work

Files listed here were removed from the git index with `git rm --cached` so
they are absent from the public repo's history. They remain fully present and
tracked in the private repo.

**To add a new exclusion:**

1. Add the path to [`.oss-gitignore`](.oss-gitignore) with a comment.
2. Run `git rm --cached -r <path>` to remove it from the index.
3. Add a row to the table below.
4. Commit `.oss-gitignore` and `OSS_EXCLUDE.md` together.

---

## Excluded paths

| Path | Type | Reason | Excluded since |
|------|------|---------|---------------|
| `test-suites-enhanced/` | Directory (136 files) | Internal quality-improvement artefacts; enhanced test cases not yet ready for public contribution | 2026-07-11 |
| `test-suites/functions.old/` | Directory (147 files) | Legacy snapshot of function test suite superseded by `test-suites/functions/`; kept for internal diff and audit only | 2026-07-11 |
| `quality_reports/` | Directory (21 files) | Automated quality-checker output files; internal tooling artefacts not meaningful to public users | 2026-07-11 |
| `ROADMAP.md` | File | Internal roadmap with timelines and priorities not suitable for public commitment | 2026-07-11 |
| `CHANGELOG.md` | File | Internal changelog; public release notes will be published via GitHub Releases | 2026-07-11 |
| `SDK_TEST_SUITE_VERIFICATION.md` | File | Internal verification record produced during pre-release SDK audit | 2026-07-11 |
| `REMAINING_LAUNCH_BLOCKERS.md` | File | Internal release blocker tracking document | 2026-07-11 |
| `RELEASE_CHECKLIST.md` | File | Internal release process checklist | 2026-07-11 |
| `OPEN_SOURCE_RELEASE_REPORT.md` | File | Internal open source readiness report | 2026-07-11 |
| `SECURITY_SCAN_REPORT.md` | File | Internal security scan results; contains CVE details and tool output not appropriate for public repo | 2026-07-11 |
| `demo/dashboard/TPCDS_INTEGRATION.md` | File | Internal integration note; gitignored (never tracked) | 2026-07-11 |
| `docs/CDMS2026_Decentralized_Compliance_Framework.md` | File | Draft paper; gitignored (never tracked) | 2026-07-11 |

---

## Files excluded from both repos (via `.gitignore`)

Standard build artefacts, IDE files, secrets, and credentials are excluded from
**both** repos by the standard [`.gitignore`](.gitignore) file. That file should
not contain any OSS-specific paths — those belong here and in
[`.oss-gitignore`](.oss-gitignore).

---

*This file lives in the private repo only and is itself excluded from the public repo.*

**Last updated**: July 11, 2026
