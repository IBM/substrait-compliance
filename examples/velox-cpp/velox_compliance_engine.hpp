/**
 * Velox C++ Reference Implementation Header
 * 
 * Defines the Velox compliance engine interface for Substrait testing.
 * Velox is Meta's unified execution engine for data processing.
 */

#ifndef SUBSTRAIT_COMPLIANCE_VELOX_ENGINE_HPP
#define SUBSTRAIT_COMPLIANCE_VELOX_ENGINE_HPP

#include <memory>
#include <string>
#include <vector>
#include <map>
#include <variant>
#include <chrono>

// Forward declarations for Velox
namespace facebook::velox {
    class Config;
    namespace memory {
        class MemoryPool;
    }
    namespace core {
        class QueryCtx;
        class PlanNode;
    }
    namespace exec {
        class Task;
    }
}

namespace substrait {
    class Plan;
}

namespace substrait::compliance {

// Type alias for cell values
using Value = std::variant<
    std::monostate,  // NULL
    bool,
    int8_t,
    int16_t,
    int32_t,
    int64_t,
    float,
    double,
    std::string,
    std::vector<uint8_t>
>;

/**
 * Engine metadata
 */
struct EngineInfo {
    std::string name;
    std::string version;
    std::string vendor;
    std::string substrait_version;
    std::string description;
};

/**
 * Engine capabilities
 */
struct EngineCapabilities {
    std::vector<std::string> supported_relations;
    std::vector<std::string> supported_functions;
    std::vector<std::string> supported_types;
    int max_plan_depth;
    bool supports_extensions;
    bool supports_advanced_extensions;
    bool supports_vectorized_execution;
};

/**
 * Table data structure
 */
class TableData {
public:
    TableData(
        const std::vector<std::string>& column_names,
        const std::vector<std::string>& column_types,
        const std::vector<std::vector<Value>>& rows)
        : column_names_(column_names)
        , column_types_(column_types)
        , rows_(rows) {}
    
    const std::vector<std::string>& getColumnNames() const { return column_names_; }
    const std::vector<std::string>& getColumnTypes() const { return column_types_; }
    const std::vector<std::vector<Value>>& getRows() const { return rows_; }
    size_t getRowCount() const { return rows_.size(); }
    size_t getColumnCount() const { return column_names_.size(); }
    
private:
    std::vector<std::string> column_names_;
    std::vector<std::string> column_types_;
    std::vector<std::vector<Value>> rows_;
};

/**
 * Plan validation result
 */
class PlanValidationResult {
public:
    static PlanValidationResult supported() {
        return PlanValidationResult(true, {});
    }
    
    static PlanValidationResult unsupported(
        const std::vector<std::string>& reasons) {
        return PlanValidationResult(false, reasons);
    }
    
    bool isSupported() const { return is_supported_; }
    const std::vector<std::string>& getReasons() const { return reasons_; }
    
private:
    PlanValidationResult(bool supported, const std::vector<std::string>& reasons)
        : is_supported_(supported), reasons_(reasons) {}
    
    bool is_supported_;
    std::vector<std::string> reasons_;
};

/**
 * Compliance test result
 */
class ComplianceResult {
public:
    static ComplianceResult success(const TableData& output, int64_t duration_ms) {
        return ComplianceResult(true, output, "", duration_ms);
    }
    
    static ComplianceResult failure(const std::string& error, int64_t duration_ms) {
        return ComplianceResult(false, TableData({}, {}, {}), error, duration_ms);
    }
    
    bool isSuccess() const { return success_; }
    const TableData& getOutput() const { return output_; }
    const std::string& getError() const { return error_; }
    int64_t getDuration() const { return duration_ms_; }
    
private:
    ComplianceResult(
        bool success,
        const TableData& output,
        const std::string& error,
        int64_t duration_ms)
        : success_(success)
        , output_(output)
        , error_(error)
        , duration_ms_(duration_ms) {}
    
    bool success_;
    TableData output_;
    std::string error_;
    int64_t duration_ms_;
};

/**
 * Velox Compliance Engine
 * 
 * Production-ready implementation using Velox's vectorized execution engine
 * with native Substrait support.
 */
class VeloxComplianceEngine {
public:
    VeloxComplianceEngine();
    ~VeloxComplianceEngine();
    
    // Disable copy/move
    VeloxComplianceEngine(const VeloxComplianceEngine&) = delete;
    VeloxComplianceEngine& operator=(const VeloxComplianceEngine&) = delete;
    
    /**
     * Get engine metadata
     */
    EngineInfo getEngineInfo() const;
    
    /**
     * Get engine capabilities
     */
    EngineCapabilities getCapabilities() const;
    
    /**
     * Execute a Substrait plan
     */
    ComplianceResult executePlan(
        const substrait::Plan& plan,
        const std::map<std::string, TableData>& input_data);
    
    /**
     * Validate a Substrait plan
     */
    PlanValidationResult validatePlan(const substrait::Plan& plan) const;
    
private:
    // Initialization
    void initializeVelox();
    
    // Data loading
    void loadInputData(const std::map<std::string, TableData>& input_data);
    std::shared_ptr<facebook::velox::RowVector> tableDataToRowVector(
        const TableData& data);
    
    // Plan conversion
    std::shared_ptr<facebook::velox::core::PlanNode> convertSubstraitPlan(
        const substrait::Plan& plan);
    
    // Execution
    TableData executeVeloxPlan(
        const std::shared_ptr<facebook::velox::core::PlanNode>& plan);
    
    // Result conversion
    TableData rowVectorToTableData(
        const std::shared_ptr<facebook::velox::RowVector>& rowVector) const;
    
    // Type mapping
    std::shared_ptr<facebook::velox::Type> mapSubstraitTypeToVelox(
        const std::string& substrait_type) const;
    std::string mapVeloxTypeToString(
        const std::shared_ptr<facebook::velox::Type>& velox_type) const;
    
    // Value conversion
    Value extractValueFromVector(
        const facebook::velox::VectorPtr& vector,
        size_t index) const;
    
    // Velox components
    std::shared_ptr<facebook::velox::memory::MemoryPool> pool_;
    std::shared_ptr<facebook::velox::core::QueryCtx> queryCtx_;
    std::map<std::string, std::shared_ptr<facebook::velox::RowVector>> tables_;
};

} // namespace substrait::compliance

#endif // SUBSTRAIT_COMPLIANCE_VELOX_ENGINE_HPP

// Made with Bob
