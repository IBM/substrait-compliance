package io.substrait.compliance.api.webhook;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Map;

/**
 * Event published when a webhook notification should be sent.
 *
 * <p>This event is handled asynchronously by the webhook delivery service.
 */
@Getter
public class WebhookEvent extends ApplicationEvent {
    
    private final String eventType;
    private final Map<String, Object> payload;
    private final Instant eventTimestamp;
    
    public WebhookEvent(Object source, String eventType, Map<String, Object> payload) {
        super(source);
        this.eventType = eventType;
        this.payload = payload;
        this.eventTimestamp = Instant.now();
    }
    
    /**
     * Event types for webhook notifications.
     */
    public static class EventType {
        public static final String REPORT_SUBMITTED = "report.submitted";
        public static final String REPORT_FAILED = "report.failed";
        public static final String LEADERBOARD_UPDATED = "leaderboard.updated";
        
        private EventType() {
            // Utility class
        }
    }
}

// Made with Bob
