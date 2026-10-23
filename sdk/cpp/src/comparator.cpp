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

// A number from text, only when the whole text is one (" 12.5 " yes, "13-555" no).
bool parse_number(const std::string& text, double& out) {
    size_t begin = text.find_first_not_of(" \t\r\n");
    if (begin == std::string::npos) return false;
    size_t end = text.find_last_not_of(" \t\r\n");
    std::string s = text.substr(begin, end - begin + 1);
    try {
        size_t used = 0;
        double d = std::stod(s, &used);
        if (used != s.size()) return false;
        out = d;
        return true;
    } catch (...) {
        return false;
    }
}

// A number held natively (not as text).
bool native_number(const CellValue& v, double& out) {
    if (std::holds_alternative<int32_t>(v)) { out = static_cast<double>(std::get<int32_t>(v)); return true; }
    if (std::holds_alternative<int64_t>(v)) { out = static_cast<double>(std::get<int64_t>(v)); return true; }
    if (std::holds_alternative<float>(v))   { out = static_cast<double>(std::get<float>(v));   return true; }
    if (std::holds_alternative<double>(v))  { out = std::get<double>(v);                       return true; }
    return false;
}

// Any CellValue as a double: a native number or text holding exactly one.
bool to_double(const CellValue& v, double& out) {
    if (native_number(v, out)) return true;
    if (std::holds_alternative<std::string>(v)) return parse_number(std::get<std::string>(v), out);
    return false;
}

// Lower-case base type name, without whitespace or parameters: "DECIMAL(15, 2)" -> "decimal".
std::string base_type(const std::string& raw) {
    std::string t = raw;
    std::transform(t.begin(), t.end(), t.begin(), ::tolower);
    t.erase(std::remove_if(t.begin(), t.end(), ::isspace), t.end());
    auto paren = t.find('(');
    if (paren != std::string::npos && paren > 0) t = t.substr(0, paren);
    return t;
}

// Canonical type name for parsing and comparison routing
std::string normalize_type(const std::string& raw) {
    std::string t = base_type(raw);
    if (t == "integer" || t == "int" || t == "int32" || t == "i32" || t == "i8" || t == "tinyint" ||
        t == "smallint" || t == "int2" || t == "int4" || t == "int16" || t == "i16") return "integer";
    if (t == "bigint" || t == "int64" || t == "i64" || t == "long" || t == "int8" || t == "hugeint") return "bigint";
    if (t == "double" || t == "fp64" || t == "float8" || t == "number" ||
        t == "numeric" || t == "decimal") return "double";
    if (t == "float" || t == "fp32" || t == "float4" || t == "real") return "float";
    if (t == "boolean" || t == "bool") return "boolean";
    return "string";
}

// The family a column type is compared by.
std::string type_family(const std::string& raw) {
    std::string t = base_type(raw);
    if (t.empty()) return "";
    std::string n = normalize_type(t);
    if (n == "integer" || n == "bigint" || n == "double" || n == "float") return "numeric";
    if (n == "boolean") return "boolean";
    return "string";
}

std::string show(const CellValue& v) {
    if (cell_value::is_null(v)) return "NULL";
    if (std::holds_alternative<std::string>(v)) return "'" + std::get<std::string>(v) + "'";
    if (std::holds_alternative<double>(v)) {
        std::ostringstream ss;
        ss.precision(17);
        ss << std::get<double>(v);
        return ss.str();
    }
    return cell_value::to_string(v);
}

std::string names_of(const std::vector<ColumnMetadata>& cols) {
    std::string out = "[";
    for (size_t i = 0; i < cols.size(); ++i) out += (i ? ", " : "") + cols[i].name;
    return out + "]";
}

std::string types_of(const std::vector<ColumnMetadata>& cols) {
    std::string out = "[";
    for (size_t i = 0; i < cols.size(); ++i) out += (i ? ", " : "") + cols[i].type;
    return out + "]";
}

bool equal_ignore_case(const std::string& a, const std::string& b) {
    auto trim = [](const std::string& s) {
        size_t begin = s.find_first_not_of(" \t");
        size_t end = s.find_last_not_of(" \t");
        return begin == std::string::npos ? std::string() : s.substr(begin, end - begin + 1);
    };
    std::string x = trim(a), y = trim(b);
    return x.size() == y.size() &&
           std::equal(x.begin(), x.end(), y.begin(), [](char c1, char c2) { return ::tolower(c1) == ::tolower(c2); });
}

} // anonymous namespace

// ---------------------------------------------------------------------------
// ResultComparator
// ---------------------------------------------------------------------------

ComparisonResult ResultComparator::compare_tables(
        const TableData& actual,
        const TableData& expected) const {
    return compare_tables(actual, expected, {});
}

ComparisonResult ResultComparator::compare_tables(
        const TableData& actual,
        const TableData& expected,
        const std::vector<std::optional<int>>& declared_scales) const {
    auto mismatch = describe_mismatch(expected, actual, declared_scales);
    return mismatch ? ComparisonResult::failure("Output mismatch: " + *mismatch) : ComparisonResult::success();
}

