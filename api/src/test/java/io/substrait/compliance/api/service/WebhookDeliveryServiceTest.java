package io.substrait.compliance.api.service;

import io.substrait.compliance.api.model.entity.WebhookEntity;
import io.substrait.compliance.api.repository.WebhookRepository;
import io.substrait.compliance.api.webhook.WebhookEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebhookDeliveryService.
 */
@ExtendWith(MockitoExtension.class)
class WebhookDeliveryServiceTest {
    
    @Mock
    private WebhookRepository webhookRepository;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    
    @Mock
    private EntityManager entityManager;
    
    @Mock
    private Query query;
    
    private WebhookDeliveryService webhookDeliveryService;
    
    @BeforeEach
    void setUp() {
        webhookDeliveryService = new WebhookDeliveryService(webhookRepository, objectMapper);
        ReflectionTestUtils.setField(webhookDeliveryService, "entityManager", entityManager);
        ReflectionTestUtils.setField(webhookDeliveryService, "deliveryEnabled", true);
        ReflectionTestUtils.setField(webhookDeliveryService, "deliveryTimeout", 5000);
    }
    
    @Test
    void testHandleWebhookEvent_NoActiveWebhooks() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("reportId", 123L);
        payload.put("engineName", "TestEngine");
        
        WebhookEvent event = new WebhookEvent(
            this,
            WebhookEvent.EventType.REPORT_SUBMITTED,
            payload
        );
        
        when(webhookRepository.findByActive(true))
            .thenReturn(Collections.emptyList());
        
        // When
        webhookDeliveryService.handleWebhookEvent(event);
        
        // Then
        verify(webhookRepository).findByActive(true);
        verifyNoInteractions(entityManager);
    }
    
    @Test
    void testHandleWebhookEvent_WithActiveWebhooks() {
        // Given
        WebhookEntity webhook = WebhookEntity.builder()
            .id(1L)
            .url("https://example.com/webhook")
            .secret("test-secret")
            .events(Arrays.asList(WebhookEvent.EventType.REPORT_SUBMITTED))
            .active(true)
            .build();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("reportId", 123L);
        payload.put("engineName", "TestEngine");
        
        WebhookEvent event = new WebhookEvent(
            this,
            WebhookEvent.EventType.REPORT_SUBMITTED,
            payload
        );
        
        when(webhookRepository.findByActive(true))
            .thenReturn(Collections.singletonList(webhook));
        
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);
        
        // When
        webhookDeliveryService.handleWebhookEvent(event);
        
        // Then
        verify(webhookRepository).findByActive(true);
        verify(entityManager, atLeastOnce()).createNativeQuery(anyString());
    }
    
    @Test
    void testHandleWebhookEvent_DeliveryDisabled() {
        // Given
        ReflectionTestUtils.setField(webhookDeliveryService, "deliveryEnabled", false);
        
        Map<String, Object> payload = new HashMap<>();
        WebhookEvent event = new WebhookEvent(
            this,
            WebhookEvent.EventType.REPORT_SUBMITTED,
            payload
        );
        
        // When
        webhookDeliveryService.handleWebhookEvent(event);
        
        // Then
        verifyNoInteractions(webhookRepository);
        verifyNoInteractions(entityManager);
    }
    
    @Test
    void testProcessRetries_NoRetries() {
        // Given
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());
        
        // When
        webhookDeliveryService.processRetries();
        
        // Then
        verify(entityManager).createNativeQuery(anyString());
        verify(query).getResultList();
    }
    
    @Test
    void testProcessRetries_WithPendingRetries() {
        // Given
        Object[] retry = new Object[]{1L, 1L, "report.submitted", "{}", 1};
        
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(retry));
        
        WebhookEntity webhook = WebhookEntity.builder()
            .id(1L)
            .url("https://example.com/webhook")
            .secret("test-secret")
            .active(false)
            .build();
        
        when(webhookRepository.findById(1L)).thenReturn(Optional.of(webhook));
        when(query.executeUpdate()).thenReturn(1);
        
        // When
        webhookDeliveryService.processRetries();
        
        // Then
        verify(webhookRepository).findById(1L);
        verify(entityManager, atLeastOnce()).createNativeQuery(anyString());
    }
    
    @Test
    void testProcessRetries_DeliveryDisabled() {
        // Given
        ReflectionTestUtils.setField(webhookDeliveryService, "deliveryEnabled", false);
        
        // When
        webhookDeliveryService.processRetries();
        
        // Then
        verifyNoInteractions(entityManager);
        verifyNoInteractions(webhookRepository);
    }
    
    @Test
    void testGenerateSignature() throws Exception {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("test", "value");
        String secret = "test-secret";
        
        // When - using reflection to test private method
        java.lang.reflect.Method method = WebhookDeliveryService.class.getDeclaredMethod(
            "generateSignature", Map.class, String.class
        );
        method.setAccessible(true);
        String signature = (String) method.invoke(webhookDeliveryService, payload, secret);
        
        // Then
        assert signature != null;
        assert !signature.isEmpty();
    }
}

