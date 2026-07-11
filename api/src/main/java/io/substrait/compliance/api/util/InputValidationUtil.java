package io.substrait.compliance.api.util;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Utility class for input validation and sanitization.
 */
@Component
public class InputValidationUtil {
    
    private static final Pattern SAFE_STRING_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@(.+)$"
    );
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    
    /**
     * Validates if a string contains only safe characters.
     *
     * @param input the input string
     * @return true if safe, false otherwise
     */
    public boolean isSafeString(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        return SAFE_STRING_PATTERN.matcher(input).matches();
    }
    
    /**
     * Validates email format.
     *
     * @param email the email to validate
     * @return true if valid email format, false otherwise
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Validates UUID format.
     *
     * @param uuid the UUID to validate
     * @return true if valid UUID format, false otherwise
     */
    public boolean isValidUuid(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return false;
        }
        return UUID_PATTERN.matcher(uuid).matches();
    }
    
    /**
     * Sanitizes a string by removing potentially dangerous characters.
     *
     * @param input the input string
     * @return sanitized string
     */
    public String sanitizeString(String input) {
        if (input == null) {
            return null;
        }
        
        return input
            .replace("<", "")
            .replace(">", "")
            .replace(String.valueOf((char) 34), "")
            .replace("'", "")
            .replace("&", "")
            .trim();
    }
    
    /**
     * Validates pagination parameters.
     *
     * @param page the page number
     * @param size the page size
     * @return true if valid, false otherwise
     */
    public boolean isValidPagination(Integer page, Integer size) {
        if (page == null || size == null) {
            return false;
        }
        return page >= 0 && size > 0 && size <= 1000;
    }
    
    /**
     * Validates sort direction.
     *
     * @param direction the sort direction
     * @return true if valid, false otherwise
     */
    public boolean isValidSortDirection(String direction) {
        if (direction == null) {
            return false;
        }
        return "ASC".equalsIgnoreCase(direction) || "DESC".equalsIgnoreCase(direction);
    }
    
    /**
     * Validates file path to prevent directory traversal.
     *
     * @param path the file path
     * @return true if safe, false otherwise
     */
    public boolean isSafeFilePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        
        // Check for directory traversal attempts
        if (path.contains("..") || path.contains("~") || path.startsWith("/")) {
            return false;
        }
        
        // Check for null bytes
        if (path.contains("\0")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validates SQL identifier (table name, column name, etc.).
     *
     * @param identifier the SQL identifier
     * @return true if valid, false otherwise
     */
    public boolean isValidSqlIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        
        // SQL identifiers should start with letter or underscore, followed by letters, numbers, or underscores
        return identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
    
    /**
     * Validates JSON string format.
     *
     * @param json the JSON string
     * @return true if appears to be valid JSON, false otherwise
     */
    public boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = json.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
    
    /**
     * Limits string length to prevent DoS attacks.
     *
     * @param input the input string
     * @param maxLength maximum allowed length
     * @return truncated string if too long, original otherwise
     */
    public String limitStringLength(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        
        if (input.length() > maxLength) {
            return input.substring(0, maxLength);
        }
        
        return input;
    }
    
    /**
     * Validates numeric range.
     *
     * @param value the numeric value
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return true if within range, false otherwise
     */
    public boolean isInRange(Number value, double min, double max) {
        if (value == null) {
            return false;
        }
        
        double doubleValue = value.doubleValue();
        return doubleValue >= min && doubleValue <= max;
    }
    
    /**
     * Sanitizes HTML content to prevent XSS.
     *
     * @param input the HTML input
     * @return sanitized HTML
     */
    public String sanitizeHtml(String input) {
        if (input == null) {
            return null;
        }
        
        StringBuilder sanitized = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '&':
                    sanitized.append("&");
                    break;
                case '<':
                    sanitized.append("<");
                    break;
                case '>':
                    sanitized.append(">");
                    break;
                case 34:
                    sanitized.append("&#34;");
                    break;
                case '\'':
                    sanitized.append("&#x27;");
                    break;
                case '/':
                    sanitized.append("&#x2F;");
                    break;
                default:
                    sanitized.append(c);
                    break;
            }
        }
        return sanitized.toString();
    }
    
    /**
     * Validates API key format.
     *
     * @param apiKey the API key
     * @return true if valid format, false otherwise
     */
    public boolean isValidApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        
        // API keys should be at least 32 characters and contain only alphanumeric characters
        return apiKey.length() >= 32 && apiKey.matches("^[a-zA-Z0-9]+$");
    }
}

