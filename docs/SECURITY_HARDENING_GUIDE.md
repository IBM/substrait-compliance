# Security Hardening Guide

## Overview

This guide covers security hardening measures implemented in the Substrait Compliance API.

## Implemented Security Features

### 1. Security Headers (SecurityHeadersConfig.java)

**Headers Added:**
- `X-Content-Type-Options: nosniff` - Prevents MIME sniffing
- `X-Frame-Options: DENY` - Prevents clickjacking
- `X-XSS-Protection: 1; mode=block` - Enables XSS filtering
- `Strict-Transport-Security` - Enforces HTTPS (production only)
- `Content-Security-Policy` - Restricts resource loading
- `Referrer-Policy` - Controls referrer information
- `Permissions-Policy` - Controls browser features

### 2. Security Audit Logging (SecurityAuditService.java)

**Events Logged:**
- Authentication success/failure
- Authorization failures
- Rate limit violations
- Suspicious activity
- API key usage and rotation
- Invalid input attempts
- SQL injection attempts
- XSS attempts
- Password changes
- Account lockouts

**Log Format:**
```
[SECURITY_AUDIT] {event=AUTHENTICATION_FAILURE, identifier=user@example.com, 
timestamp=2026-04-30T13:00:00Z, ipAddress=192.168.1.1, userAgent=..., 
method=POST, uri=/api/v1/auth/login, reason=Invalid credentials}
```

### 3. Input Validation (InputValidationUtil.java)

**Protections Against:**
- SQL Injection
- XSS (Cross-Site Scripting)
- Path Traversal
- Command Injection
- LDAP Injection

**Validation Methods:**
- `isSqlInjection()` - Detects SQL injection patterns
- `isXss()` - Detects XSS patterns
- `isPathTraversal()` - Detects path traversal
- `isCommandInjection()` - Detects command injection
- `validateInput()` - Comprehensive validation

**Sanitization Methods:**
- `sanitize()` - General sanitization
- `sanitizeHtml()` - HTML entity encoding
- `sanitizeSql()` - SQL escaping

## Configuration

### Enable Security Features

**application.yml:**
```yaml
security:
  headers:
    enabled: true
  audit:
    enabled: true
    log-level: INFO
  validation:
    strict-mode: true
```

### HTTPS Configuration

**Production (required):**
```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

## Best Practices

### 1. Authentication
- Use strong JWT secrets (min 256 bits)
- Rotate secrets regularly
- Set appropriate token expiration
- Implement refresh tokens

### 2. Authorization
- Use role-based access control (RBAC)
- Apply principle of least privilege
- Audit authorization failures

### 3. Rate Limiting
- Configure per-endpoint limits
- Monitor rate limit violations
- Implement progressive delays

### 4. Input Validation
- Validate all user input
- Sanitize before storage
- Use parameterized queries
- Implement whitelist validation

### 5. Logging
- Log all security events
- Protect logs from tampering
- Implement log rotation
- Monitor for suspicious patterns

### 6. API Keys
- Generate cryptographically secure keys
- Hash keys before storage
- Implement key rotation
- Revoke compromised keys immediately

## Security Checklist

- [ ] HTTPS enabled in production
- [ ] Strong JWT secret configured
- [ ] Rate limiting enabled
- [ ] Security headers configured
- [ ] Audit logging enabled
- [ ] Input validation on all endpoints
- [ ] SQL injection protection
- [ ] XSS protection
- [ ] CSRF protection
- [ ] API keys properly secured
- [ ] Regular security audits scheduled
- [ ] Dependency scanning enabled
- [ ] Container security scanning
- [ ] Secrets in environment variables
- [ ] Database credentials encrypted

## Monitoring

### Security Metrics to Track
- Failed authentication attempts
- Rate limit violations
- Input validation failures
- Suspicious activity patterns
- API key usage patterns

### Alerting Rules
- Multiple failed logins from same IP
- Unusual API usage patterns
- Repeated input validation failures
- Unauthorized access attempts

## Incident Response

### Steps:
1. **Detect** - Monitor security logs
2. **Contain** - Disable compromised accounts/keys
3. **Investigate** - Review audit logs
4. **Remediate** - Fix vulnerabilities
5. **Document** - Record incident details
6. **Review** - Update security measures

## References

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [OWASP API Security](https://owasp.org/www-project-api-security/)
- [Spring Security](https://spring.io/projects/spring-security)

---

**Version**: 1.0.0  
**Last Updated**: 2026-04-30  
**Status**: Production Ready ✅