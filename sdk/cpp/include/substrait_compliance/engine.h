#pragma once

#include "result.h"
#include "table_data.h"
#include <string>
#include <vector>
#include <memory>
#include <unordered_map>

namespace substrait::compliance {

/**
 * @brief Engine metadata information
 */
struct EngineInfo {
    std::string name;
    std::string version;
    std::string vendor;
    std::string description;
    
    EngineInfo() = default;
    
    EngineInfo(std::string n, std::string v, std::string vnd)
        : name(std::move(n))
        , version(std::move(v))
        , vendor(std::move(vnd)) {}
    
    EngineInfo& with_description(std::string desc) {
        description = std::move(desc);
        return *this;
    }
};

/**
 * @brief Engine capability information
 */
struct EngineCapabilities {
    std::vector<std::string> supported_relations;
    std::vector<std::string> supported_functions;
    std::vector<std::string> supported_types;
    std::unordered_map<std::string, std::string> extensions;
    
    EngineCapabilities() = default;
    
    /**
     * @brief Check if a relation is supported
     */
    bool supports_relation(const std::string& relation) const {
        return std::find(supported_relations.begin(), 
                        supported_relations.end(), 
                        relation) != supported_relations.end();
    }
    
    /**
     * @brief Check if a function is supported
     */
    bool supports_function(const std::string& function) const {
        return std::find(supported_functions.begin(), 
                        supported_functions.end(), 
                        function) != supported_functions.end();
    }
    
    /**
     * @brief Check if a type is supported
     */
    bool supports_type(const std::string& type) const {
        return std::find(supported_types.begin(), 
                        supported_types.end(), 
                        type) != supported_types.end();
    }
    
    /**
     * @brief Add a supported relation
     */
    EngineCapabilities& add_relation(std::string relation) {
        supported_relations.push_back(std::move(relation));
        return *this;
    }
    
    /**
     * @brief Add a supported function
     */
    EngineCapabilities& add_function(std::string function) {
        supported_functions.push_back(std::move(function));
        return *this;
    }
    
    /**
     * @brief Add a supported type
     */
    EngineCapabilities& add_type(std::string type) {
        supported_types.push_back(std::move(type));
        return *this;
    }
    
    /**
     * @brief Add an extension property
     */
    EngineCapabilities& add_extension(std::string key, std::string value) {
        extensions[std::move(key)] = std::move(value);
        return *this;
    }
};

/**
 * @brief Main interface that query engines must implement
 * 
 * This is the core abstraction for the compliance framework.
 * Engine developers implement this interface to integrate their
 * query engine with the compliance testing framework.
 */
class ComplianceEngine {
public:
    virtual ~ComplianceEngine() = default;
    
    /**
     * @brief Get engine metadata
     * 
     * @return EngineInfo containing name, version, vendor, etc.
     */
    virtual EngineInfo get_info() const = 0;
    
    /**
     * @brief Get engine capabilities
     * 
     * @return EngineCapabilities describing supported features
     */
    virtual EngineCapabilities get_capabilities() const = 0;
    
    /**
     * @brief Execute a Substrait plan
     * 
     * @param plan_bytes Serialized Substrait plan (protobuf)
     * @param input_data Map of table names to input data
     * @return ComplianceResult with execution status and output
     */
    virtual ComplianceResult execute_plan(
        const std::vector<uint8_t>& plan_bytes,
        const TableCollection& input_data
    ) = 0;
    
    /**
     * @brief Validate a Substrait plan without executing it
     * 
     * @param plan_bytes Serialized Substrait plan (protobuf)
     * @return ComplianceResult indicating if plan is valid
     */
    virtual ComplianceResult validate_plan(
        const std::vector<uint8_t>& plan_bytes
    ) = 0;
    
    /**
     * @brief Optional: Initialize engine resources
     * 
     * Called once before running tests. Override to perform
     * any necessary setup (e.g., connection pooling, caching).
     */
    virtual void initialize() {}
    
    /**
     * @brief Optional: Cleanup engine resources
     * 
     * Called once after all tests complete. Override to perform
     * cleanup (e.g., close connections, free resources).
     */
    virtual void shutdown() {}
    
    /**
     * @brief Optional: Check if engine can handle a specific test
     * 
     * @param test_id Test identifier
     * @return true if engine can run this test, false to skip
     */
    virtual bool can_run_test(const std::string& test_id) const {
        return true; // By default, attempt all tests
    }
};

/**
 * @brief Smart pointer type for engines
 */
using EnginePtr = std::shared_ptr<ComplianceEngine>;

/**
 * @brief Create a shared pointer to an engine
 */
template<typename T, typename... Args>
EnginePtr make_engine(Args&&... args) {
    return std::make_shared<T>(std::forward<Args>(args)...);
}

} // namespace substrait::compliance

