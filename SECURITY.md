# Security Policy

## Supported Versions

Security support commitments will be finalized at open-source launch. Until a stable public release process is in place, treat this repository as pre-release and coordinate security reports against the current default branch.

| Version | Supported |
| ------- | --------- |
| main    | Best effort, pre-release |
| tagged releases | To be defined at launch |

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

- **Initial Response**: Best effort acknowledgment as maintainers are available
- **Status Update**: Best effort follow-up after triage
- **Fix Timeline**: Depends on severity, maintainer availability, and release readiness
- **Disclosure**: Coordinated disclosure after a fix or mitigation is available

### What to Expect

1. **Acknowledgment**: We will acknowledge receipt of your vulnerability report
2. **Assessment**: We will assess the vulnerability and determine its severity
3. **Fix Development**: We will develop and test a fix
4. **Disclosure**: We will coordinate disclosure with you
5. **Credit**: We will credit you in the security advisory (unless you prefer to remain anonymous)

## Security Best Practices

### For Users

#### API Security

1. **Authentication**
   - Always use strong JWT secrets (minimum 256 bits)
   - Rotate API keys regularly (recommended: every 90 days)
   - Never commit secrets to version control
   - Use environment variables for sensitive configuration

2. **HTTPS/TLS**
   - Always use HTTPS in production environments
   - Use TLS 1.2 or higher
   - Keep SSL certificates up to date
   - Configure proper certificate validation

3. **Rate Limiting**
   - Configure appropriate rate limits for your use case
   - Monitor for unusual traffic patterns
   - Implement IP-based restrictions if needed

4. **Input Validation**
   - Enable strict input validation mode
   - Sanitize all user inputs
   - Use parameterized queries for database operations

#### Configuration Security

```yaml
# Example secure configuration (application.yml)
security:
  headers:
    enabled: true
  audit:
    enabled: true
    log-level: INFO
  validation:
    strict-mode: true

jwt:
  secret: ${JWT_SECRET}  # Use environment variable
  expiration: 3600000    # 1 hour

server:
  ssl:
    enabled: true
    key-store: ${SSL_KEYSTORE_PATH}
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
```

#### Deployment Security

1. **Environment Variables**
   - Never hardcode secrets
   - Use secure secret management (e.g., HashiCorp Vault, AWS Secrets Manager)
   - Rotate credentials regularly

2. **Network Security**
   - Use firewalls to restrict access
   - Implement network segmentation
   - Use VPCs in cloud environments

3. **Monitoring**
   - Enable security audit logging
   - Monitor for suspicious activity
   - Set up alerts for security events

### For Contributors

#### Secure Development

1. **Code Review**
   - All code changes require review
   - Security-sensitive changes require additional review
   - Use automated security scanning tools

2. **Dependencies**
   - Keep dependencies up to date
   - Review dependency security advisories
   - Use tools like Dependabot or Snyk

3. **Testing**
   - Write security tests for new features
   - Test authentication and authorization
   - Validate input handling

4. **Secrets Management**
   - Never commit secrets to the repository
   - Use `.env.example` for configuration templates
   - Scan commits for accidentally committed secrets

## Security Implementation Status

Security-related code exists in the repository, especially around the pre-release REST API, but the project should not yet be treated as having a fully verified security posture.

### Current Status

The following areas may exist partially or in pre-release form, but should be considered **implementation goals rather than verified guarantees** until they are covered by dedicated tests, deployment guidance, and release validation:

1. **Authentication & Authorization**
   - JWT-based authentication code paths may exist in the API module
   - Role and permission checks may exist in selected endpoints or services
   - Operational hardening and end-to-end verification are still required

2. **Input Validation**
   - Some validation and defensive coding patterns are present
   - Repository-wide verification for injection, traversal, and related classes of attacks is still pending

3. **Security Headers and Transport**
   - Recommended production headers and TLS settings are documented
   - Actual enforcement depends on deployment configuration and has not yet been validated as a repository-wide guarantee

4. **Rate Limiting and Audit Logging**
   - These are design goals for production deployments
   - They should not be assumed to be comprehensively implemented or enabled by default across all components

### Verification Guidance

Before describing any protection as supported in a release, verify it with:
- automated tests covering the relevant behavior
- configuration documentation showing how it is enabled
- release validation confirming the behavior in the shipped artifact

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
- **Release Notes**: Check [CHANGELOG.md](CHANGELOG.md) for security fixes in published releases
- **Mailing List**: Subscribe to security announcements (coming soon, if adopted at launch)

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

## Contact

For security-related questions or concerns:

- **Email**: security@substrait.io
- **GitHub**: [Security Advisories](https://github.com/substrait-io/substrait-compliance/security/advisories)

---

**Last Updated**: May 22, 2026

Thank you for helping keep the Substrait Compliance Framework and its users safe!