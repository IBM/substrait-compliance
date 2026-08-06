// comparator.cpp — ResultComparator and TypeAwareComparator implementation

#include "substrait_compliance/comparator.h"
#include <algorithm>
#include <cmath>
#include <sstream>
#include <stdexcept>

namespace substrait::compliance {

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

namespace {

// Convert any CellValue to a double for numeric comparison (returns false if not numeric)
bool to_double(const CellValue& v, double& out) {
    if (std::holds_alternative<int32_t>(v)) {
        out = static_cast<double>(std::get<int32_t>(v));
        return true;
    }
    if (std::holds_alternative<int64_t>(v)) {
        out = static_cast<double>(std::get<int64_t>(v));
        return true;
    }
    if (std::holds_alternative<float>(v)) {
        out = static_cast<double>(std::get<float>(v));
        return true;
    }
    if (std::holds_alternative<double>(v)) {
        out = std::get<double>(v);
        return true;
    }
    // Try string → double
    if (std::holds_alternative<std::string>(v)) {
        try {
            out = std::stod(std::get<std::string>(v));
            return true;
        } catch (...) {}
    }
    return false;
}

// Canonical type name for comparison routing
std::string normalize_type(const std::string& raw) {
    std::string t = raw;
    // lower-case
    std::transform(t.begin(), t.end(), t.begin(), ::tolower);
    // strip whitespace
    t.erase(std::remove_if(t.begin(), t.end(), ::isspace), t.end());

    if (t == "integer" || t == "int" || t == "int32" || t == "i32" ||
        t == "smallint" || t == "int4" || t == "int16" || t == "i16") return "integer";
    if (t == "bigint" || t == "int64" || t == "i64" || t == "long") return "bigint";
    if (t == "double" || t == "fp64" || t == "float8" || t == "number" ||
        t == "numeric" || t == "decimal") return "double";
    if (t == "float" || t == "fp32" || t == "float4" || t == "real") return "float";
    if (t == "boolean" || t == "bool") return "boolean";
    if (t == "string" || t == "varchar" || t == "text" || t == "utf8" ||
        t == "char" || t == "date" || t == "timestamp") return "string";
    return "string";
}

} // anonymous namespace

// ---------------------------------------------------------------------------
// ResultComparator
// ---------------------------------------------------------------------------

ComparisonResult ResultComparator::compare_tables(
        const TableData& actual,
        const TableData& expected) const {

    // Column count must match
    if (actual.column_count() != expected.column_count()) {
        std::ostringstream ss;
        ss << "Column count mismatch: expected " << expected.column_count()
           << ", got " << actual.column_count();
        return ComparisonResult::failure(ss.str());
    }

    // Row count must match
    if (actual.row_count() != expected.row_count()) {
        std::ostringstream ss;
        ss << "Row count mismatch: expected " << expected.row_count()
           << ", got " << actual.row_count();
        return ComparisonResult::failure(ss.str());
    }

    const auto& cols = expected.columns();

    for (size_t r = 0; r < expected.row_count(); ++r) {
        ComparisonResult row_cmp = compare_rows(
            actual.rows()[r], expected.rows()[r], cols);
        if (!row_cmp.matches) {
            std::ostringstream ss;
            ss << "Row " << r << ": " << row_cmp.message;
            return ComparisonResult::failure(ss.str(), r,
                row_cmp.column_index.value_or(0));
        }
    }

    return ComparisonResult::success();
}

ComparisonResult ResultComparator::compare_rows(
        const Row& actual,
        const Row& expected,
        const std::vector<ColumnMetadata>& columns) const {

    for (size_t c = 0; c < expected.size(); ++c) {
        const std::string& type = (c < columns.size()) ? columns[c].type : "";
        if (!compare_values(actual[c], expected[c], type)) {
            std::ostringstream ss;
            ss << "Column " << c << " mismatch";
            if (c < columns.size()) ss << " (" << columns[c].name << ")";
            return ComparisonResult::failure(ss.str(), 0, c);
        }
    }
    return ComparisonResult::success();
}

bool ResultComparator::compare_values(
        const CellValue& actual,
        const CellValue& expected,
        const std::string& type) const {

    // Both NULL → equal
    if (cell_value::is_null(actual) && cell_value::is_null(expected)) return true;
    // One NULL → not equal
    if (cell_value::is_null(actual) || cell_value::is_null(expected)) return false;

    // Special float handling (NaN == NaN, ±inf)
    if (SpecialValueHandler::compare_special_floats(actual, expected)) return true;

    // Type-aware numeric comparison
    std::string norm = normalize_type(type);

    if (norm == "double" || norm == "float") {
        double a, e;
        if (to_double(actual, a) && to_double(expected, e)) {
            return compare_floats(a, e);
        }
    }

    if (norm == "integer" || norm == "bigint") {
        double a, e;
        if (to_double(actual, a) && to_double(expected, e)) {
            return compare_integers(static_cast<int64_t>(a), static_cast<int64_t>(e));
        }
    }

    if (norm == "boolean") {
        if (std::holds_alternative<bool>(actual) && std::holds_alternative<bool>(expected)) {
            return compare_booleans(std::get<bool>(actual), std::get<bool>(expected));
        }
    }

    // Cross-type: try numeric first
    {
        double a, e;
        if (to_double(actual, a) && to_double(expected, e)) {
            return compare_floats(a, e);
        }
    }

    // Fall back to string comparison
    return compare_strings(cell_value::to_string(actual), cell_value::to_string(expected));
}

bool ResultComparator::compare_floats(double a, double b) const {
    if (std::isnan(a) && std::isnan(b)) return true;
    if (std::isinf(a) && std::isinf(b)) return a == b;
    return std::abs(a - b) <= config_.float_epsilon;
}

bool ResultComparator::compare_integers(int64_t a, int64_t b) const {
    return a == b;
}

bool ResultComparator::compare_strings(const std::string& a, const std::string& b) const {
    return a == b;
}

bool ResultComparator::compare_booleans(bool a, bool b) const {
    return a == b;
}

// ---------------------------------------------------------------------------
// TypeAwareComparator
// ---------------------------------------------------------------------------

bool TypeAwareComparator::compare(
        const CellValue& actual,
        const CellValue& expected,
        const std::string& sql_type,
        double epsilon) {
    ResultComparator cmp(ComparisonConfig{}.with_epsilon(epsilon));
    return cmp.compare_values(actual, expected, sql_type);
}

bool TypeAwareComparator::is_numeric_type(const std::string& t) {
    return is_float_type(t) || is_integer_type(t);
}

bool TypeAwareComparator::is_float_type(const std::string& t) {
    std::string n = normalize_type(t);
    return n == "double" || n == "float";
}

bool TypeAwareComparator::is_integer_type(const std::string& t) {
    std::string n = normalize_type(t);
    return n == "integer" || n == "bigint";
}

bool TypeAwareComparator::is_string_type(const std::string& t) {
    return normalize_type(t) == "string";
}

// ---------------------------------------------------------------------------
// SpecialValueHandler
// ---------------------------------------------------------------------------

bool SpecialValueHandler::is_nan(const CellValue& v) {
    double d;
    if (to_double(v, d)) return std::isnan(d);
    return false;
}

bool SpecialValueHandler::is_positive_infinity(const CellValue& v) {
    double d;
    if (to_double(v, d)) return std::isinf(d) && d > 0;
    return false;
}

bool SpecialValueHandler::is_negative_infinity(const CellValue& v) {
    double d;
    if (to_double(v, d)) return std::isinf(d) && d < 0;
    return false;
}

bool SpecialValueHandler::is_infinity(const CellValue& v) {
    double d;
    if (to_double(v, d)) return std::isinf(d);
    return false;
}

bool SpecialValueHandler::compare_special_floats(const CellValue& a, const CellValue& b) {
    double da, db;
    if (!to_double(a, da) || !to_double(b, db)) return false;

    if (std::isnan(da) && std::isnan(db)) return true;
    if (std::isinf(da) && std::isinf(db)) return da == db;
    return false;
}

// ---------------------------------------------------------------------------
// cell_value helpers
// ---------------------------------------------------------------------------

namespace cell_value {

std::string to_string(const CellValue& value) {
    return std::visit([](const auto& v) -> std::string {
        using T = std::decay_t<decltype(v)>;
        if constexpr (std::is_same_v<T, std::nullptr_t>) return "NULL";
        else if constexpr (std::is_same_v<T, bool>)       return v ? "true" : "false";
        else if constexpr (std::is_same_v<T, int32_t>)    return std::to_string(v);
        else if constexpr (std::is_same_v<T, int64_t>)    return std::to_string(v);
        else if constexpr (std::is_same_v<T, float>)      return std::to_string(v);
        else if constexpr (std::is_same_v<T, double>)     return std::to_string(v);
        else if constexpr (std::is_same_v<T, std::string>) return v;
        return "";
    }, value);
}

bool equals(const CellValue& a, const CellValue& b, double epsilon) {
    ResultComparator cmp(ComparisonConfig{}.with_epsilon(epsilon));
    return cmp.compare_values(a, b, "");
}

CellValue parse(const std::string& str, const std::string& type) {
    if (str.empty() || str == "NULL" || str == "null") return nullptr;

    std::string norm = normalize_type(type);
    try {
        if (norm == "integer") return static_cast<int32_t>(std::stoi(str));
        if (norm == "bigint")  return static_cast<int64_t>(std::stoll(str));
        if (norm == "double")  return std::stod(str);
        if (norm == "float")   return std::stof(str);
        if (norm == "boolean") return (str == "true" || str == "1" || str == "t");
    } catch (...) {}

    return str;
}

} // namespace cell_value

} // namespace substrait::compliance
