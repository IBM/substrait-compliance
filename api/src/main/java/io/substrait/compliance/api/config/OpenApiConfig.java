package io.substrait.compliance.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for the Compliance API.
 * 
 * <p>This configuration provides:
 * <ul>
 *   <li>API metadata (title, version, description)</li>
 *   <li>Security scheme (JWT Bearer token)</li>
 *   <li>Server information</li>
 *   <li>Contact and license information</li>
 * </ul>
 * 
 * <p>Access Swagger UI at: http://localhost:8080/swagger-ui.html
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Substrait Compliance API",
        version = "1.0.0",
        description = "REST API for Substrait compliance testing results. " +
                     "This API enables programmatic submission of compliance reports, " +
                     "querying of compliance data, and webhook notifications.",
        contact = @Contact(
            name = "Substrait Compliance Team",
            email = "compliance@substrait.io",
            url = "https://github.com/IBM/substrait-compliance"
        ),
        license = @License(
            name = "Apache 2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0"
        )
    ),
    servers = {
        @Server(
            url = "http://localhost:8080",
            description = "Development server"
        ),
        @Server(
            url = "https://api.substrait.io",
            description = "Production server"
        )
    }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT authentication. Obtain a token from /api/v1/auth/login and include it in the Authorization header as 'Bearer {token}'"
)
public class OpenApiConfig {
    // Configuration is done via annotations
}

