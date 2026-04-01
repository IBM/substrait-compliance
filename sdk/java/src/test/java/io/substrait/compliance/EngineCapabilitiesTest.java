package io.substrait.compliance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EngineCapabilitiesTest {
    
    @Test
    void testBuilderPattern() {
        EngineCapabilities caps = EngineCapabilities.builder()
            .addRelation("READ")
            .addRelation("FILTER")
            .addFunction("add")
            .addFunction("subtract")
            .addType("i32")
            .supportsExtensions(true)
            .build();
        
        assertThat(caps.getSupportedRelations()).containsExactlyInAnyOrder("READ", "FILTER");
        assertThat(caps.getSupportedFunctions()).containsExactlyInAnyOrder("add", "subtract");
        assertThat(caps.getSupportedTypes()).contains("i32");
        assertThat(caps.supportsExtensions()).isTrue();
    }
    
    @Test
    void testSupportsChecks() {
        EngineCapabilities caps = EngineCapabilities.builder()
            .addRelation("READ")
            .addFunction("add")
            .build();
        
        assertThat(caps.supportsRelation("READ")).isTrue();
        assertThat(caps.supportsRelation("JOIN")).isFalse();
        assertThat(caps.supportsFunction("add")).isTrue();
        assertThat(caps.supportsFunction("multiply")).isFalse();
    }
}
