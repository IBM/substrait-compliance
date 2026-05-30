/**
 * DuckDB C++ Reference Implementation Header
 * 
 * Defines the DuckDB compliance engine interface for Substrait testing.
 */

#ifndef SUBSTRAIT_COMPLIANCE_DUCKDB_ENGINE_HPP
#define SUBSTRAIT_COMPLIANCE_DUCKDB_ENGINE_HPP

#include <memory>
#include <string>
#include <vector>
#include <map>
#include <variant>
#include <chrono>

// Forward declarations
namespace duckdb {
    class DuckDB;
    class Connection;
    class QueryResult;
    class Value;
}

namespace substrait {
    class Plan;
}

namespace substrait::compliance {

// Type alias for cell values
using Value = std::variant<
    std::monostate,  // NULL
    bool,
    int64_t,
    double,
    std::string
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
 * DuckDB Compliance Engine
 * 
 * Production-ready implementation using DuckDB's native Substrait support.
 */
class DuckDBComplianceEngine {
public:
    DuckDBComplianceEngine();
    ~DuckDBComplianceEngine();
    
    // Disable copy/move
    DuckDBComplianceEngine(const DuckDBComplianceEngine&) = delete;
    DuckDBComplianceEngine& operator=(const DuckDBComplianceEngine&) = delete;
    
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
    // Data loading
    void loadInputData(const std::map<std::string, TableData>& input_data);
    std::string buildCreateTableSQL(
        const std::string& table_name,
        const TableData& data) const;
    void insertTableData(
        const std::string& table_name,
        const TableData& data);
    
    // Result conversion
    TableData convertResultToTableData(duckdb::QueryResult& result) const;
    
    // Type mapping
    std::string mapSubstraitTypeToDuckDB(const std::string& substrait_type) const;
    
    // Value formatting
    std::string formatValue(const Value& value) const;
    Value convertDuckDBValue(const duckdb::Value& value) const;
    
    // Utilities
    std::string base64Encode(const std::string& input) const;
    
    // DuckDB connection
    std::unique_ptr<duckdb::DuckDB> db_;
    std::unique_ptr<duckdb::Connection> conn_;
};

} // namespace substrait::compliance

#endif // SUBSTRAIT_COMPLIANCE_DUCKDB_ENGINE_HPP

// Made with Bob
