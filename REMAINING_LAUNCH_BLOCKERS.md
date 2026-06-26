# Remaining Launch Blockers
**Substrait Compliance Framework**  
**Date:** May 30, 2026  
**Status:** Post-Verification Summary

---

## Executive Summary

Based on the comprehensive open source release review and subsequent SDK/test suite verification, the following items remain as blockers before the project can be publicly launched.

**Items Completed:** ✅ SDK Verification, ✅ Test Suite Audit  
**Items Remaining:** 5 Critical Blockers + 4 High Priority Items

---

## ✅ COMPLETED - No Longer Blockers

### 1. SDK Verification ✅ RESOLVED
**Previous Status:** ⚠️ NEEDS VALIDATION  
**Current Status:** ✅ **VERIFIED**

All 8 SDKs have been verified:
- ✅ Proper build configurations present
- ✅ All version numbers synchronized at 1.0.0
- ✅ Package metadata complete for publication
- ✅ Dependencies properly declared
- ✅ Test files present
- ✅ Examples included

**Evidence:** See [`SDK_TEST_SUITE_VERIFICATION.md`](SDK_TEST_SUITE_VERIFICATION.md)

---

### 2. Test Suite Completeness ✅ RESOLVED
**Previous Status:** ⚠️ NEEDS CLARIFICATION  
**Current Status:** ✅ **VERIFIED**

All test suite claims are accurate:
- ✅ 279 function test files (143 standard + 136 enhanced)
- ✅ TPC-H: 22 queries confirmed
- ✅ TPC-DS: 99 queries confirmed
- ✅ 16 function categories documented

**Evidence:** See [`SDK_TEST_SUITE_VERIFICATION.md`](SDK_TEST_SUITE_VERIFICATION.md)

---

## ❌ CRITICAL BLOCKERS (Must Complete Before Launch)

### 1. Repository Transfer & Public Access
**Status:** ❌ **BLOCKER**  
**Owner:** Repository Administrator  
**Estimated Effort:** 1-2 days

**Required Actions:**
- [ ] Transfer/mirror repository from github.ibm.com to github.com/substrait-io/substrait-compliance
- [ ] Make repository public
- [ ] Configure branch protection on main branch (require PR reviews, status checks)
- [ ] Enable GitHub Discussions
- [ ] Add repository topics/tags: `substrait`, `compliance`, `testing`, `query-engine`, `sql`
- [ ] Set repository description
- [ ] Configure GitHub Pages (if documentation site planned)

**Why This Blocks Launch:** Cannot announce an open source project that isn't publicly accessible.

**Next Steps:**
1. Coordinate with GitHub organization admins for substrait-io
2. Execute repository transfer
3. Verify all links and references update correctly
4. Test public access

---

### 2. Community Infrastructure Setup
**Status:** ❌ **BLOCKER**  
**Owner:** Project Lead / Infrastructure Team  
**Estimated Effort:** 3-5 days

**Required Actions:**
- [ ] Set up `security@substrait.io` email address
- [ ] Set up `conduct@substrait.io` email address
- [ ] Verify `substrait-dev@googlegroups.com` mailing list access
- [ ] Create Slack `#substrait-compliance` channel
- [ ] Create GitHub Discussions categories:
  - Announcements
  - General
  - Q&A
  - Show and Tell
  - Feature Requests
- [ ] Document how to access each communication channel

**Why This Blocks Launch:** Security and conduct emails are referenced in multiple governance documents. Must be operational before public announcement.

**Next Steps:**
1. Request email addresses from Substrait organization
2. Set up email forwarding/distribution lists
3. Test email delivery
4. Create Slack channel and invite initial members
5. Configure GitHub Discussions

---

### 3. API Status Decision & Documentation
**Status:** ❌ **BLOCKER**  
**Owner:** Project Lead / Maintainers  
**Estimated Effort:** 2-3 days

**Required Decision:**
Will the REST API be:
- **Option A:** Hosted as a public service at launch
- **Option B:** Provided as reference implementation only (local/self-hosted)

**If Option A (Hosted Service):**
- [ ] Complete production validation checklist
- [ ] Establish support ownership and SLA
- [ ] Set up monitoring and alerting
- [ ] Document API endpoint URL
- [ ] Provide API keys/authentication mechanism
- [ ] Update all documentation to reflect hosted service

**If Option B (Reference Only):**
- [ ] Update README.md to clarify "reference implementation only"
- [ ] Update API documentation to emphasize local deployment
- [ ] Remove any references to hosted service
- [ ] Clarify no SLA or support commitment for API

**Why This Blocks Launch:** Current documentation is ambiguous. API README explicitly states "pre-release" and "not yet declared as hosted public service." Must clarify before launch to avoid user confusion.

**Next Steps:**
1. Maintainers decide on API hosting strategy
2. Update all documentation consistently
3. If hosting: Complete operational readiness
4. If reference-only: Update messaging throughout docs

---

### 4. Final Security Scan
**Status:** ❌ **BLOCKER**  
**Owner:** Security Team / Project Lead  
**Estimated Effort:** 1 day

**Required Actions:**
- [ ] Re-run dependency scanning (Dependabot, Snyk, or Trivy)
- [ ] Re-run secret scanning (gitleaks or similar)
- [ ] Review and address any new vulnerabilities
- [ ] Update SECURITY_SCAN_REPORT.md with latest results
- [ ] Verify no secrets in git history

**Why This Blocks Launch:** Security scan report is dated. Must verify no new vulnerabilities introduced since last scan.

**Next Steps:**
1. Run automated security scans
2. Review results
3. Address any critical/high findings
4. Document scan results
5. Get security sign-off

---

