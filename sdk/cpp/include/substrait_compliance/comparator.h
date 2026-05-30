#pragma once

#include "table_data.h"
#include "result.h"
#include <string>
#include <optional>

namespace substrait::compliance {

/**
 * @brief Configuration for result comparison
 */
struct ComparisonConfig {
    double float_epsilon = 1e-9;      // Tolerance for floating point comparison
    bool ignore_row_order = false;     // Whether to sort rows before comparison
    bool ignore_column_order = false;  // Whether to match columns by name
    bool strict_null_handling = true;  // Distinguish between NULL and empty string
    
    ComparisonConfig() = default;
    
    ComparisonConfig& with_epsilon(double eps) {
        float_epsilon = eps;
        return *this;
    }
    
    ComparisonConfig& ignore_order(bool ignore = true) {
        ignore_row_order = ignore;
        return *this;
    }
    
    ComparisonConfig& strict_nulls(bool strict = true) {
        strict_null_handling = strict;
        return *this;
    }
};

/**
 * @brief Result of comparing two values or tables
 */
struct ComparisonResult {
    bool matches = false;
    std::string message;
    std::optional<size_t> row_index;
    std::optional<size_t> column_index;
    
    static ComparisonResult success() {
        return ComparisonResult{true, "Match"};
    }
    
    static ComparisonResult failure(std::string msg) {
        return ComparisonResult{false, std::move(msg)};
    }
    
    static ComparisonResult failure(std::string msg, size_t row, size_t col) {
        return ComparisonResult{false, std::move(msg), row, col};
    }
};

/**
 * @brief Compares test results with expected outputs
 */
class ResultComparator {
public:
    explicit ResultComparator(ComparisonConfig config = ComparisonConfig())
        : config_(std::move(config)) {}
    
    /**
     * @brief Compare two tables for equality
     * 
     * @param actual Actual output from engine
     * @param expected Expected output from test case
     * @return Comparison result with details
     */
    ComparisonResult compare_tables(
        const TableData& actual,
        const TableData& expected
    ) const;
    
    /**
     * @brief Compare two cell values
     * 
     * @param actual Actual cell value
     * @param expected Expected cell value
     * @param type Data type for type-aware comparison
     * @return true if values match within tolerance
     */
    bool compare_values(
        const CellValue& actual,
        const CellValue& expected,
        const std::string& type = ""
    ) const;
    
    /**
     * @brief Compare two rows
     * 
     * @param actual Actual row
     * @param expected Expected row
     * @param columns Column metadata for type information
     * @return Comparison result
     */
    ComparisonResult compare_rows(
        const Row& actual,
        const Row& expected,
        const std::vector<ColumnMetadata>& columns
    ) const;
    
    /**
     * @brief Get comparison configuration
     */
    const ComparisonConfig& config() const { return config_; }
    ComparisonConfig& config() { return config_; }

private:
    /**
     * @brief Compare floating point values with epsilon
     */
    bool compare_floats(double a, double b) const;
    
    /**
     * @brief Compare integer values
     */
    bool compare_integers(int64_t a, int64_t b) const;
    
    /**
     * @brief Compare string values
     */
    bool compare_strings(const std::string& a, const std::string& b) const;
    
    /**
     * @brief Compare boolean values
     */
    bool compare_booleans(bool a, bool b) const;
    
    /**
     * @brief Sort rows for order-independent comparison
     */
    std::vector<Row> sort_rows(std::vector<Row> rows) const;
    
    ComparisonConfig config_;
};

/**
 * @brief Type-aware value comparator
 */
class TypeAwareComparator {
public:
    /**
     * @brief Compare values based on their SQL type
     */
    static bool compare(
        const CellValue& actual,
        const CellValue& expected,
        const std::string& sql_type,
        double epsilon = 1e-9
    );
    
    /**
     * @brief Check if type is numeric
     */
    static bool is_numeric_type(const std::string& type);
    
    /**
     * @brief Check if type is floating point
     */
    static bool is_float_type(const std::string& type);
    
    /**
     * @brief Check if type is integer
     */
    static bool is_integer_type(const std::string& type);
    
    /**
     * @brief Check if type is string
     */
    static bool is_string_type(const std::string& type);
};

/**
 * @brief Special value handler for NaN, Infinity, etc.
 */
class SpecialValueHandler {
public:
    /**
     * @brief Check if value is NaN
     */
    static bool is_nan(const CellValue& value);
    
    /**
     * @brief Check if value is positive infinity
     */
    static bool is_positive_infinity(const CellValue& value);
    
    /**
     * @brief Check if value is negative infinity
     */
    static bool is_negative_infinity(const CellValue& value);
    
    /**
     * @brief Check if value is any infinity
     */
    static bool is_infinity(const CellValue& value);
    
    /**
     * @brief Compare special floating point values
     */
    static bool compare_special_floats(const CellValue& a, const CellValue& b);
};

} // namespace substrait::compliance

// Made with Bob
