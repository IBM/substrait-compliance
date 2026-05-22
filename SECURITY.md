# Security Policy

## Supported Versions

We release patches for security vulnerabilities in the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |
| < 1.0   | :x:                |

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

- **Initial Response**: Within 48 hours of report submission
- **Status Update**: Within 7 days with an assessment of the report
- **Fix Timeline**: Critical vulnerabilities will be addressed within 30 days
- **Disclosure**: Coordinated disclosure after patch is available

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

## Security Features

### Implemented Protections

1. **Authentication & Authorization**
   - JWT-based authentication
   - Role-based access control (RBAC)
   - API key management with rotation
   - Session management (stateless)

2. **Input Validation**
   - SQL injection prevention
   - XSS (Cross-Site Scripting) protection
   - Path traversal prevention
   - Command injection prevention
   - LDAP injection prevention

3. **Security Headers**
   - `X-Content-Type-Options: nosniff`
   - `X-Frame-Options: DENY`
   - `X-XSS-Protection: 1; mode=block`
   - `Strict-Transport-Security` (HSTS)
   - `Content-Security-Policy` (CSP)
   - `Referrer-Policy: strict-origin-when-cross-origin`
   - `Permissions-Policy`

4. **Rate Limiting**
   - Configurable rate limits per endpoint
   - IP-based throttling
   - Burst protection

5. **Audit Logging**
   - Authentication events
   - Authorization failures
   - Rate limit violations
   - Suspicious activity detection
   - API key usage tracking

### Security Audit Events

The following events are logged for security monitoring:

- Authentication success/failure
- Authorization failures
- Rate limit violations
- Invalid input attempts
- SQL injection attempts
- XSS attempts
- Password changes
- Account lockouts
- API key creation/rotation/deletion
- Suspicious activity patterns

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
- **Release Notes**: Check [CHANGELOG.md](CHANGELOG.md) for security fixes
- **Mailing List**: Subscribe to security announcements (coming soon)

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