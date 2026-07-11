#pragma once

#include <string>
#include <vector>
#include <variant>
#include <memory>
#include <optional>
#include <unordered_map>

namespace substrait::compliance {

/**
 * @brief Represents a single value in a table cell
 */
using CellValue = std::variant<
    std::nullptr_t,      // NULL
    bool,                // Boolean
    int32_t,             // Integer (32-bit)
    int64_t,             // Long (64-bit)
    float,               // Float (32-bit)
    double,              // Double (64-bit)
    std::string          // String/Text
>;

/**
 * @brief Represents a single row in a table
 */
using Row = std::vector<CellValue>;

/**
 * @brief Column metadata
 */
struct ColumnMetadata {
    std::string name;
    std::string type;
    bool nullable = true;
    
    ColumnMetadata() = default;
    ColumnMetadata(std::string n, std::string t, bool null = true)
        : name(std::move(n)), type(std::move(t)), nullable(null) {}
};

/**
 * @brief Represents tabular data with schema information
 */
class TableData {
public:
    TableData() = default;
    
    /**
     * @brief Construct table with column metadata
     */
    explicit TableData(std::vector<ColumnMetadata> columns)
        : columns_(std::move(columns)) {}
    
    /**
     * @brief Add a row to the table
     */
    void add_row(Row row) {
        rows_.push_back(std::move(row));
    }
    
    /**
     * @brief Add multiple rows to the table
     */
    void add_rows(std::vector<Row> rows) {
        rows_.insert(rows_.end(),
                    std::make_move_iterator(rows.begin()),
                    std::make_move_iterator(rows.end()));
    }
    
    /**
     * @brief Get all rows
     */
    const std::vector<Row>& rows() const { return rows_; }
    std::vector<Row>& rows() { return rows_; }
    
    /**
     * @brief Get column metadata
     */
    const std::vector<ColumnMetadata>& columns() const { return columns_; }
    std::vector<ColumnMetadata>& columns() { return columns_; }
    
    /**
     * @brief Get number of rows
     */
    size_t row_count() const { return rows_.size(); }
    
    /**
     * @brief Get number of columns
     */
    size_t column_count() const { return columns_.size(); }
    
    /**
     * @brief Check if table is empty
     */
    bool empty() const { return rows_.empty(); }
    
    /**
     * @brief Clear all data
     */
    void clear() {
        rows_.clear();
    }
    
    /**
     * @brief Get a specific cell value
     */
    const CellValue& get_cell(size_t row_idx, size_t col_idx) const {
        return rows_.at(row_idx).at(col_idx);
    }
    
    /**
     * @brief Set column metadata
     */
    void set_columns(std::vector<ColumnMetadata> columns) {
        columns_ = std::move(columns);
    }

private:
    std::vector<ColumnMetadata> columns_;
    std::vector<Row> rows_;
};

/**
 * @brief Helper functions for working with CellValue
 */
namespace cell_value {

/**
 * @brief Check if a cell value is NULL
 */
inline bool is_null(const CellValue& value) {
    return std::holds_alternative<std::nullptr_t>(value);
}

/**
 * @brief Get string representation of a cell value
 */
std::string to_string(const CellValue& value);

/**
 * @brief Compare two cell values for equality
 */
bool equals(const CellValue& a, const CellValue& b, double epsilon = 1e-9);

/**
 * @brief Parse a string into a CellValue based on type
 */
CellValue parse(const std::string& str, const std::string& type);

} // namespace cell_value

/**
 * @brief Collection of named tables (for multi-table test cases)
 */
using TableCollection = std::unordered_map<std::string, TableData>;

} // namespace substrait::compliance