std::optional<std::string> ResultComparator::describe_mismatch(
        const TableData& expected,
        const TableData& actual,
        const std::vector<std::optional<int>>& declared_scales) const {
    const auto& e_cols = expected.columns();
    const auto& a_cols = actual.columns();
    if (expected.row_count() != actual.row_count()) {
        std::ostringstream ss;
        ss << "expected " << expected.row_count() << " rows, got " << actual.row_count();
        return ss.str();
    }
    if (e_cols.size() != a_cols.size()) {
        std::ostringstream ss;
        ss << "expected " << e_cols.size() << " columns " << names_of(e_cols)
           << ", got " << a_cols.size() << " " << names_of(a_cols);
        return ss.str();
    }
    for (size_t c = 0; c < e_cols.size(); ++c) {
        if (!equal_ignore_case(e_cols[c].name, a_cols[c].name)) {
            return "expected column names " + names_of(e_cols) + ", got " + names_of(a_cols);
        }
    }
    for (size_t c = 0; c < e_cols.size(); ++c) {
        if (type_family(e_cols[c].type) != type_family(a_cols[c].type)) {
            return "expected column types " + types_of(e_cols) + ", got " + types_of(a_cols);
        }
    }
    for (size_t r = 0; r < expected.row_count(); ++r) {
        const Row& e_row = expected.rows()[r];
        const Row& a_row = actual.rows()[r];
        for (size_t c = 0; c < e_row.size() && c < a_row.size(); ++c) {
            std::optional<int> scale = c < declared_scales.size() ? declared_scales[c] : std::nullopt;
            bool numeric = c < e_cols.size() && type_family(e_cols[c].type) == "numeric";
            if (!values_match(e_row[c], a_row[c], scale, numeric)) {
                std::ostringstream ss;
                ss << "row " << r << ", column " << (c < e_cols.size() ? e_cols[c].name : std::to_string(c))
                   << ": expected " << show(e_row[c]) << ", got " << show(a_row[c]);
                return ss.str();
            }
        }
    }
    return std::nullopt;
}

bool ResultComparator::numbers_close(double expected, double actual,
                                     std::optional<int> declared_scale, double epsilon) {
    if (std::isnan(expected) || std::isnan(actual)) return std::isnan(expected) && std::isnan(actual);
    if (std::isinf(expected) || std::isinf(actual)) return expected == actual;
    double magnitude = std::max(std::abs(expected), std::abs(actual));
    double tolerance = std::max(epsilon, epsilon * magnitude);
    if (declared_scale) {
        tolerance = std::max(tolerance, std::pow(10.0, -*declared_scale) * (1 + epsilon));
    }
    return std::abs(expected - actual) <= tolerance;
}

bool ResultComparator::values_match(const CellValue& expected, const CellValue& actual,
                                    std::optional<int> declared_scale, bool numeric_column) const {
    bool e_null = cell_value::is_null(expected), a_null = cell_value::is_null(actual);
    if (e_null || a_null) return e_null && a_null;
    if (std::holds_alternative<bool>(expected) || std::holds_alternative<bool>(actual)) {
        auto truthy = [](const CellValue& v) {
            if (std::holds_alternative<bool>(v)) return std::get<bool>(v);
            std::string s = cell_value::to_string(v);
            std::transform(s.begin(), s.end(), s.begin(), ::tolower);
            return s == "true";
        };
        return truthy(expected) == truthy(actual);
    }
    double e, a;
    bool e_native = native_number(expected, e), a_native = native_number(actual, a);
    if (numeric_column || e_native || a_native) {
        if (to_double(expected, e) && to_double(actual, a)) {
            return numbers_close(e, a, declared_scale, config_.float_epsilon);
        }
    }
    return compare_strings(cell_value::to_string(actual), cell_value::to_string(expected));
}

ComparisonResult ResultComparator::compare_rows(
        const Row& actual,
        const Row& expected,
        const std::vector<ColumnMetadata>& columns) const {
    for (size_t c = 0; c < expected.size() && c < actual.size(); ++c) {
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
    return values_match(expected, actual, std::nullopt, type_family(type) == "numeric");
}

bool ResultComparator::compare_floats(double a, double b) const {
    return numbers_close(b, a, std::nullopt, config_.float_epsilon);
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
    // A value parses only when the whole field is one: "13-555" stays text.
    try {
        size_t used = 0;
        if (norm == "integer") { int v = std::stoi(str, &used); if (used == str.size()) return static_cast<int32_t>(v); }
        if (norm == "bigint")  { long long v = std::stoll(str, &used); if (used == str.size()) return static_cast<int64_t>(v); }
        if (norm == "double")  { double v = std::stod(str, &used); if (used == str.size()) return v; }
        if (norm == "float")   { float v = std::stof(str, &used); if (used == str.size()) return v; }
        if (norm == "boolean") return (str == "true" || str == "1" || str == "t");
    } catch (...) {}

    return str;
}

} // namespace cell_value

} // namespace substrait::compliance
