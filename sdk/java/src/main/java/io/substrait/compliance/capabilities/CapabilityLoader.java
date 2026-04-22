package io.substrait.compliance.capabilities;

import io.substrait.compliance.EngineDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads engine capability descriptors from JSON files.
 * 
 * <p>Capability descriptors define what Substrait features an engine supports,
 * allowing tests to be filtered based on engine capabilities.
 */
public class CapabilityLoader {
    
    private final ObjectMapper objectMapper;
    
    public CapabilityLoader() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Loads an engine descriptor from a JSON file.
     * 
     * @param path path to the JSON descriptor file
     * @return loaded engine descriptor
     * @throws IOException if file cannot be read or parsed
     */
    public EngineDescriptor loadFromFile(Path path) throws IOException {
        return objectMapper.readValue(Files.newInputStream(path), EngineDescriptor.class);
    }
    
    /**
     * Loads an engine descriptor from an input stream.
     * 
     * @param inputStream input stream containing JSON descriptor
     * @return loaded engine descriptor
     * @throws IOException if stream cannot be read or parsed
     */
    public EngineDescriptor loadFromStream(InputStream inputStream) throws IOException {
        return objectMapper.readValue(inputStream, EngineDescriptor.class);
    }
    
    /**
     * Loads an engine descriptor from a JSON string.
     * 
     * @param json JSON string containing descriptor
     * @return loaded engine descriptor
     * @throws IOException if JSON cannot be parsed
     */
    public EngineDescriptor loadFromString(String json) throws IOException {
        return objectMapper.readValue(json, EngineDescriptor.class);
    }
    
    /**
     * Loads an engine descriptor from classpath resources.
     * 
     * @param resourcePath path to resource (e.g., "/descriptors/duckdb.json")
     * @return loaded engine descriptor
     * @throws IOException if resource cannot be found or parsed
     */
    public EngineDescriptor loadFromResource(String resourcePath) throws IOException {
        InputStream stream = getClass().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        return loadFromStream(stream);
    }
}

// Made with Bob
