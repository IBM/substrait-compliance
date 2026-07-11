package io.substrait.compliance.api.repository;

import io.substrait.compliance.api.model.entity.EngineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for accessing engine information.
 */
@Repository
public interface EngineRepository extends JpaRepository<EngineEntity, Long> {
    
    /**
     * Finds an engine by name and version.
     */
    Optional<EngineEntity> findByNameAndVersion(String name, String version);
    
    /**
     * Finds an engine by name (latest version).
     */
    Optional<EngineEntity> findFirstByNameOrderByCreatedAtDesc(String name);
    
    /**
     * Checks if an engine exists by name and version.
     */
    boolean existsByNameAndVersion(String name, String version);
}

