package io.substrait.compliance.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for webhook data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {
    
    private Long id;
    private String url;
    private List<String> events;
    private Boolean active;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}

// Made with Bob