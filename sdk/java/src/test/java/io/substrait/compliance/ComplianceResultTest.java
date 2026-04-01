package io.substrait.compliance;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ComplianceResultTest {
    
    @Test
    void testSuccessResult() {
        TableData data = createSampleTableData();
        ComplianceResult result = ComplianceResult.success(data, 100);
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).isEqualTo(data);
        assertThat(result.getExecutionTimeMs()).isEqualTo(100);
        assertThat(result.getErrorMessage()).isNull();
    }
    
    @Test
    void testFailureResult() {
        Exception ex = new RuntimeException("Test error");
        ComplianceResult result = ComplianceResult.failure("Error occurred", ex, 50);
        
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Error occurred");
        assertThat(result.getException()).isEqualTo(ex);
        assertThat(result.getExecutionTimeMs()).isEqualTo(50);
    }
    
    private TableData createSampleTableData() {
        List<String> columns = Arrays.asList("id", "name");
        List<String> types = Arrays.asList("i32", "string");
        List<List<Object>> rows = Arrays.asList(
            Arrays.asList(1, "Alice"),
            Arrays.asList(2, "Bob")
        );
        return new TableData(columns, types, rows);
    }
}
