/**
 * Velox Compliance Example - Main Entry Point
 * 
 * Demonstrates how to use the Velox compliance engine to run
 * Substrait compliance tests.
 */

#include "velox_compliance_engine.hpp"
#include <iostream>
#include <fstream>
#include <sstream>

using namespace substrait::compliance;

/**
 * Load a Substrait plan from file
 */
substrait::Plan loadPlanFromFile(const std::string& filename) {
    std::ifstream file(filename, std::ios::binary);
    if (!file) {
        throw std::runtime_error("Failed to open plan file: " + filename);
    }
    
    substrait::Plan plan;
    if (!plan.ParseFromIstream(&file)) {
        throw std::runtime_error("Failed to parse plan from file: " + filename);
    }
    
    return plan;
}

/**
 * Create sample input data for testing
 */
std::map<std::string, TableData> createSampleData() {
    std::map<std::string, TableData> data;
    
    // Create a simple "orders" table
    std::vector<std::string> columns = {"order_id", "customer_id", "amount", "status"};
    std::vector<std::string> types = {"i32", "i32", "fp64", "string"};
    std::vector<std::vector<Value>> rows = {
        {int32_t(1), int32_t(100), 150.50, std::string("completed")},
        {int32_t(2), int32_t(101), 200.75, std::string("pending")},
        {int32_t(3), int32_t(100), 99.99, std::string("completed")},
        {int32_t(4), int32_t(102), 450.00, std::string("shipped")},
        {int32_t(5), int32_t(101), 325.25, std::string("completed")}
    };
    
    data["orders"] = TableData(columns, types, rows);
    
    // Create a simple "customers" table
    columns = {"customer_id", "name", "country", "active"};
    types = {"i32", "string", "string", "bool"};
    rows = {
        {int32_t(100), std::string("Alice"), std::string("USA"), true},
        {int32_t(101), std::string("Bob"), std::string("UK"), true},
        {int32_t(102), std::string("Charlie"), std::string("Canada"), false}
    };
    
    data["customers"] = TableData(columns, types, rows);
    
    return data;
}

/**
 * Print table data
 */
void printTableData(const TableData& data) {
    const auto& columns = data.getColumnNames();
    const auto& rows = data.getRows();
    
    // Print header
    for (size_t i = 0; i < columns.size(); ++i) {
        if (i > 0) std::cout << " | ";
        std::cout << columns[i];
    }
    std::cout << "\n";
    
    // Print separator
    for (size_t i = 0; i < columns.size(); ++i) {
        if (i > 0) std::cout << "-+-";
        std::cout << std::string(columns[i].length(), '-');
    }
    std::cout << "\n";
    
    // Print rows
    for (const auto& row : rows) {
        for (size_t i = 0; i < row.size(); ++i) {
            if (i > 0) std::cout << " | ";
            
            const auto& value = row[i];
            if (std::holds_alternative<std::monostate>(value)) {
                std::cout << "NULL";
            } else if (std::holds_alternative<bool>(value)) {
                std::cout << (std::get<bool>(value) ? "true" : "false");
            } else if (std::holds_alternative<int8_t>(value)) {
                std::cout << static_cast<int>(std::get<int8_t>(value));
            } else if (std::holds_alternative<int16_t>(value)) {
                std::cout << std::get<int16_t>(value);
            } else if (std::holds_alternative<int32_t>(value)) {
                std::cout << std::get<int32_t>(value);
            } else if (std::holds_alternative<int64_t>(value)) {
                std::cout << std::get<int64_t>(value);
            } else if (std::holds_alternative<float>(value)) {
                std::cout << std::get<float>(value);
            } else if (std::holds_alternative<double>(value)) {
                std::cout << std::get<double>(value);
            } else if (std::holds_alternative<std::string>(value)) {
                std::cout << std::get<std::string>(value);
            }
        }
        std::cout << "\n";
    }
}

int main(int argc, char* argv[]) {
    std::cout << "Velox Substrait Compliance Example\n";
    std::cout << "===================================\n\n";
    
    try {
        // 1. Create Velox engine
        VeloxComplianceEngine engine;
        
        // 2. Print engine info
        auto info = engine.getEngineInfo();
        std::cout << "Engine: " << info.name << " " << info.version << "\n";
        std::cout << "Vendor: " << info.vendor << "\n";
        std::cout << "Substrait Version: " << info.substrait_version << "\n";
        std::cout << "Description: " << info.description << "\n\n";
        
        // 3. Print capabilities
        auto caps = engine.getCapabilities();
        std::cout << "Capabilities:\n";
        std::cout << "  Relations: " << caps.supported_relations.size() << "\n";
        std::cout << "  Functions: " << caps.supported_functions.size() << "\n";
        std::cout << "  Types: " << caps.supported_types.size() << "\n";
        std::cout << "  Max Plan Depth: " << caps.max_plan_depth << "\n";
        std::cout << "  Extensions: " << (caps.supports_extensions ? "Yes" : "No") << "\n";
        std::cout << "  Vectorized: " << (caps.supports_vectorized_execution ? "Yes" : "No") << "\n\n";
        
        // 4. Create sample data
        std::cout << "Creating sample data...\n";
        auto input_data = createSampleData();
        std::cout << "Created " << input_data.size() << " tables\n\n";
        
        // 5. Execute a plan (if provided)
        if (argc > 1) {
            std::string plan_file = argv[1];
            std::cout << "Loading plan from: " << plan_file << "\n";
            
            auto plan = loadPlanFromFile(plan_file);
            
            // Validate plan
            std::cout << "Validating plan...\n";
            auto validation = engine.validatePlan(plan);
            
            if (!validation.isSupported()) {
                std::cout << "Plan validation failed:\n";
                for (const auto& reason : validation.getReasons()) {
                    std::cout << "  - " << reason << "\n";
                }
                return 1;
            }
            
            std::cout << "Plan is valid\n\n";
            
            // Execute plan
            std::cout << "Executing plan...\n";
            auto result = engine.executePlan(plan, input_data);
            
            if (result.isSuccess()) {
                std::cout << "Execution successful (" 
                         << result.getDuration() << " ms)\n\n";
                
                std::cout << "Results:\n";
                printTableData(result.getOutput());
            } else {
                std::cout << "Execution failed: " << result.getError() << "\n";
                return 1;
            }
        } else {
            std::cout << "Usage: " << argv[0] << " <plan_file.substrait>\n";
            std::cout << "\nNo plan file provided. Showing sample data:\n\n";
            
            for (const auto& [table_name, table_data] : input_data) {
                std::cout << "Table: " << table_name << "\n";
                printTableData(table_data);
                std::cout << "\n";
            }
        }
        
        std::cout << "\nExample completed successfully!\n";
        return 0;
        
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << "\n";
        return 1;
    }
}

// Made with Bob
