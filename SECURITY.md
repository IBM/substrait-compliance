# Security Policy

## Supported Versions

Security support commitments remain pre-release. Only the controls explicitly called out as verified below should be treated as current guarantees.

At the current launch stage, security support is owned by the named maintainer of record in [`MAINTAINERS.md`](MAINTAINERS.md). There is no separate staffed security operations team or 24x7 incident-response rotation.

| Version | Supported |
| ------- | --------- |
| [`main`](README.md) | Best effort for active development and pre-release validation only |
| pre-release tags | Best effort for the most recent tagged pre-release only |
| older pre-release tags | Not supported |
| stable releases | No stable support window is claimed until the project publishes its first stable release and updates this policy |

## Reporting a Vulnerability

We take the security of the Substrait Compliance Framework seriously. If you believe you have found a security vulnerability, please report it to us as described below.

### Where to Report

**Please do NOT report security vulnerabilities through public GitHub issues.**

Instead, please report them via email to:

- **Primary Contact**: [rsinha@us.ibm.com](mailto:rsinha@us.ibm.com)
- **Secondary Contact**: [rsinha@us.ibm.com](mailto:rsinha@us.ibm.com)
- **Alternative**: Open a [GitHub Security Advisory](https://github.com/IBM/substrait-compliance/security/advisories/new)

### What to Include

Please include the following information in your report:

- Type of vulnerability (e.g., SQL injection, XSS, authentication bypass)
- Full paths of source file(s) related to the vulnerability
- Location of the affected source code (tag/branch/commit or direct URL)
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the vulnerability, including how an attacker might exploit it

### Response Timeline

- **Security Triage Owner**: Ranjan Sinha, acting as maintainer of record and security triage coordinator
- **Initial Response**: Best effort acknowledgment during normal maintainer working hours (UTC-7)
- **Status Update**: Best effort follow-up after triage
- **Fix Timeline**: Depends on severity, maintainer availability, and release readiness
- **Disclosure**: Coordinated disclosure after a fix or mitigation is available
- **Incident Coordination**: Current process is maintainer-driven email/advisory triage; no separate 24x7 incident-response rotation is claimed

### What to Expect

1. **Acknowledgment**: We will acknowledge receipt of your vulnerability report
2. **Assessment**: We will assess the vulnerability and determine its severity
3. **Fix Development**: We will develop and test a fix
4. **Disclosure**: We will coordinate disclosure with you
5. **Credit**: We will credit you in the security advisory (unless you prefer to remain anonymous)

## Security Best Practices

### What This Repository Ships

The repository ships libraries, scripts and GitHub Actions workflows: the eight SDKs, the
test suites, the compliance runners, and the leaderboard published as static files on GitHub
Pages. It does not run a hosted service, and there is no server, database or account to
secure. (A pre-release REST API was removed in favour of pull-request submissions; see the
[CHANGELOG](CHANGELOG.md).)

#### Verified in Repository Artifacts

1. **Leaderboard submissions are data, checked by trusted code**
   - A submission ([`submissions/README.md`](submissions/README.md)) contains only JSON files;
     nothing in it is executed.
   - [`.github/workflows/submission-validation.yml`](.github/workflows/submission-validation.yml)
     runs the checker, suites and schemas from the base branch, never from the pull request, with
     a read-only token and no secrets.
   - Scores are recomputed from per-case results; submitted pass rates are ignored.

2. **The reusable submission workflow keeps the engine team's token out of the checks**
   - [`.github/workflows/submit-results.yml`](.github/workflows/submit-results.yml) uses the token
     only to push to the team's fork and open the pull request.
   - Teams are told to use a dedicated bot account and to pin the workflow to a commit SHA.

3. **Dependency and artifact validation are part of repository automation**
   - [`.github/workflows/sdk-build-test.yml`](.github/workflows/sdk-build-test.yml) builds and tests every SDK
   - [`.github/workflows/release-publish.yml`](.github/workflows/release-publish.yml) validates
     release-candidate artifacts and smoke-tests packaged outputs before publication
   - Dependabot proposes updates for GitHub Actions and the Java SDK

#### Not Yet Claimed as Guarantees

1. **Self-reported results**: the submission check catches inconsistent or incomplete results,
   not deliberately invented ones. Review, the linked CI run and maintainer re-runs are the safeguards.
2. **Repository-wide dependency scanning and incident-response readiness**: continuous scanning
   coverage, alert routing and staffed incident response are not claimed as service guarantees.

### For Users

- Run the compliance suites in your own CI; no data leaves your environment unless you submit it.
- When submitting from CI, follow [`submissions/README.md`](submissions/README.md): a dedicated bot
  account, the token stored as a secret, and the reusable workflow pinned to a commit SHA.
- Treat test-suite files and other engines' submissions as untrusted input in your own tooling.

### For Contributors

1. **Code Review**
   - All code changes require review
   - Changes to the submission checker, schemas and workflows (listed in
     [`CODEOWNERS`](.github/CODEOWNERS)) need a maintainer's approval
   - Keep automated validation green before merge

2. **Dependencies**
   - Keep dependencies up to date
   - Review dependency advisories before release
   - Treat release-candidate workflow failures as blockers for publication

3. **Testing**
   - Add tests for input handling when touching parsers, the submission checker or the runners
   - Prefer release-artifact smoke tests over source-only validation for user-facing packages

4. **Secrets Management**
   - Never commit secrets to the repository
   - Workflows that run on pull requests must not receive secrets or a write token
   - Rotate any accidentally exposed credentials immediately

## Security Implementation Status

The project is **pre-release**. Only the controls listed under "Verified in Repository
Artifacts" should be treated as current behavior.

### Verification Standard for Future Guarantees

Before describing any protection as supported in a stable release, verify it with:
- automated tests covering the relevant behavior
- documentation showing how it is enabled
- release validation confirming the behavior in the shipped artifact
- an identified maintainer process for triage and incident handling

## Vulnerability Disclosure Policy

### Scope

This security policy applies to:

- Substrait Compliance Framework core
- Leaderboard submission checks and GitHub Actions workflows
- Java, Python, and Rust SDKs
- Demo applications
- Documentation and examples

### Out of Scope

The following are generally considered out of scope:

- Vulnerabilities in third-party dependencies (report to the respective projects)
- Social engineering attacks
- Physical attacks
- Denial of Service (DoS) attacks without demonstrable impact
- Issues in unsupported versions

### Safe Harbor

We support safe harbor for security researchers who:

- Make a good faith effort to avoid privacy violations and data destruction
- Only interact with accounts you own or with explicit permission
- Do not exploit a vulnerability beyond what is necessary to demonstrate it
- Report vulnerabilities promptly
- Keep vulnerability details confidential until we've had a chance to address them

We will not pursue legal action against researchers who follow these guidelines.

## Security Updates

### Notification Channels

Stay informed about security updates:

- **GitHub Security Advisories**: [github.com/IBM/substrait-compliance/security/advisories](https://github.com/IBM/substrait-compliance/security/advisories)
- **Release Notes**: Check [`CHANGELOG.md`](CHANGELOG.md) for security fixes in published releases
- **Repository Documentation**: Review [`SECURITY.md`](SECURITY.md) and release notes in the tagged version you deploy

### Update Recommendations

- **Critical**: Update immediately (within 24 hours)
- **High**: Update within 7 days
- **Medium**: Update within 30 days
- **Low**: Update at next convenient maintenance window

## Additional Resources

- [Deployment Guide](docs/DEPLOYMENT_GUIDE.md)
- [Contributing Guidelines](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Leaderboard submissions](submissions/README.md)

## Contact

For security-related questions or concerns:

- **Primary Email**: [rsinha@us.ibm.com](mailto:rsinha@us.ibm.com)
- **Secondary Email**: [rsinha@us.ibm.com](mailto:rsinha@us.ibm.com)
- **GitHub**: [Security Advisories](https://github.com/IBM/substrait-compliance/security/advisories)

---

**Last Updated**: June 26, 2026

Thank you for helping keep the Substrait Compliance Framework and its users safe!