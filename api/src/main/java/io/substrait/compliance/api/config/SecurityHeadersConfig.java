package io.substrait.compliance.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Configuration for security headers.
 * 
 * <p>Adds security headers to all HTTP responses to protect against common vulnerabilities:
 * <ul>
 *   <li>X-Content-Type-Options: Prevents MIME type sniffing</li>
 *   <li>X-Frame-Options: Prevents clickjacking</li>
 *   <li>X-XSS-Protection: Enables XSS filtering</li>
 *   <li>Strict-Transport-Security: Enforces HTTPS</li>
 *   <li>Content-Security-Policy: Restricts resource loading</li>
 *   <li>Referrer-Policy: Controls referrer information</li>
 *   <li>Permissions-Policy: Controls browser features</li>
 * </ul>
 */
@Configuration
public class SecurityHeadersConfig {
    
    @Bean
    public SecurityHeadersFilter securityHeadersFilter() {
        return new SecurityHeadersFilter();
    }
    
    /**
     * Filter that adds security headers to all responses.
     */
    public static class SecurityHeadersFilter extends OncePerRequestFilter {
        
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            
            // Prevent MIME type sniffing
            response.setHeader("X-Content-Type-Options", "nosniff");
            
            // Prevent clickjacking
            response.setHeader("X-Frame-Options", "DENY");
            
            // Enable XSS protection
            response.setHeader("X-XSS-Protection", "1; mode=block");
            
            // Enforce HTTPS (only in production)
            if (isProduction(request)) {
                response.setHeader("Strict-Transport-Security", 
                    "max-age=31536000; includeSubDomains; preload");
            }
            
            // Content Security Policy
            response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "font-src 'self' data:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'");
            
            // Referrer policy
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            
            // Permissions policy (formerly Feature-Policy)
            response.setHeader("Permissions-Policy",
                "geolocation=(), " +
                "microphone=(), " +
                "camera=(), " +
                "payment=(), " +
                "usb=(), " +
                "magnetometer=(), " +
                "gyroscope=(), " +
                "accelerometer=()");
            
            // Cache control for sensitive endpoints
            if (isSensitiveEndpoint(request)) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
            }
            
            filterChain.doFilter(request, response);
        }
        
        /**
         * Checks if the request is in production environment.
         */
        private boolean isProduction(HttpServletRequest request) {
            String scheme = request.getScheme();
            return "https".equalsIgnoreCase(scheme);
        }
        
        /**
         * Checks if the endpoint contains sensitive data.
         */
        private boolean isSensitiveEndpoint(HttpServletRequest request) {
            String path = request.getRequestURI();
            return path.contains("/api/") && 
                   (path.contains("/reports") || 
                    path.contains("/webhooks") || 
                    path.contains("/auth"));
        }
    }
}

