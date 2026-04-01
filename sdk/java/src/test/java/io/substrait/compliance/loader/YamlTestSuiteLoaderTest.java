package io.substrait.compliance.loader;

import io.substrait.compliance.TestSuite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class YamlTestSuiteLoaderTest {
    
    @Test
    void testSupportsYamlFiles() {
        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
        
        assertThat(loader.supports(Path.of("test.yaml"))).isTrue();
        assertThat(loader.supports(Path.of("test.yml"))).isTrue();
        assertThat(loader.supports(Path.of("test.json"))).isFalse();
        assertThat(loader.supports(Path.of("test.txt"))).isFalse();
    }
    
    @Test
    void testLoadSimpleYaml(@TempDir Path tempDir) throws Exception {
        String yaml = "name: \"test-suite\"\n" +
                      "version: \"1.0.0\"\n" +
                      "description: \"Test suite\"\n" +
                      "testCases: []\n";
        
        Path yamlFile = tempDir.resolve("test.yaml");
        Files.writeString(yamlFile, yaml);
        
        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
        TestSuite suite = loader.load(yamlFile);
        
        assertThat(suite.getName()).isEqualTo("test-suite");
        assertThat(suite.getMetadata().getVersion()).isEqualTo("1.0.0");
        assertThat(suite.getTestCases()).isEmpty();
    }
}
