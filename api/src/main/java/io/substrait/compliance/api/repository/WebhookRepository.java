package io.substrait.compliance.api.repository;

import io.substrait.compliance.api.model.entity.WebhookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for webhook entities.
 * 
 * <p>Provides data access methods for webhook management and querying.
 */
@Repository
public interface WebhookRepository extends JpaRepository<WebhookEntity, Long> {
    
    /**
     * Finds all active webhooks subscribed to a specific event type.
     * 
     * @param active whether the webhook is active
     * @param eventType the event type to filter by
     * @return list of matching webhooks
     */
    @Query("SELECT w FROM WebhookEntity w WHERE w.active = :active AND :eventType MEMBER OF w.events")
    List<WebhookEntity> findByActiveAndEventsContaining(
        @Param("active") Boolean active,
        @Param("eventType") String eventType
    );
    
    /**
     * Finds all webhooks created by a specific user.
     * 
     * @param createdBy the username
     * @return list of webhooks
     */
    List<WebhookEntity> findByCreatedBy(String createdBy);
    
    /**
     * Finds all active webhooks.
     * 
     * @param active whether the webhook is active
     * @return list of active webhooks
     */
    List<WebhookEntity> findByActive(Boolean active);
}

// Made with Bob