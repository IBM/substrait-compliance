package io.substrait.compliance.api.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for security audit logging.
 * 
 * <p>Logs security-relevant events for compliance and forensics:
 * <ul>
 *   <li>Authentication attempts (success/failure)</li>
 *   <li>Authorization failures</li>
 *   <li>Rate limit violations</li>
 *   <li>Suspicious activity</li>
 *   <li>API key usage</li>
 * </ul>
 */
@Service
@Slf4j
public class SecurityAuditService {
    
    private static final String AUDIT_LOG_PREFIX = "[SECURITY_AUDIT]";
    
    /**
     * Logs successful authentication.
     */
    public void logAuthenticationSuccess(String username, HttpServletRequest request) {
        Map<String, Object> auditData = createAuditData("AUTHENTICATION_SUCCESS", username, request);
        log.info("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs failed authentication attempt.
     */
    public void logAuthenticationFailure(String username, String reason, HttpServletRequest request) {
        Map<String, Object> auditData = createAuditData("AUTHENTICATION_FAILURE", username, request);
        auditData.put("reason", reason);
        log.warn("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs authorization failure.
     */
    public void logAuthorizationFailure(String username, String resource, String action, HttpServletRequest request) {
        Map<String, Object> auditData = createAuditData("AUTHORIZATION_FAILURE", username, request);
        auditData.put("resource", resource);
        auditData.put("action", action);
        log.warn("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs rate limit violation.
     */
    public void logRateLimitViolation(String identifier, String endpoint, HttpServletRequest request) {
        Map<String, Object> auditData = createAuditData("RATE_LIMIT_VIOLATION", identifier, request);
        auditData.put("endpoint", endpoint);
        log.warn("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs suspicious activity.
     */
    public void logSuspiciousActivity(String description, String identifier, HttpServletRequest request) {
        Map<String, Object> auditData = createAuditData("SUSPICIOUS_ACTIVITY", identifier, request);
        auditData.put("description", description);
        log.warn("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs API key usage.
     */
    public void logApiKeyUsage(String apiKeyId, String endpoint, HttpServletRequest request) {
        Map<String, Object> auditData = createAuditData("API_KEY_USAGE", apiKeyId, request);
        auditData.put("endpoint", endpoint);
        log.info("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs API key rotation.
     */
    public void logApiKeyRotation(String apiKeyId, String performedBy) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("event", "API_KEY_ROTATION");
        auditData.put("apiKeyId", apiKeyId);
        auditData.put("performedBy", performedBy);
        auditData.put("timestamp", Instant.now().toString());
        log.info("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs invalid input attempt.
     */
    public void logInvalidInput(String endpoint, String fieldName, String reason, HttpServletRequest request) {
        Map<String, Object> auditData = createAuditData("INVALID_INPUT", "anonymous", request);
        auditData.put("endpoint", endpoint);
        auditData.put("fieldName", fieldName);
        auditData.put("reason", reason);
        log.warn("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs SQL injection attempt.
     */
    public void logSqlInjectionAttempt(String input, HttpServletRequest request) {
        Map<String, Object> auditData = createAuditData("SQL_INJECTION_ATTEMPT", "anonymous", request);
        auditData.put("suspiciousInput", sanitizeForLogging(input));
        log.error("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs XSS attempt.
     */
    public void logXssAttempt(String input, HttpServletRequest request) {
        Map<String, Object> auditData = createAuditData("XSS_ATTEMPT", "anonymous", request);
        auditData.put("suspiciousInput", sanitizeForLogging(input));
        log.error("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs password change.
     */
    public void logPasswordChange(String username, boolean forced) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("event", "PASSWORD_CHANGE");
        auditData.put("username", username);
        auditData.put("forced", forced);
        auditData.put("timestamp", Instant.now().toString());
        log.info("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Logs account lockout.
     */
    public void logAccountLockout(String username, String reason) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("event", "ACCOUNT_LOCKOUT");
        auditData.put("username", username);
        auditData.put("reason", reason);
        auditData.put("timestamp", Instant.now().toString());
        log.warn("{} {}", AUDIT_LOG_PREFIX, formatAuditData(auditData));
    }
    
    /**
     * Creates base audit data structure.
     */
    private Map<String, Object> createAuditData(String event, String identifier, HttpServletRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("event", event);
        data.put("identifier", identifier);
        data.put("timestamp", Instant.now().toString());
        data.put("ipAddress", getClientIpAddress(request));
        data.put("userAgent", request.getHeader("User-Agent"));
        data.put("method", request.getMethod());
        data.put("uri", request.getRequestURI());
        return data;
    }
    
    /**
     * Extracts client IP address from request.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle multiple IPs (take first one)
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Formats audit data as JSON-like string.
     */
    private String formatAuditData(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Sanitizes input for safe logging (prevents log injection).
     */
    private String sanitizeForLogging(String input) {
        if (input == null) {
            return "null";
        }
        // Remove newlines and limit length
        String sanitized = input.replaceAll("[\r\n]", " ");
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100) + "...";
        }
        return sanitized;
    }
}

// Made with Bob