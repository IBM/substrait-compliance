package io.substrait.compliance.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import java.util.List;

/**
 * Request DTO for webhook creation/update.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookRequest {
    
    @NotBlank(message = "URL is required")
    @Pattern(regexp = "^https?://.*", message = "URL must start with http:// or https://")
    private String url;
    
    @NotEmpty(message = "At least one event type is required")
    private List<String> events;
    
    private Boolean active;
}

// Made with Bob