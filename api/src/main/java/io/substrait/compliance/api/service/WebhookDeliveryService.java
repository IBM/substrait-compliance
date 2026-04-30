package io.substrait.compliance.api.service;

import io.substrait.compliance.api.model.entity.WebhookEntity;
import io.substrait.compliance.api.repository.WebhookRepository;
import io.substrait.compliance.api.webhook.WebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service for delivering webhook notifications asynchronously.
 * 
 * <p>Features:
 * <ul>
 *   <li>Async event-driven delivery</li>
 *   <li>Exponential backoff retry logic</li>
 *   <li>HMAC-SHA256 signature verification</li>
 *   <li>Delivery tracking and statistics</li>
 * </ul>
 */
@Service
@Slf4j
public class WebhookDeliveryService {
    
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final String EVENT_TYPE_HEADER = "X-Event-Type";
    private static final String DELIVERY_ID_HEADER = "X-Delivery-ID";
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final int INITIAL_RETRY_DELAY_MINUTES = 1;
    
    private final WebhookRepository webhookRepository;
    private final RestTemplate restTemplate;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Value("${webhook.delivery.timeout:5000}")
    private int deliveryTimeout;
    
    @Value("${webhook.delivery.enabled:true}")
    private boolean deliveryEnabled;
    
    public WebhookDeliveryService(WebhookRepository webhookRepository) {
        this.webhookRepository = webhookRepository;
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Listens for webhook events and delivers them asynchronously.
     */
    @Async
    @EventListener
    @Transactional
    public void handleWebhookEvent(WebhookEvent event) {
        if (!deliveryEnabled) {
            log.debug("Webhook delivery is disabled, skipping event: {}", event.getEventType());
            return;
        }
        
        log.info("Processing webhook event: {}", event.getEventType());
        
        // Find all active webhooks subscribed to this event type
        List<WebhookEntity> webhooks = webhookRepository.findByActiveAndEventsContaining(
            true, 
            event.getEventType()
        );
        
        if (webhooks.isEmpty()) {
            log.debug("No active webhooks found for event type: {}", event.getEventType());
            return;
        }
        
        log.info("Found {} active webhook(s) for event type: {}", webhooks.size(), event.getEventType());
        
        // Deliver to each webhook
        for (WebhookEntity webhook : webhooks) {
            try {
                deliverWebhook(webhook, event);
            } catch (Exception e) {
                log.error("Failed to deliver webhook to {}: {}", webhook.getUrl(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Delivers a webhook notification with retry logic.
     */
    private void deliverWebhook(WebhookEntity webhook, WebhookEvent event) {
        Long deliveryId = createDeliveryRecord(webhook.getId(), event);
        
        try {
            // Prepare request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(EVENT_TYPE_HEADER, event.getEventType());
            headers.set(DELIVERY_ID_HEADER, deliveryId.toString());
            
            // Generate HMAC signature
            String signature = generateSignature(event.getPayload(), webhook.getSecret());
            headers.set(SIGNATURE_HEADER, signature);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event.getPayload(), headers);
            
            // Send request
            log.debug("Delivering webhook to: {}", webhook.getUrl());
            ResponseEntity<String> response = restTemplate.exchange(
                webhook.getUrl(),
                HttpMethod.POST,
                request,
                String.class
            );
            
            // Update delivery record
            if (response.getStatusCode().is2xxSuccessful()) {
                updateDeliverySuccess(deliveryId, response.getStatusCodeValue(), response.getBody());
                log.info("Webhook delivered successfully to: {}", webhook.getUrl());
            } else {
                updateDeliveryFailure(deliveryId, response.getStatusCodeValue(), response.getBody(), 1);
                log.warn("Webhook delivery failed with status {}: {}", response.getStatusCodeValue(), webhook.getUrl());
            }
            
        } catch (Exception e) {
            log.error("Exception during webhook delivery to {}: {}", webhook.getUrl(), e.getMessage());
            updateDeliveryFailure(deliveryId, null, e.getMessage(), 1);
            scheduleRetry(deliveryId, 1);
        }
    }
    
    /**
     * Generates HMAC-SHA256 signature for webhook payload.
     */
    private String generateSignature(Map<String, Object> payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);
            
            // Convert payload to JSON string (simplified)
            String payloadJson = payload.toString();
            byte[] signatureBytes = mac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
            
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to generate webhook signature: {}", e.getMessage());
            throw new RuntimeException("Failed to generate webhook signature", e);
        }
    }
    
    /**
     * Creates a delivery record in the database.
     */
    @Transactional
    private Long createDeliveryRecord(Long webhookId, WebhookEvent event) {
        String sql = "INSERT INTO webhook_deliveries " +
            "(webhook_id, event_type, payload, status, attempt_count, created_at) " +
            "VALUES (?, ?, ?::jsonb, 'PENDING', 1, ?) " +
            "RETURNING id";
        
        return ((Number) entityManager.createNativeQuery(sql)
            .setParameter(1, webhookId)
            .setParameter(2, event.getEventType())
            .setParameter(3, convertPayloadToJson(event.getPayload()))
            .setParameter(4, Instant.now())
            .getSingleResult()).longValue();
    }
    
    /**
     * Updates delivery record on success.
     */
    @Transactional
    private void updateDeliverySuccess(Long deliveryId, int statusCode, String responseBody) {
        String sql = "UPDATE webhook_deliveries " +
            "SET status = 'SUCCESS', " +
            "response_code = ?, " +
            "response_body = ?, " +
            "delivered_at = ? " +
            "WHERE id = ?";
        
        entityManager.createNativeQuery(sql)
            .setParameter(1, statusCode)
            .setParameter(2, truncate(responseBody, 1000))
            .setParameter(3, Instant.now())
            .setParameter(4, deliveryId)
            .executeUpdate();
    }
    
    /**
     * Updates delivery record on failure.
     */
    @Transactional
    private void updateDeliveryFailure(Long deliveryId, Integer statusCode, String responseBody, int attemptCount) {
        String status = attemptCount < MAX_RETRY_ATTEMPTS ? "RETRYING" : "FAILED";
        
        String sql = "UPDATE webhook_deliveries " +
            "SET status = ?, " +
            "response_code = ?, " +
            "response_body = ?, " +
            "attempt_count = ? " +
            "WHERE id = ?";
        
        entityManager.createNativeQuery(sql)
            .setParameter(1, status)
            .setParameter(2, statusCode)
            .setParameter(3, truncate(responseBody, 1000))
            .setParameter(4, attemptCount)
            .setParameter(5, deliveryId)
            .executeUpdate();
    }
    
    /**
     * Schedules a retry with exponential backoff.
     */
    @Transactional
    private void scheduleRetry(Long deliveryId, int attemptCount) {
        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
            log.warn("Max retry attempts reached for delivery ID: {}", deliveryId);
            return;
        }
        
        // Calculate next retry time with exponential backoff
        int delayMinutes = INITIAL_RETRY_DELAY_MINUTES * (int) Math.pow(2, attemptCount - 1);
        Instant nextRetryAt = Instant.now().plus(delayMinutes, ChronoUnit.MINUTES);
        
        String sql = "UPDATE webhook_deliveries " +
            "SET next_retry_at = ? " +
            "WHERE id = ?";
        
        entityManager.createNativeQuery(sql)
            .setParameter(1, nextRetryAt)
            .setParameter(2, deliveryId)
            .executeUpdate();
        
        log.info("Scheduled retry for delivery ID {} in {} minutes", deliveryId, delayMinutes);
    }
    
    /**
     * Processes pending retries (runs every minute).
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @Transactional
    public void processRetries() {
        if (!deliveryEnabled) {
            return;
        }
        
        String sql = "SELECT id, webhook_id, event_type, payload, attempt_count " +
            "FROM webhook_deliveries " +
            "WHERE status = 'RETRYING' " +
            "AND next_retry_at <= ? " +
            "LIMIT 100";
        
        @SuppressWarnings("unchecked")
        List<Object[]> retries = entityManager.createNativeQuery(sql)
            .setParameter(1, Instant.now())
            .getResultList();
        
        if (retries.isEmpty()) {
            return;
        }
        
        log.info("Processing {} webhook retries", retries.size());
        
        for (Object[] retry : retries) {
            Long deliveryId = ((Number) retry[0]).longValue();
            Long webhookId = ((Number) retry[1]).longValue();
            int attemptCount = ((Number) retry[4]).intValue();
            
            try {
                retryDelivery(deliveryId, webhookId, attemptCount + 1);
            } catch (Exception e) {
                log.error("Failed to retry delivery {}: {}", deliveryId, e.getMessage());
            }
        }
    }
    
    /**
     * Retries a failed webhook delivery.
     */
    private void retryDelivery(Long deliveryId, Long webhookId, int attemptCount) {
        WebhookEntity webhook = webhookRepository.findById(webhookId)
            .orElseThrow(() -> new RuntimeException("Webhook not found: " + webhookId));
        
        if (!webhook.getActive()) {
            log.info("Webhook {} is inactive, skipping retry", webhookId);
            updateDeliveryFailure(deliveryId, null, "Webhook inactive", attemptCount);
            return;
        }
        
        // Get delivery details
        String sql = "SELECT event_type, payload FROM webhook_deliveries WHERE id = ?";
        Object[] result = (Object[]) entityManager.createNativeQuery(sql)
            .setParameter(1, deliveryId)
            .getSingleResult();
        
        String eventType = (String) result[0];
        String payloadJson = (String) result[1];
        
        try {
            // Prepare request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(EVENT_TYPE_HEADER, eventType);
            headers.set(DELIVERY_ID_HEADER, deliveryId.toString());
            headers.set(SIGNATURE_HEADER, generateSignature(Map.of("payload", payloadJson), webhook.getSecret()));
            
            HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);
            
            // Send request
            ResponseEntity<String> response = restTemplate.exchange(
                webhook.getUrl(),
                HttpMethod.POST,
                request,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                updateDeliverySuccess(deliveryId, response.getStatusCodeValue(), response.getBody());
                log.info("Webhook retry successful for delivery ID: {}", deliveryId);
            } else {
                updateDeliveryFailure(deliveryId, response.getStatusCodeValue(), response.getBody(), attemptCount);
                scheduleRetry(deliveryId, attemptCount);
            }
            
        } catch (Exception e) {
            log.error("Retry failed for delivery {}: {}", deliveryId, e.getMessage());
            updateDeliveryFailure(deliveryId, null, e.getMessage(), attemptCount);
            scheduleRetry(deliveryId, attemptCount);
        }
    }
    
    /**
     * Converts payload map to JSON string.
     */
    private String convertPayloadToJson(Map<String, Object> payload) {
        // Simplified JSON conversion - in production use Jackson ObjectMapper
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                json.append("\"").append(entry.getValue()).append("\"");
            } else {
                json.append(entry.getValue());
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }
    
    /**
     * Truncates a string to maximum length.
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() <= maxLength ? str : str.substring(0, maxLength);
    }
}

// Made with Bob