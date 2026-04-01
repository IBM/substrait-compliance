package io.substrait.compliance;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Describes engine capabilities and supported Substrait features.
 */
public class EngineCapabilities {
    
    private final Set<String> supportedRelations;
    private final Set<String> supportedFunctions;
    private final Set<String> supportedTypes;
    private final boolean supportsExtensions;
    
    private EngineCapabilities(Builder builder) {
        this.supportedRelations = Collections.unmodifiableSet(new HashSet<>(builder.supportedRelations));
        this.supportedFunctions = Collections.unmodifiableSet(new HashSet<>(builder.supportedFunctions));
        this.supportedTypes = Collections.unmodifiableSet(new HashSet<>(builder.supportedTypes));
        this.supportsExtensions = builder.supportsExtensions;
    }
    
    public Set<String> getSupportedRelations() { return supportedRelations; }
    public Set<String> getSupportedFunctions() { return supportedFunctions; }
    public Set<String> getSupportedTypes() { return supportedTypes; }
    public boolean supportsExtensions() { return supportsExtensions; }
    
    public boolean supportsRelation(String relationType) {
        return supportedRelations.contains(relationType);
    }
    
    public boolean supportsFunction(String functionName) {
        return supportedFunctions.contains(functionName);
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private final Set<String> supportedRelations = new HashSet<>();
        private final Set<String> supportedFunctions = new HashSet<>();
        private final Set<String> supportedTypes = new HashSet<>();
        private boolean supportsExtensions = false;
        
        public Builder addRelation(String relationType) {
            supportedRelations.add(relationType);
            return this;
        }
        
        public Builder addFunction(String functionName) {
            supportedFunctions.add(functionName);
            return this;
        }
        
        public Builder addType(String typeName) {
            supportedTypes.add(typeName);
            return this;
        }
        
        public Builder supportsExtensions(boolean supports) {
            this.supportsExtensions = supports;
            return this;
        }
        
        public EngineCapabilities build() {
            return new EngineCapabilities(this);
        }
    }
}
