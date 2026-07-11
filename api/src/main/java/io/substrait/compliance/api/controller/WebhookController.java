package io.substrait.compliance.api.controller;

import io.substrait.compliance.api.model.dto.WebhookRequest;
import io.substrait.compliance.api.model.dto.WebhookResponse;
import io.substrait.compliance.api.model.entity.WebhookEntity;
import io.substrait.compliance.api.repository.WebhookRepository;
import io.substrait.compliance.api.security.SecurityAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for webhook management.
 * 
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Creating webhooks</li>
 *   <li>Listing webhooks</li>
 *   <li>Updating webhooks</li>
 *   <li>Deleting webhooks</li>
 *   <li>Viewing webhook statistics</li>
 *   <li>Viewing delivery history</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Webhook management endpoints")
@Slf4j
public class WebhookController {
    
    private final WebhookRepository webhookRepository;
    private final SecurityAuditService auditService;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public WebhookController(WebhookRepository webhookRepository, SecurityAuditService auditService) {
        this.webhookRepository = webhookRepository;
        this.auditService = auditService;
    }
    
    /**
     * Creates a new webhook.
     */
    @PostMapping
    @Operation(summary = "Create webhook", description = "Register a new webhook for event notifications")
    public ResponseEntity<WebhookResponse> createWebhook(
            @Valid @RequestBody WebhookRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        log.info("Creating webhook for URL: {}", request.getUrl());
        
        String username = authentication != null ? authentication.getName() : "anonymous";
        
        // Create webhook entity
        WebhookEntity webhook = WebhookEntity.builder()
            .url(request.getUrl())
            .secret(generateSecret())
            .events(request.getEvents())
            .active(request.getActive() != null ? request.getActive() : true)
            .createdBy(username)
            .build();
        
        webhook = webhookRepository.save(webhook);
        
        auditService.logApiKeyUsage(webhook.getId().toString(), "/api/v1/webhooks", httpRequest);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(webhook));
    }
    
    /**
     * Lists all webhooks.
     */
    @GetMapping
    @Operation(summary = "List webhooks", description = "Get all registered webhooks with pagination")
    public ResponseEntity<Map<String, Object>> listWebhooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            Authentication authentication) {
        
        String username = authentication != null ? authentication.getName() : "anonymous";
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
            Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<WebhookEntity> webhookPage;
        if (authentication != null && !hasAdminRole(authentication)) {
            // Non-admin users see only their own webhooks
            webhookPage = webhookRepository.findAll(pageable);
        } else {
            webhookPage = webhookRepository.findAll(pageable);
        }
        
        List<WebhookResponse> webhooks = webhookPage.getContent().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("webhooks", webhooks);
        response.put("currentPage", webhookPage.getNumber());
        response.put("totalItems", webhookPage.getTotalElements());
        response.put("totalPages", webhookPage.getTotalPages());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets a specific webhook by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get webhook", description = "Get webhook details by ID")
    public ResponseEntity<WebhookResponse> getWebhook(@PathVariable Long id) {
        return webhookRepository.findById(id)
            .map(webhook -> ResponseEntity.ok(toResponse(webhook)))
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Updates a webhook.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update webhook", description = "Update webhook configuration")
    public ResponseEntity<WebhookResponse> updateWebhook(
            @PathVariable Long id,
            @Valid @RequestBody WebhookRequest request,
            Authentication authentication) {
        
        return webhookRepository.findById(id)
            .map(webhook -> {
                if (request.getUrl() != null) {
                    webhook.setUrl(request.getUrl());
                }
                if (request.getEvents() != null) {
                    webhook.setEvents(request.getEvents());
                }
                if (request.getActive() != null) {
                    webhook.setActive(request.getActive());
                }
                
                webhook = webhookRepository.save(webhook);
                log.info("Updated webhook ID: {}", id);
                
                return ResponseEntity.ok(toResponse(webhook));
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Deletes a webhook.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete webhook", description = "Delete a webhook")
    public ResponseEntity<Void> deleteWebhook(@PathVariable Long id, Authentication authentication) {
        return webhookRepository.findById(id)
            .map(webhook -> {
                webhookRepository.delete(webhook);
                log.info("Deleted webhook ID: {}", id);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Gets webhook statistics.
     */
    @GetMapping("/{id}/stats")
    @Operation(summary = "Get webhook statistics", description = "Get delivery statistics for a webhook")
    public ResponseEntity<Map<String, Object>> getWebhookStats(@PathVariable Long id) {
        String sql = "SELECT " +
            "COUNT(*) as total_deliveries, " +
            "COUNT(CASE WHEN status = 'SUCCESS' THEN 1 END) as successful, " +
            "COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed, " +
            "COUNT(CASE WHEN status = 'RETRYING' THEN 1 END) as retrying, " +
            "MAX(created_at) as last_delivery " +
            "FROM webhook_deliveries WHERE webhook_id = ?";
        
        Object[] result = (Object[]) entityManager.createNativeQuery(sql)
            .setParameter(1, id)
            .getSingleResult();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("webhookId", id);
        stats.put("totalDeliveries", ((Number) result[0]).longValue());
        stats.put("successfulDeliveries", ((Number) result[1]).longValue());
        stats.put("failedDeliveries", ((Number) result[2]).longValue());
        stats.put("retryingDeliveries", ((Number) result[3]).longValue());
        stats.put("lastDeliveryAt", result[4]);
        
        long total = ((Number) result[0]).longValue();
        long successful = ((Number) result[1]).longValue();
        double successRate = total > 0 ? (successful * 100.0 / total) : 0.0;
        stats.put("successRate", String.format("%.2f", successRate));
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Gets webhook delivery history.
     */
    @GetMapping("/{id}/deliveries")
    @Operation(summary = "Get delivery history", description = "Get delivery history for a webhook")
    public ResponseEntity<Map<String, Object>> getDeliveryHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        String sql = "SELECT id, event_type, status, response_code, attempt_count, " +
            "delivered_at, created_at FROM webhook_deliveries " +
            "WHERE webhook_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        
        @SuppressWarnings("unchecked")
        List<Object[]> deliveries = entityManager.createNativeQuery(sql)
            .setParameter(1, id)
            .setParameter(2, size)
            .setParameter(3, page * size)
            .getResultList();
        
        List<Map<String, Object>> deliveryList = deliveries.stream()
            .map(row -> {
                Map<String, Object> delivery = new HashMap<>();
                delivery.put("id", row[0]);
                delivery.put("eventType", row[1]);
                delivery.put("status", row[2]);
                delivery.put("responseCode", row[3]);
                delivery.put("attemptCount", row[4]);
                delivery.put("deliveredAt", row[5]);
                delivery.put("createdAt", row[6]);
                return delivery;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("deliveries", deliveryList);
        response.put("currentPage", page);
        response.put("pageSize", size);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Converts entity to response DTO.
     */
    private WebhookResponse toResponse(WebhookEntity webhook) {
        return WebhookResponse.builder()
            .id(webhook.getId())
            .url(webhook.getUrl())
            .events(webhook.getEvents())
            .active(webhook.getActive())
            .createdBy(webhook.getCreatedBy())
            .createdAt(webhook.getCreatedAt())
            .updatedAt(webhook.getUpdatedAt())
            .build();
    }
    
    /**
     * Generates a secure random secret for webhook signing.
     */
    private String generateSecret() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Checks if user has admin role.
     */
    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }
}

