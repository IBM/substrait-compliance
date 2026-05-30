# Security Policy

## Supported Versions

Security support commitments remain pre-release. The repository contains security-related implementation and deployment scaffolding, but only the controls explicitly called out as verified below should be treated as current guarantees.

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

- **Primary Contact**: [security@substrait.io](mailto:security@substrait.io)
- **Alternative**: Open a [GitHub Security Advisory](https://github.com/substrait-io/substrait-compliance/security/advisories/new)

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

### Verified Today vs. Still Operator-Dependent

The repository now has enough implementation and deployment material to distinguish between controls that are **verified in-repo** and controls that are **still deployment/operator responsibilities**.

#### Verified in Repository Artifacts

The following statements are supported by repository code, configuration, or workflow evidence and can be treated as current verified behavior for the pre-release codebase:

1. **Containerized API deployment runs as a non-root user**
   - [`api/Containerfile`](api/Containerfile) creates and switches to the `spring` user before startup
   - The runtime image exposes only the application process and health check entrypoint

2. **Container and local deployment paths require externally supplied JWT secrets**
   - [`api/docker-compose.yml`](api/docker-compose.yml) requires `JWT_SECRET` for the API service
   - [`api/README.md`](api/README.md) instructs operators to generate secrets outside source control
   - [`api/src/main/resources/application.yml`](api/src/main/resources/application.yml) reads JWT configuration from environment variables

3. **Webhook signing uses canonical JSON serialization before HMAC generation**
   - [`api/src/main/java/io/substrait/compliance/api/service/WebhookDeliveryService.java`](api/src/main/java/io/substrait/compliance/api/service/WebhookDeliveryService.java) serializes payloads with Jackson before signature generation
   - This closes the earlier mismatch between payload representation and signature input

4. **Dependency and artifact validation are part of repository automation**
   - [` .github/workflows/sdk-build-test.yml`](.github/workflows/sdk-build-test.yml) verifies build/test coverage across claimed SDKs
   - [` .github/workflows/release-publish.yml`](.github/workflows/release-publish.yml) now validates release-candidate artifacts and smoke-tests packaged outputs before release publication

#### Not Yet Claimed as Guarantees

The following controls may be partially implemented or documented, but they are **not yet guaranteed by this repository alone** and must still be treated as deployment-specific or future hardening work:

1. **End-to-end authentication and authorization assurance**
   - JWT-related code and Spring Security dependencies exist
   - Full production auth-flow verification, token lifecycle testing, and role-boundary validation are not yet documented as release gates

2. **TLS / HTTPS enforcement**
   - HTTPS is recommended in docs
   - The repository does not ship certificates or prove that every deployment path terminates TLS correctly by default

3. **Rate limiting effectiveness in production**
   - Rate-limit configuration exists
   - Production tuning, proxy-awareness, and abuse-case validation remain operator responsibilities

4. **Audit logging and incident monitoring**
   - Logging and monitoring hooks exist
   - Centralized retention, alert routing, and incident escalation are not yet verified as maintained service guarantees

5. **Repository-wide dependency scanning and incident-response readiness**
   - Security review guidance and workflow-based validation exist
   - Continuous dependency-scanning coverage, alert routing, and staffed incident-response operations are not yet claimed as complete service guarantees

### For Users

#### API Security

1. **Authentication**
   - Use strong JWT secrets (minimum 256 bits)
   - Rotate API keys and signing secrets on an operator-defined schedule
   - Never commit secrets to version control
   - Use environment variables or a secret manager for sensitive configuration

2. **HTTPS/TLS**
   - Terminate TLS in front of the API for every production deployment
   - Use TLS 1.2 or higher
   - Keep certificates up to date
   - Validate upstream and downstream certificates explicitly

3. **Rate Limiting**
   - Configure limits appropriate to your traffic profile
   - Monitor for unusual traffic patterns
   - Add IP- or identity-based restrictions where needed

4. **Input Validation**
   - Treat all external input as untrusted
   - Keep validation enabled in deployed environments
   - Use parameterized queries and framework-managed persistence paths

#### Deployment Security

1. **Environment Variables**
   - Never hardcode secrets
   - Use secure secret management (for example Vault or cloud secret stores)
   - Rotate credentials regularly

2. **Network Security**
   - Restrict ingress to trusted networks or gateways
   - Place the API behind a reverse proxy or ingress controller
   - Use segmented networks/VPCs for database access

3. **Monitoring**
   - Forward application and security logs to centralized storage
   - Alert on repeated auth failures, webhook delivery failures, and unusual request spikes
   - Test incident contacts before claiming production readiness

### For Contributors

#### Secure Development

1. **Code Review**
   - All code changes require review
   - Security-sensitive changes should include explicit reviewer attention to auth, webhook, and persistence behavior
   - Keep automated validation green before merge

2. **Dependencies**
   - Keep dependencies up to date
   - Review dependency advisories before release
   - Treat release-candidate workflow failures as blockers for publication

3. **Testing**
   - Add tests for authentication, authorization, webhook signing, and unsafe input handling when touching those areas
   - Prefer release-artifact smoke tests over source-only validation for user-facing packages

4. **Secrets Management**
   - Never commit secrets to the repository
   - Use templates and environment variables for local setup
   - Rotate any accidentally exposed credentials immediately

## Security Implementation Status

Security-related code exists in the repository, especially around the pre-release REST API, but the project should still be treated as **pre-release with partial verification** rather than as a fully production-certified service.

### Verification Standard for Future Guarantees

Before describing any protection as supported in a stable release, verify it with:
- automated tests covering the relevant behavior
- deployment documentation showing how it is enabled
- release validation confirming the behavior in the shipped artifact
- an identified maintainer process for triage and incident handling

## Vulnerability Disclosure Policy

### Scope

This security policy applies to:

- Substrait Compliance Framework core
- REST API implementation
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

- **GitHub Security Advisories**: [github.com/substrait-io/substrait-compliance/security/advisories](https://github.com/substrait-io/substrait-compliance/security/advisories)
- **Release Notes**: Check [`CHANGELOG.md`](CHANGELOG.md) for security fixes in published releases
- **Repository Documentation**: Review [`SECURITY.md`](SECURITY.md) and release notes in the tagged version you deploy

### Update Recommendations

- **Critical**: Update immediately (within 24 hours)
- **High**: Update within 7 days
- **Medium**: Update within 30 days
- **Low**: Update at next convenient maintenance window

## Additional Resources

- [Security Hardening Guide](docs/SECURITY_HARDENING_GUIDE.md)
- [Deployment Guide](docs/DEPLOYMENT_GUIDE.md)
- [Contributing Guidelines](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [API README](api/README.md)

## Contact

For security-related questions or concerns:

- **Email**: security@substrait.io
- **GitHub**: [Security Advisories](https://github.com/substrait-io/substrait-compliance/security/advisories)

---

**Last Updated**: May 30, 2026

Thank you for helping keep the Substrait Compliance Framework and its users safe!