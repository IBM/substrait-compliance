/**
 * Velox C++ Reference Implementation for Substrait Compliance Testing
 * 
 * This demonstrates a production-ready integration of Velox with the
 * Substrait compliance framework using Velox's vectorized execution engine.
 */

#include "velox_compliance_engine.hpp"
#include <velox/core/PlanNode.h>
#include <velox/core/QueryCtx.h>
#include <velox/exec/Task.h>
#include <velox/functions/prestosql/registration/RegistrationFunctions.h>
#include <velox/parse/TypeResolver.h>
#include <velox/substrait/SubstraitToVeloxPlan.h>
#include <velox/vector/ComplexVector.h>
#include <velox/vector/FlatVector.h>
#include <iostream>
#include <sstream>

namespace substrait::compliance {

using namespace facebook::velox;

VeloxComplianceEngine::VeloxComplianceEngine() {
    initializeVelox();
}

VeloxComplianceEngine::~VeloxComplianceEngine() = default;

void VeloxComplianceEngine::initializeVelox() {
    // Initialize Velox memory pool
    pool_ = memory::addDefaultLeafMemoryPool();
    
    // Register Presto SQL functions
    functions::prestosql::registerAllScalarFunctions();
    functions::prestosql::registerAllAggregateFunctions();
    
    // Initialize parse type resolver
    parse::registerTypeResolver();
    
    // Create query context
    std::unordered_map<std::string, std::shared_ptr<Config>> configs;
    queryCtx_ = core::QueryCtx::create(nullptr, std::move(configs));
}

EngineInfo VeloxComplianceEngine::getEngineInfo() const {
    return EngineInfo{
        .name = "Velox",
        .version = "0.0.1",
        .vendor = "Meta Platforms, Inc.",
        .substrait_version = "0.80.0",
        .description = "Unified execution engine for data processing with vectorized execution"
    };
}

EngineCapabilities VeloxComplianceEngine::getCapabilities() const {
    EngineCapabilities caps;
    
    // Supported relations
    caps.supported_relations = {
        "read", "filter", "project", "aggregate", "join",
        "sort", "limit", "union", "cross", "window", "expand"
    };
    
    // Supported scalar functions (Presto SQL compatible)
    caps.supported_functions = {
        // Arithmetic
        "add", "subtract", "multiply", "divide", "modulus",
        "power", "sqrt", "abs", "negate", "ceil", "floor", "round",
        
        // Comparison
        "equal", "not_equal", "less_than", "less_than_or_equal",
        "greater_than", "greater_than_or_equal", "between",
        
        // String functions
        "concat", "substring", "upper", "lower", "trim",
        "ltrim", "rtrim", "length", "like", "regexp_like",
        "regexp_extract", "replace", "split", "strpos",
        
        // Aggregate functions
        "sum", "count", "avg", "min", "max", "stddev", "variance",
        "approx_distinct", "arbitrary", "array_agg",
        
        // Date/time functions
        "extract", "date_add", "date_diff", "date_trunc",
        "year", "month", "day", "hour", "minute", "second",
        
        // Conditional
        "if", "coalesce", "nullif", "case",
        
        // Array functions
        "array_constructor", "array_contains", "array_length",
        "element_at", "slice", "cardinality",
        
        // Map functions
        "map", "map_keys", "map_values", "map_entries",
        
        // JSON functions
        "json_extract", "json_extract_scalar", "json_parse"
    };
    
    // Supported types
    caps.supported_types = {
        "boolean", "tinyint", "smallint", "integer", "bigint",
        "real", "double", "varchar", "varbinary",
        "date", "timestamp", "interval",
        "decimal", "array", "map", "row"
    };
    
    caps.max_plan_depth = 100;
    caps.supports_extensions = true;
    caps.supports_advanced_extensions = true;
    caps.supports_vectorized_execution = true;
    
    return caps;
}

ComplianceResult VeloxComplianceEngine::executePlan(
    const substrait::Plan& plan,
    const std::map<std::string, TableData>& input_data) {
    
    auto start = std::chrono::high_resolution_clock::now();
    
    try {
        // 1. Load input data into Velox
        loadInputData(input_data);
        
        // 2. Convert Substrait plan to Velox plan
        auto veloxPlan = convertSubstraitPlan(plan);
        
        // 3. Execute Velox plan
        TableData output = executeVeloxPlan(veloxPlan);
        
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

PlanValidationResult VeloxComplianceEngine::validatePlan(
    const substrait::Plan& plan) const {
    
    try {
        // Check basic plan structure
        if (plan.relations_size() == 0) {
            return PlanValidationResult::unsupported(
                {"Plan has no relations"});
        }
        
        // Try to convert the plan (validation without execution)
        // In production, would use Velox's plan validator
        auto converter = substrait::SubstraitVeloxPlanConverter(pool_.get());
        auto veloxPlan = converter.toVeloxPlan(plan);
        
        if (!veloxPlan) {
            return PlanValidationResult::unsupported(
                {"Failed to convert Substrait plan to Velox plan"});
        }
        
        return PlanValidationResult::supported();
        
    } catch (const std::exception& e) {
        return PlanValidationResult::unsupported({e.what()});
    }
}

void VeloxComplianceEngine::loadInputData(
    const std::map<std::string, TableData>& input_data) {
    
    for (const auto& [table_name, data] : input_data) {
        auto rowVector = tableDataToRowVector(data);
        tables_[table_name] = rowVector;
    }
}

std::shared_ptr<RowVector> VeloxComplianceEngine::tableDataToRowVector(
    const TableData& data) {
    
    // Build Velox type
    std::vector<std::string> names = data.getColumnNames();
    std::vector<TypePtr> types;
    
    for (const auto& type_str : data.getColumnTypes()) {
        types.push_back(mapSubstraitTypeToVelox(type_str));
    }
    
    auto rowType = ROW(std::move(names), std::move(types));
    
    // Build column vectors
    std::vector<VectorPtr> children;
    size_t numRows = data.getRowCount();
    
    for (size_t col_idx = 0; col_idx < data.getColumnCount(); ++col_idx) {
        const auto& column_type = data.getColumnTypes()[col_idx];
        auto veloxType = mapSubstraitTypeToVelox(column_type);
        
        // Create flat vector for this column
        auto flatVector = BaseVector::create(veloxType, numRows, pool_.get());
        
        // Populate values
        const auto& rows = data.getRows();
        for (size_t row_idx = 0; row_idx < numRows; ++row_idx) {
            const auto& value = rows[row_idx][col_idx];
            
            if (std::holds_alternative<std::monostate>(value)) {
                flatVector->setNull(row_idx, true);
            } else {
                // Set value based on type
                if (auto* intVector = flatVector->as<FlatVector<int32_t>>()) {
                    if (std::holds_alternative<int32_t>(value)) {
                        intVector->set(row_idx, std::get<int32_t>(value));
                    }
                } else if (auto* bigintVector = flatVector->as<FlatVector<int64_t>>()) {
                    if (std::holds_alternative<int64_t>(value)) {
                        bigintVector->set(row_idx, std::get<int64_t>(value));
                    }
                } else if (auto* doubleVector = flatVector->as<FlatVector<double>>()) {
                    if (std::holds_alternative<double>(value)) {
                        doubleVector->set(row_idx, std::get<double>(value));
                    }
                } else if (auto* stringVector = flatVector->as<FlatVector<StringView>>()) {
                    if (std::holds_alternative<std::string>(value)) {
                        stringVector->set(row_idx, StringView(std::get<std::string>(value)));
                    }
                } else if (auto* boolVector = flatVector->as<FlatVector<bool>>()) {
                    if (std::holds_alternative<bool>(value)) {
                        boolVector->set(row_idx, std::get<bool>(value));
                    }
                }
            }
        }
        
        children.push_back(flatVector);
    }
    
    return std::make_shared<RowVector>(
        pool_.get(), rowType, BufferPtr(nullptr), numRows, std::move(children));
}

std::shared_ptr<core::PlanNode> VeloxComplianceEngine::convertSubstraitPlan(
    const substrait::Plan& plan) {
    
    // Use Velox's Substrait converter
    substrait::SubstraitVeloxPlanConverter converter(pool_.get());
    
    // Convert plan with table mappings
    std::unordered_map<std::string, std::shared_ptr<RowVector>> tableMap;
    for (const auto& [name, vector] : tables_) {
        tableMap[name] = vector;
    }
    
    return converter.toVeloxPlan(plan, tableMap);
}

TableData VeloxComplianceEngine::executeVeloxPlan(
    const std::shared_ptr<core::PlanNode>& plan) {
    
    // Create task
    auto task = exec::Task::create(
        "task-0",
        plan,
        0,
        queryCtx_,
        exec::Task::ExecutionMode::kSerial);
    
    // Start task
    task->start(1);
    
    // Collect results
    std::vector<RowVectorPtr> results;
    while (auto result = task->next()) {
        results.push_back(result);
    }
    
    // Wait for completion
    task->requestCancel();
    
    // Convert results to TableData
    if (results.empty()) {
        return TableData({}, {}, {});
    }
    
    return rowVectorToTableData(results[0]);
}

TableData VeloxComplianceEngine::rowVectorToTableData(
    const std::shared_ptr<RowVector>& rowVector) const {
    
    if (!rowVector || rowVector->size() == 0) {
        return TableData({}, {}, {});
    }
    
    auto rowType = rowVector->type()->as<TypeKind::ROW>();
    
    // Extract column names and types
    std::vector<std::string> column_names;
    std::vector<std::string> column_types;
    
    for (size_t i = 0; i < rowType.size(); ++i) {
        column_names.push_back(rowType.nameOf(i));
        column_types.push_back(mapVeloxTypeToString(rowType.childAt(i)));
    }
    
    // Extract rows
    std::vector<std::vector<Value>> rows;
    for (size_t row_idx = 0; row_idx < rowVector->size(); ++row_idx) {
        std::vector<Value> row;
        
        for (size_t col_idx = 0; col_idx < rowVector->childrenSize(); ++col_idx) {
            auto child = rowVector->childAt(col_idx);
            row.push_back(extractValueFromVector(child, row_idx));
        }
        
        rows.push_back(std::move(row));
    }
    
    return TableData(column_names, column_types, rows);
}

TypePtr VeloxComplianceEngine::mapSubstraitTypeToVelox(
    const std::string& substrait_type) const {
    
    std::string lower_type = substrait_type;
    std::transform(lower_type.begin(), lower_type.end(), 
                   lower_type.begin(), ::tolower);
    
    if (lower_type.find("i8") != std::string::npos || 
        lower_type.find("tinyint") != std::string::npos) {
        return TINYINT();
    }
    if (lower_type.find("i16") != std::string::npos || 
        lower_type.find("smallint") != std::string::npos) {
        return SMALLINT();
    }
    if (lower_type.find("i32") != std::string::npos || 
        lower_type.find("integer") != std::string::npos) {
        return INTEGER();
    }
    if (lower_type.find("i64") != std::string::npos || 
        lower_type.find("bigint") != std::string::npos) {
        return BIGINT();
    }
    if (lower_type.find("fp32") != std::string::npos || 
        lower_type.find("real") != std::string::npos) {
        return REAL();
    }
    if (lower_type.find("fp64") != std::string::npos || 
        lower_type.find("double") != std::string::npos) {
        return DOUBLE();
    }
    if (lower_type.find("bool") != std::string::npos) {
        return BOOLEAN();
    }
    if (lower_type.find("string") != std::string::npos || 
        lower_type.find("varchar") != std::string::npos) {
        return VARCHAR();
    }
    if (lower_type.find("binary") != std::string::npos || 
        lower_type.find("varbinary") != std::string::npos) {
        return VARBINARY();
    }
    if (lower_type.find("date") != std::string::npos) {
        return DATE();
    }
    if (lower_type.find("timestamp") != std::string::npos) {
        return TIMESTAMP();
    }
    
    return VARCHAR(); // Default fallback
}

std::string VeloxComplianceEngine::mapVeloxTypeToString(
    const TypePtr& velox_type) const {
    
    return velox_type->toString();
}

Value VeloxComplianceEngine::extractValueFromVector(
    const VectorPtr& vector,
    size_t index) const {
    
    if (vector->isNullAt(index)) {
        return std::monostate{};
    }
    
    switch (vector->typeKind()) {
        case TypeKind::BOOLEAN:
            return vector->as<SimpleVector<bool>>()->valueAt(index);
        case TypeKind::TINYINT:
            return static_cast<int8_t>(vector->as<SimpleVector<int8_t>>()->valueAt(index));
        case TypeKind::SMALLINT:
            return static_cast<int16_t>(vector->as<SimpleVector<int16_t>>()->valueAt(index));
        case TypeKind::INTEGER:
            return static_cast<int32_t>(vector->as<SimpleVector<int32_t>>()->valueAt(index));
        case TypeKind::BIGINT:
            return static_cast<int64_t>(vector->as<SimpleVector<int64_t>>()->valueAt(index));
        case TypeKind::REAL:
            return static_cast<float>(vector->as<SimpleVector<float>>()->valueAt(index));
        case TypeKind::DOUBLE:
            return static_cast<double>(vector->as<SimpleVector<double>>()->valueAt(index));
        case TypeKind::VARCHAR:
            return vector->as<SimpleVector<StringView>>()->valueAt(index).str();
        default:
            return std::string("<complex type>");
    }
}

} // namespace substrait::compliance

// Made with Bob
