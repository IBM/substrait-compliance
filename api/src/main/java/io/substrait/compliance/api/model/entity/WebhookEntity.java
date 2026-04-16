package io.substrait.compliance.api.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a registered webhook.
 * 
 * <p>Webhooks receive real-time notifications for compliance events.
 */
@Entity
@Table(name = "webhooks", indexes = {
    @Index(name = "idx_webhooks_active", columnList = "active"),
    @Index(name = "idx_webhooks_created_by", columnList = "created_by")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 2048)
    private String url;
    
    @Column(nullable = false)
    private String secret;
    
    @ElementCollection
    @CollectionTable(name = "webhook_events", joinColumns = @JoinColumn(name = "webhook_id"))
    @Column(name = "event_type")
    @Builder.Default
    private List<String> events = new ArrayList<>();
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * Checks if this webhook is subscribed to a specific event type.
     */
    public boolean isSubscribedTo(String eventType) {
        return events.contains(eventType);
    }
}

// Made with Bob
