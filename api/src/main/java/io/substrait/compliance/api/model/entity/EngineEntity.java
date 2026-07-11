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
 * JPA entity representing a database engine that submits compliance reports.
 * 
 * <p>Each engine is uniquely identified by its name and version combination.
 * An engine can have multiple compliance reports associated with it.
 */
@Entity
@Table(name = "engines", uniqueConstraints = {
    @UniqueConstraint(name = "uk_engine_name_version", columnNames = {"name", "version"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, length = 100)
    private String version;
    
    @Column
    private String vendor;
    
    @Column(name = "substrait_version", length = 100)
    private String substraitVersion;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @OneToMany(mappedBy = "engine", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReportEntity> reports = new ArrayList<>();
    
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
     * Gets the full engine identifier as "name-version".
     * 
     * @return the engine identifier
     */
    public String getIdentifier() {
        return name + "-" + version;
    }
}

