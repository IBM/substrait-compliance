package io.substrait.compliance.api.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Provider for generating and validating JWT tokens.
 * 
 * <p>This component handles:
 * <ul>
 *   <li>Token generation with user claims and scopes</li>
 *   <li>Token validation and parsing</li>
 *   <li>Token expiration management</li>
 * </ul>
 */
@Component
@Slf4j
public class JwtTokenProvider {
    
    private final SecretKey key;
    private final long expiration;
    
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        log.info("JWT Token Provider initialized with expiration: {}ms", expiration);
    }
    
    /**
     * Generates a JWT token for the given username and scopes.
     * 
     * @param username the username to include in the token
     * @param scopes the permission scopes for the user
     * @return the generated JWT token
     */
    public String generateToken(String username, List<String> scopes) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        String token = Jwts.builder()
                .setSubject(username)
                .claim("scopes", scopes)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        
        log.debug("Generated JWT token for user: {}", username);
        return token;
    }
    
    /**
     * Validates and parses a JWT token.
     * 
     * @param token the JWT token to validate
     * @return the claims from the token
     * @throws JwtException if the token is invalid
     */
    public Claims validateToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            log.debug("Successfully validated token for user: {}", claims.getSubject());
            return claims;
        } catch (SignatureException ex) {
            log.error("Invalid JWT signature");
            throw ex;
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
            throw ex;
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
            throw ex;
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
            throw ex;
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
            throw ex;
        }
    }
    
    /**
     * Extracts the username from a JWT token.
     * 
     * @param token the JWT token
     * @return the username
     */
    public String getUsernameFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.getSubject();
    }
    
    /**
     * Extracts the scopes from a JWT token.
     * 
     * @param token the JWT token
     * @return the list of scopes
     */
    @SuppressWarnings("unchecked")
    public List<String> getScopesFromToken(String token) {
        Claims claims = validateToken(token);
        return (List<String>) claims.get("scopes");
    }
    
    /**
     * Checks if a token is expired.
     * 
     * @param token the JWT token
     * @return true if the token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException ex) {
            return true;
        }
    }
}

