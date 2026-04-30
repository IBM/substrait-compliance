package io.substrait.compliance.api.util;

import io.substrait.compliance.api.security.SecurityAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * Utility for input validation and sanitization.
 * 
 * <p>Provides methods to validate and sanitize user input to prevent:
 * <ul>
 *   <li>SQL Injection</li>
 *   <li>XSS (Cross-Site Scripting)</li>
 *   <li>Path Traversal</li>
 *   <li>Command Injection</li>
 *   <li>LDAP Injection</li>
 * </ul>
 */
@Component
@Slf4j
public class InputValidationUtil {
    
    private final SecurityAuditService auditService;
    
    // SQL injection patterns
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('.*(--|;|/\\*|\\*/|xp_|sp_|exec|execute|select|insert|update|delete|drop|create|alter|union|script|javascript|onerror|onload).*')|" +
        "(\\b(select|insert|update|delete|drop|create|alter|exec|execute|union|script)\\b)",
        Pattern.CASE_INSENSITIVE
    );
    
    // XSS patterns
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(<script|</script|javascript:|onerror=|onload=|<iframe|</iframe|<object|</object|<embed|</embed|eval\\(|expression\\()",
        Pattern.CASE_INSENSITIVE
    );
    
    // Path traversal patterns
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        "(\\.\\./|\\.\\.\\\\|%2e%2e/|%2e%2e\\\\|\\.\\.%2f|\\.\\.%5c)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Command injection patterns
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
        "(;|\\||&|`|\\$\\(|\\$\\{|>|<|\\n|\\r)",
        Pattern.CASE_INSENSITIVE
    );
    
    // LDAP injection patterns
    private static final Pattern LDAP_INJECTION_PATTERN = Pattern.compile(
        "(\\*|\\(|\\)|\\\\|/|\\||&)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Valid alphanumeric with common symbols
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.@]+$");
    
    // Valid URL pattern
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://[a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,}(/.*)?$"
    );
    
    // Valid email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    
    public InputValidationUtil(SecurityAuditService auditService) {
        this.auditService = auditService;
    }
    
    /**
     * Validates input against SQL injection patterns.
     */
    public boolean isSqlInjection(String input, HttpServletRequest request) {
        if (input == null) {
            return false;
        }
        
        boolean isSqlInjection = SQL_INJECTION_PATTERN.matcher(input).find();
        if (isSqlInjection) {
            auditService.logSqlInjectionAttempt(input, request);
        }
        return isSqlInjection;
    }
    
    /**
     * Validates input against XSS patterns.
     */
    public boolean isXss(String input, HttpServletRequest request) {
        if (input == null) {
            return false;
        }
        
        boolean isXss = XSS_PATTERN.matcher(input).find();
        if (isXss) {
            auditService.logXssAttempt(input, request);
        }
        return isXss;
    }
    
    /**
     * Validates input against path traversal patterns.
     */
    public boolean isPathTraversal(String input) {
        if (input == null) {
            return false;
        }
        return PATH_TRAVERSAL_PATTERN.matcher(input).find();
    }
    
    /**
     * Validates input against command injection patterns.
     */
    public boolean isCommandInjection(String input) {
        if (input == null) {
            return false;
        }
        return COMMAND_INJECTION_PATTERN.matcher(input).find();
    }
    
    /**
     * Validates input against LDAP injection patterns.
     */
    public boolean isLdapInjection(String input) {
        if (input == null) {
            return false;
        }
        return LDAP_INJECTION_PATTERN.matcher(input).find();
    }
    
    /**
     * Validates if input contains only alphanumeric characters and common symbols.
     */
    public boolean isAlphanumeric(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return ALPHANUMERIC_PATTERN.matcher(input).matches();
    }
    
    /**
     * Validates if input is a valid URL.
     */
    public boolean isValidUrl(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return URL_PATTERN.matcher(input).matches();
    }
    
    /**
     * Validates if input is a valid email.
     */
    public boolean isValidEmail(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(input).matches();
    }
    
    /**
     * Sanitizes input by removing potentially dangerous characters.
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove null bytes
        String sanitized = input.replace("\0", "");
        
        // Remove control characters except newline and tab
        sanitized = sanitized.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        
        // Trim whitespace
        sanitized = sanitized.trim();
        
        return sanitized;
    }
    
    /**
     * Sanitizes HTML by escaping special characters.
     */
    public String sanitizeHtml(String input) {
        if (input == null) {
            return null;
        }
        
        return input
            .replace("&", "&")
            .replace("<", "<")
            .replace(">", ">")
            .replace("\"", """)
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;");
    }
    
    /**
     * Sanitizes SQL input by escaping single quotes.
     */
    public String sanitizeSql(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("'", "''");
    }
    
    /**
     * Validates string length.
     */
    public boolean isValidLength(String input, int minLength, int maxLength) {
        if (input == null) {
            return false;
        }
        int length = input.length();
        return length >= minLength && length <= maxLength;
    }
    
    /**
     * Validates if input matches a custom pattern.
     */
    public boolean matchesPattern(String input, Pattern pattern) {
        if (input == null || pattern == null) {
            return false;
        }
        return pattern.matcher(input).matches();
    }
    
    /**
     * Comprehensive validation that checks for all common injection attacks.
     */
    public ValidationResult validateInput(String input, HttpServletRequest request) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        
        if (input == null || input.isEmpty()) {
            result.setValid(false);
            result.setReason("Input is null or empty");
            return result;
        }
        
        if (isSqlInjection(input, request)) {
            result.setValid(false);
            result.setReason("Potential SQL injection detected");
            return result;
        }
        
        if (isXss(input, request)) {
            result.setValid(false);
            result.setReason("Potential XSS attack detected");
            return result;
        }
        
        if (isPathTraversal(input)) {
            result.setValid(false);
            result.setReason("Potential path traversal detected");
            return result;
        }
        
        if (isCommandInjection(input)) {
            result.setValid(false);
            result.setReason("Potential command injection detected");
            return result;
        }
        
        return result;
    }
    
    /**
     * Result of input validation.
     */
    public static class ValidationResult {
        private boolean valid;
        private String reason;
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}

// Made with Bob