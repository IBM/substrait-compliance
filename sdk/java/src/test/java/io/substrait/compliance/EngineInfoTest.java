package io.substrait.compliance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EngineInfoTest {
    
    @Test
    void testEngineInfoCreation() {
        EngineInfo info = new EngineInfo("DuckDB", "0.9.2", "0.80.0");
        
        assertThat(info.getEngineName()).isEqualTo("DuckDB");
        assertThat(info.getEngineVersion()).isEqualTo("0.9.2");
        assertThat(info.getSubstraitVersion()).isEqualTo("0.80.0");
    }
    
    @Test
    void testToString() {
        EngineInfo info = new EngineInfo("DuckDB", "0.9.2", "0.80.0");
        assertThat(info.toString()).contains("DuckDB", "0.9.2", "0.80.0");
    }
    
    @Test
    void testNullValidation() {
        assertThatThrownBy(() -> new EngineInfo(null, "1.0", "0.80.0"))
            .isInstanceOf(NullPointerException.class);
    }
}
