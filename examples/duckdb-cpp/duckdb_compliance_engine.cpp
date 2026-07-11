/**
 * DuckDB C++ Reference Implementation for Substrait Compliance Testing
 * 
 * This demonstrates a production-ready integration of DuckDB with the
 * Substrait compliance framework using DuckDB's native Substrait support.
 */

#include "duckdb_compliance_engine.hpp"
#include <duckdb.hpp>
#include <substrait/plan.pb.h>
#include <iostream>
#include <sstream>

namespace substrait::compliance {

DuckDBComplianceEngine::DuckDBComplianceEngine() 
    : db_(nullptr), conn_(nullptr) {
    // Initialize DuckDB in-memory database
    db_ = std::make_unique<duckdb::DuckDB>(nullptr);
    conn_ = std::make_unique<duckdb::Connection>(*db_);
    
    // Load Substrait extension
    conn_->Query("INSTALL substrait; LOAD substrait;");
}

DuckDBComplianceEngine::~DuckDBComplianceEngine() = default;

EngineInfo DuckDBComplianceEngine::getEngineInfo() const {
    return EngineInfo{
        .name = "DuckDB",
        .version = "0.10.0",
        .vendor = "DuckDB Labs",
        .substrait_version = "0.80.0",
        .description = "In-process analytical database with native Substrait support"
    };
}

EngineCapabilities DuckDBComplianceEngine::getCapabilities() const {
    EngineCapabilities caps;
    
    // Supported relations
    caps.supported_relations = {
        "read", "filter", "project", "aggregate", "join",
        "sort", "limit", "union", "cross", "fetch"
    };
    
    // Supported scalar functions
    caps.supported_functions = {
        // Arithmetic
        "add", "subtract", "multiply", "divide", "modulus",
        "power", "sqrt", "abs", "negate",
        
        // Comparison
        "equal", "not_equal", "less_than", "less_than_or_equal",
        "greater_than", "greater_than_or_equal",
        
        // String functions
        "concat", "substring", "upper", "lower", "trim",
        "ltrim", "rtrim", "length", "like", "regexp_match",
        
        // Aggregate functions
        "sum", "count", "avg", "min", "max", "stddev", "variance",
        
        // Date/time functions
        "extract", "date_add", "date_diff",
        
        // Conditional
        "if_then_else", "coalesce", "nullif"
    };
    
    // Supported types
    caps.supported_types = {
        "boolean", "i8", "i16", "i32", "i64",
        "fp32", "fp64", "string", "binary",
        "date", "time", "timestamp", "interval",
        "decimal", "struct", "list", "map"
    };
    
    caps.max_plan_depth = 100;
    caps.supports_extensions = true;
    caps.supports_advanced_extensions = true;
    
    return caps;
}

ComplianceResult DuckDBComplianceEngine::executePlan(
    const substrait::Plan& plan,
    const std::map<std::string, TableData>& input_data) {
    
    auto start = std::chrono::high_resolution_clock::now();
    
    try {
        // 1. Load input data into DuckDB
        loadInputData(input_data);
        
        // 2. Serialize Substrait plan to binary
        std::string plan_binary;
        if (!plan.SerializeToString(&plan_binary)) {
            throw std::runtime_error("Failed to serialize Substrait plan");
        }
        
        // 3. Execute Substrait plan using DuckDB's native support
        std::string query = "SELECT * FROM from_substrait('" + 
                           base64Encode(plan_binary) + "')";
        
        auto result = conn_->Query(query);
        
        if (result->HasError()) {
            throw std::runtime_error(result->GetError());
        }
        
        // 4. Convert result to TableData
        TableData output = convertResultToTableData(*result);
        
        auto end = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(
            end - start).count();
        
        return ComplianceResult::success(output, duration);
        
    } catch (const std::exception& e) {
        auto end = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(
            end - start).count();
        
        return ComplianceResult::failure(e.what(), duration);
    }
}

PlanValidationResult DuckDBComplianceEngine::validatePlan(
    const substrait::Plan& plan) const {
    
    try {
        // Check basic plan structure
        if (plan.relations_size() == 0) {
            return PlanValidationResult::unsupported(
                {"Plan has no relations"});
        }
        
        // Serialize and validate with DuckDB
        std::string plan_binary;
        if (!plan.SerializeToString(&plan_binary)) {
            return PlanValidationResult::unsupported(
                {"Failed to serialize plan"});
        }
        
        // Try to explain the plan (validation without execution)
        std::string query = "EXPLAIN SELECT * FROM from_substrait('" + 
                           base64Encode(plan_binary) + "')";
        
        auto result = conn_->Query(query);
        
        if (result->HasError()) {
            return PlanValidationResult::unsupported(
                {result->GetError()});
        }
        
        return PlanValidationResult::supported();
        
    } catch (const std::exception& e) {
        return PlanValidationResult::unsupported({e.what()});
    }
}

void DuckDBComplianceEngine::loadInputData(
    const std::map<std::string, TableData>& input_data) {
    
    for (const auto& [table_name, data] : input_data) {
        // Drop table if exists
        conn_->Query("DROP TABLE IF EXISTS " + table_name);
        
        // Create table
        std::string create_sql = buildCreateTableSQL(table_name, data);
        auto result = conn_->Query(create_sql);
        
        if (result->HasError()) {
            throw std::runtime_error("Failed to create table " + table_name + 
                                   ": " + result->GetError());
        }
        
        // Insert data
        insertTableData(table_name, data);
    }
}

std::string DuckDBComplianceEngine::buildCreateTableSQL(
    const std::string& table_name,
    const TableData& data) const {
    
    std::ostringstream sql;
    sql << "CREATE TABLE " << table_name << " (";
    
    const auto& columns = data.getColumnNames();
    const auto& types = data.getColumnTypes();
    
    for (size_t i = 0; i < columns.size(); ++i) {
        if (i > 0) sql << ", ";
        sql << columns[i] << " " << mapSubstraitTypeToDuckDB(types[i]);
    }
    
    sql << ")";
    return sql.str();
}

void DuckDBComplianceEngine::insertTableData(
    const std::string& table_name,
    const TableData& data) {
    
    const auto& rows = data.getRows();
    if (rows.empty()) return;
    
    // Use prepared statement for efficiency
    std::ostringstream sql;
    sql << "INSERT INTO " << table_name << " VALUES ";
    
    for (size_t i = 0; i < rows.size(); ++i) {
        if (i > 0) sql << ", ";
        sql << "(";
        
        const auto& row = rows[i];
        for (size_t j = 0; j < row.size(); ++j) {
            if (j > 0) sql << ", ";
            sql << formatValue(row[j]);
        }
        
        sql << ")";
    }
    
    auto result = conn_->Query(sql.str());
    if (result->HasError()) {
        throw std::runtime_error("Failed to insert data: " + result->GetError());
    }
}

TableData DuckDBComplianceEngine::convertResultToTableData(
    duckdb::QueryResult& result) const {
    
    std::vector<std::string> column_names;
    std::vector<std::string> column_types;
    std::vector<std::vector<Value>> rows;
    
    // Get column metadata
    for (size_t i = 0; i < result.ColumnCount(); ++i) {
        column_names.push_back(result.ColumnName(i));
        column_types.push_back(result.ColumnType(i).ToString());
    }
    
    // Fetch all rows
    while (true) {
        auto chunk = result.Fetch();
        if (!chunk || chunk->size() == 0) break;
        
        for (size_t row_idx = 0; row_idx < chunk->size(); ++row_idx) {
            std::vector<Value> row;
            
            for (size_t col_idx = 0; col_idx < result.ColumnCount(); ++col_idx) {
                auto value = chunk->GetValue(col_idx, row_idx);
                row.push_back(convertDuckDBValue(value));
            }
            
            rows.push_back(std::move(row));
        }
    }
    
    return TableData(column_names, column_types, rows);
}

std::string DuckDBComplianceEngine::mapSubstraitTypeToDuckDB(
    const std::string& substrait_type) const {
    
    std::string lower_type = substrait_type;
    std::transform(lower_type.begin(), lower_type.end(), 
                   lower_type.begin(), ::tolower);
    
    if (lower_type.find("i8") != std::string::npos) return "TINYINT";
    if (lower_type.find("i16") != std::string::npos) return "SMALLINT";
    if (lower_type.find("i32") != std::string::npos) return "INTEGER";
    if (lower_type.find("i64") != std::string::npos) return "BIGINT";
    if (lower_type.find("fp32") != std::string::npos) return "FLOAT";
    if (lower_type.find("fp64") != std::string::npos) return "DOUBLE";
    if (lower_type.find("string") != std::string::npos) return "VARCHAR";
    if (lower_type.find("binary") != std::string::npos) return "BLOB";
    if (lower_type.find("boolean") != std::string::npos) return "BOOLEAN";
    if (lower_type.find("date") != std::string::npos) return "DATE";
    if (lower_type.find("time") != std::string::npos) return "TIME";
    if (lower_type.find("timestamp") != std::string::npos) return "TIMESTAMP";
    if (lower_type.find("decimal") != std::string::npos) return "DECIMAL(18,2)";
    
    return "VARCHAR"; // Default fallback
}

std::string DuckDBComplianceEngine::formatValue(const Value& value) const {
    if (std::holds_alternative<std::monostate>(value)) {
        return "NULL";
    } else if (std::holds_alternative<int64_t>(value)) {
        return std::to_string(std::get<int64_t>(value));
    } else if (std::holds_alternative<double>(value)) {
        return std::to_string(std::get<double>(value));
    } else if (std::holds_alternative<std::string>(value)) {
        std::string str = std::get<std::string>(value);
        // Escape single quotes
        size_t pos = 0;
        while ((pos = str.find("'", pos)) != std::string::npos) {
            str.replace(pos, 1, "''");
            pos += 2;
        }
        return "'" + str + "'";
    } else if (std::holds_alternative<bool>(value)) {
        return std::get<bool>(value) ? "TRUE" : "FALSE";
    }
    
    return "NULL";
}

Value DuckDBComplianceEngine::convertDuckDBValue(
    const duckdb::Value& value) const {
    
    if (value.IsNull()) {
        return std::monostate{};
    }
    
    switch (value.type().id()) {
        case duckdb::LogicalTypeId::BOOLEAN:
            return value.GetValue<bool>();
        case duckdb::LogicalTypeId::TINYINT:
        case duckdb::LogicalTypeId::SMALLINT:
        case duckdb::LogicalTypeId::INTEGER:
        case duckdb::LogicalTypeId::BIGINT:
            return value.GetValue<int64_t>();
        case duckdb::LogicalTypeId::FLOAT:
        case duckdb::LogicalTypeId::DOUBLE:
            return value.GetValue<double>();
        case duckdb::LogicalTypeId::VARCHAR:
            return value.GetValue<std::string>();
        default:
            return value.ToString();
    }
}

std::string DuckDBComplianceEngine::base64Encode(
    const std::string& input) const {
    
    static const char* base64_chars = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        "abcdefghijklmnopqrstuvwxyz"
        "0123456789+/";
    
    std::string output;
    int val = 0;
    int valb = -6;
    
    for (unsigned char c : input) {
        val = (val << 8) + c;
        valb += 8;
        while (valb >= 0) {
            output.push_back(base64_chars[(val >> valb) & 0x3F]);
            valb -= 6;
        }
    }
    
    if (valb > -6) {
        output.push_back(base64_chars[((val << 8) >> (valb + 8)) & 0x3F]);
    }
    
    while (output.size() % 4) {
        output.push_back('=');
    }
    
    return output;
}

} // namespace substrait::compliance

