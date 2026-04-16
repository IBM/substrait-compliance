package io.substrait.compliance.api.webhook;

import io.substrait.compliance.api.model.entity.ReportEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Publisher for webhook events.
 * 
 * <p>This component publishes events that trigger webhook notifications.
 * Events are handled asynchronously by the webhook delivery service.
 */
@Component
@Slf4j
public class WebhookPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public WebhookPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Publishes a report submitted event.
     */
    public void publishReportSubmitted(ReportEntity report) {
        log.debug("Publishing report.submitted event for report ID: {}", report.getId());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("reportId", report.getId());
        payload.put("engineName", report.getEngine().getName());
        payload.put("engineVersion", report.getEngine().getVersion());
        payload.put("testSuiteName", report.getTestSuiteName());
        payload.put("complianceScore", report.getComplianceScore());
        payload.put("passed", report.getPassedCount());
        payload.put("failed", report.getFailedCount());
        payload.put("skipped", report.getSkippedCount());
        payload.put("timestamp", report.getTimestamp());
        
        WebhookEvent event = new WebhookEvent(this, WebhookEvent.EventType.REPORT_SUBMITTED, payload);
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publishes a report failed event (low compliance score).
     */
    public void publishReportFailed(ReportEntity report) {
        log.debug("Publishing report.failed event for report ID: {}", report.getId());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("reportId", report.getId());
        payload.put("engineName", report.getEngine().getName());
        payload.put("engineVersion", report.getEngine().getVersion());
        payload.put("testSuiteName", report.getTestSuiteName());
        payload.put("complianceScore", report.getComplianceScore());
        payload.put("failedTests", report.getFailedCount());
        
        WebhookEvent event = new WebhookEvent(this, WebhookEvent.EventType.REPORT_FAILED, payload);
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publishes a leaderboard updated event.
     */
    public void publishLeaderboardUpdated() {
        log.debug("Publishing leaderboard.updated event");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", "Compliance leaderboard has been updated");
        payload.put("timestamp", System.currentTimeMillis());
        
        WebhookEvent event = new WebhookEvent(this, WebhookEvent.EventType.LEADERBOARD_UPDATED, payload);
        eventPublisher.publishEvent(event);
    }
}

// Made with Bob
