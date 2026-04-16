package io.substrait.compliance.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Compliance API.
 * 
 * <p>This configuration:
 * <ul>
 *   <li>Disables CSRF (stateless API)</li>
 *   <li>Configures stateless session management</li>
 *   <li>Sets up JWT authentication filter</li>
 *   <li>Defines authorization rules for endpoints</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless API
            .csrf().disable()
            
            // Stateless session management
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            
            // Authorization rules
            .authorizeRequests()
                // Public endpoints
                .antMatchers("/api/v1/auth/**").permitAll()
                .antMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .antMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                .antMatchers("/v3/api-docs/**", "/api-docs/**").permitAll()
                
                // Read-only endpoints (require report:read scope)
                .antMatchers(HttpMethod.GET, "/api/v1/reports/**").hasAuthority("report:read")
                .antMatchers(HttpMethod.GET, "/api/v1/engines/**").hasAuthority("report:read")
                .antMatchers(HttpMethod.GET, "/api/v1/leaderboard").hasAuthority("report:read")
                .antMatchers(HttpMethod.GET, "/api/v1/statistics").hasAuthority("report:read")
                
                // Write endpoints (require report:write scope)
                .antMatchers(HttpMethod.POST, "/api/v1/reports/**").hasAuthority("report:write")
                
                // Webhook management (require webhook:manage scope)
                .antMatchers("/api/v1/webhooks/**").hasAuthority("webhook:manage")
                
                // Admin endpoints
                .antMatchers("/actuator/**").hasAuthority("admin")
                
                // All other requests must be authenticated
                .anyRequest().authenticated()
            .and()
            
            // Add JWT filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

// Made with Bob
