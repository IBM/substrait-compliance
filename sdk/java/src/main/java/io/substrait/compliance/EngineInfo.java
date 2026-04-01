package io.substrait.compliance;

import java.util.Objects;

/**
 * Engine identification and version information.
 */
public class EngineInfo {
    
    private final String engineName;
    private final String engineVersion;
    private final String substraitVersion;
    
    public EngineInfo(String engineName, String engineVersion, String substraitVersion) {
        this.engineName = Objects.requireNonNull(engineName);
        this.engineVersion = Objects.requireNonNull(engineVersion);
        this.substraitVersion = Objects.requireNonNull(substraitVersion);
    }
    
    public String getEngineName() { return engineName; }
    public String getEngineVersion() { return engineVersion; }
    public String getSubstraitVersion() { return substraitVersion; }
    
    @Override
    public String toString() {
        return String.format("%s %s (Substrait %s)", 
            engineName, engineVersion, substraitVersion);
    }
}
