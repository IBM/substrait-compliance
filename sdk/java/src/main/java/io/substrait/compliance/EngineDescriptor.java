/*
 * Copyright 2026 Substrait Validation Framework Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.substrait.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Model representing a Substrait engine descriptor that defines engine capabilities,
 * supported functions, and expression types.
 *
 * <p>Engine descriptors specify which Substrait operations and functions are supported
 * by a particular database engine (e.g., DuckDB, PostgreSQL, Spark).
 *
 * @see EngineDescriptorValidator
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EngineDescriptor {
    
    @JsonProperty("engine_name")
    private String engineName;
    
    @JsonProperty("engine_version")
    private String engineVersion;
    
    @JsonProperty("Expressions")
    private Map<String, Object> expressions = new HashMap<>();
    
    /**
     * Gets the engine name (e.g., "DuckDB", "PostgreSQL", "Apache Spark").
     */
    public String getEngineName() {
        return engineName;
    }
    
    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }
    
    /**
     * Gets the engine version string.
     */
    public String getEngineVersion() {
        return engineVersion;
    }
    
    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }
    
    /**
     * Gets the expressions map containing supported expression types and functions.
     * 
     * <p>The map structure is:
     * <ul>
     *   <li>Key: Expression type (e.g., "substrait.expression.FieldReferenceExpression")</li>
     *   <li>Value: Either boolean (true if supported) or Map of function categories to function lists</li>
     * </ul>
     */
    public Map<String, Object> getExpressions() {
        return expressions;
    }
    
    public void setExpressions(Map<String, Object> expressions) {
        this.expressions = expressions;
    }
    
    /**
     * Checks if a specific expression type is supported.
     */
    public boolean supportsExpression(String expressionType) {
        Object value = expressions.get(expressionType);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null; // If it's a map, it's supported
    }
    
    /**
     * Gets the function categories for a specific expression type.
     * Returns null if the expression type doesn't have function categories.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFunctionCategories(String expressionType) {
        Object value = expressions.get(expressionType);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "EngineDescriptor{" +
                "engineName='" + engineName + '\'' +
                ", engineVersion='" + engineVersion + '\'' +
                ", expressionTypes=" + expressions.keySet().size() +
                '}';
    }
}

// Made with Bob