### 5. Documentation Consistency Pass
**Status:** ❌ **BLOCKER**  
**Owner:** Documentation Team / Contributors  
**Estimated Effort:** 1-2 days

**Required Actions:**
- [ ] Global search/replace: `github.ibm.com` → `github.com/substrait-io`
- [ ] Verify all internal links work
- [ ] Remove placeholder text (e.g., "None yet" in MAINTAINERS.md)
- [ ] Synchronize version numbers across all SDK build files
- [ ] Update "last updated" dates in governance documents
- [ ] Verify contact information is correct throughout

**Why This Blocks Launch:** Inconsistent documentation creates confusion and looks unprofessional.

**Next Steps:**
1. Run global search for common inconsistencies
2. Create checklist of files to update
3. Make updates systematically
4. Peer review changes
5. Test all links

---

## ⚠️ HIGH PRIORITY (Should Complete Before Launch)

### 6. Operational Evidence Generation
**Status:** ⚠️ **RECOMMENDED**  
**Owner:** Operations Team  
**Estimated Effort:** 2-3 days

**Required Actions (from RELEASE_CHECKLIST.md Section 5A):**
- [ ] Document deployment owner for target environment
- [ ] Record rollback owner and rollback trigger
- [ ] Perform backup rehearsal and record evidence
- [ ] Perform restore rehearsal and record outcome
- [ ] Perform incident drill (secret exposure scenario)
- [ ] Store evidence links with release notes

**Why This Is Important:** Demonstrates operational readiness and provides confidence in production deployment capabilities.

**Can Launch Without:** Yes, if API is reference-only. No, if API will be hosted.

---

### 7. Support Policy Definition
**Status:** ⚠️ **RECOMMENDED**  
**Owner:** Project Lead / Maintainers  
**Estimated Effort:** 1 day

**Required Actions:**
- [ ] Define first stable supported-version window
- [ ] Confirm security triage ownership and response times
- [ ] Decide on previous minor release support policy
- [ ] Update SECURITY.md with actual support commitments
- [ ] Update README.md to match support policy

**Why This Is Important:** Users need to know what support to expect.

**Can Launch Without:** Yes, but should clarify "best effort" support explicitly.

---

### 8. CI/CD Full Build Verification
**Status:** ⚠️ **RECOMMENDED**  
**Owner:** CI/CD Team  
**Estimated Effort:** 1-2 days

**Required Actions:**
- [ ] Run full CI builds for all 8 SDKs
- [ ] Verify all tests pass
- [ ] Test example code execution
- [ ] Perform cross-platform testing (Linux, macOS, Windows)
- [ ] Run integration tests against test suites
- [ ] Document any build failures or limitations

**Why This Is Important:** Build configurations are verified, but actual compilation hasn't been tested in CI.

**Can Launch Without:** Yes, but increases risk of user-reported build failures.

---

### 9. Release Candidate Testing
**Status:** ⚠️ **RECOMMENDED**  
**Owner:** Community / Early Adopters  
**Estimated Effort:** 3-7 days

**Required Actions:**
- [ ] Tag v1.0.0-rc1
- [ ] Deploy to staging environment (if applicable)
- [ ] Announce RC for community testing
- [ ] Collect feedback
- [ ] Fix critical issues
- [ ] Create additional RCs if needed

**Why This Is Important:** Catches issues before final release.

**Can Launch Without:** Yes, but increases risk of post-launch issues.

---

## Summary: What Must Be Done

### Absolute Minimum for Launch (5 items):
1. ❌ Repository transfer and public access
2. ❌ Community infrastructure setup (emails, Slack, Discussions)
3. ❌ API status decision and documentation update
4. ❌ Final security scan
5. ❌ Documentation consistency pass

### Strongly Recommended (4 items):
6. ⚠️ Operational evidence (if hosting API)
7. ⚠️ Support policy definition
8. ⚠️ CI/CD full build verification
9. ⚠️ Release candidate testing

---

## Estimated Timeline to Launch

**Minimum Path (Critical Blockers Only):**
- Week 1: Repository transfer, community setup, security scan
- Week 2: API decision, documentation updates, final review
- **Total: 2 weeks**

**Recommended Path (Including High Priority):**
- Week 1: Repository transfer, community setup, security scan
- Week 2: API decision, documentation updates, CI/CD verification
- Week 3: RC testing, operational evidence, support policy
- Week 4: Final review and launch
- **Total: 4 weeks**

---

## Decision Points

### Key Questions to Answer:
1. **Will the API be hosted publicly at launch?**
   - If yes: Complete operational readiness
   - If no: Update documentation to clarify reference-only

2. **What support commitment can be made?**
   - Best effort only?
   - Defined response times for security issues?
   - Supported version window?

3. **Is RC testing required?**
   - Recommended for v1.0.0 launch
   - Can skip if treating as "soft launch" to early adopters

---

## Conclusion

**SDK Verification and Test Suite Audit are COMPLETE.**

**5 critical blockers remain** before the project can be truthfully represented as open source and publicly launched. These are primarily **external execution steps** (repository transfer, infrastructure setup) and **policy decisions** (API hosting, support commitments) rather than code issues.

**Estimated time to launch-ready:** 2-4 weeks depending on chosen path.

**Recommendation:** Complete all 5 critical blockers before any public announcement. Consider completing high-priority items for a more robust launch.

---

**Report Generated:** May 30, 2026  
**Based On:** 
- [`OPEN_SOURCE_RELEASE_REPORT.md`](OPEN_SOURCE_RELEASE_REPORT.md)
- [`SDK_TEST_SUITE_VERIFICATION.md`](SDK_TEST_SUITE_VERIFICATION.md)