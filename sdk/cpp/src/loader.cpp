// loader.cpp — YamlTestSuiteLoader implementation

#include "substrait_compliance/loader.h"
#include "substrait_compliance/comparator.h"  // cell_value::parse
#include "substrait_compliance/error.h"
#include "substrait_compliance/plan_types.h"
#include <yaml-cpp/yaml.h>
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <algorithm>
#include <climits>
#include <cstdint>

namespace substrait::compliance {

// ---------------------------------------------------------------------------
// CSV parsing helpers (matching Java reference: pipe-or-comma delimiter,
// typed-header detection, quote handling)
// ---------------------------------------------------------------------------

namespace {

char detect_delimiter(const std::string& line) {
    long pipes  = std::count(line.begin(), line.end(), '|');
    long commas = std::count(line.begin(), line.end(), ',');
    return pipes > commas ? '|' : ',';
}

std::vector<std::string> parse_delimited_line(const std::string& line, char delimiter) {
    std::vector<std::string> fields;
    std::string current;
    bool in_quotes = false;

    for (size_t i = 0; i < line.size(); ++i) {
        char ch = line[i];
        if (ch == '"') {
            if (in_quotes && i + 1 < line.size() && line[i + 1] == '"') {
                current += '"';
                ++i;
            } else {
                in_quotes = !in_quotes;
            }
        } else if (ch == delimiter && !in_quotes) {
            // trim
            while (!current.empty() && current.front() == ' ') current.erase(current.begin());
            while (!current.empty() && current.back()  == ' ') current.pop_back();
            // strip wrapping quotes
            if (current.size() >= 2 && current.front() == '"' && current.back() == '"') {
                current = current.substr(1, current.size() - 2);
            }
            fields.push_back(std::move(current));
            current.clear();
        } else {
            current += ch;
        }
    }
    // last field
    while (!current.empty() && current.front() == ' ') current.erase(current.begin());
    while (!current.empty() && current.back()  == ' ') current.pop_back();
    if (current.size() >= 2 && current.front() == '"' && current.back() == '"') {
        current = current.substr(1, current.size() - 2);
    }
    fields.push_back(std::move(current));
    return fields;
}

// A header is "typed" if every field contains a ':' separator (name:type).
bool looks_like_typed_header(const std::string& line, char delimiter) {
    auto fields = parse_delimited_line(line, delimiter);
    if (fields.empty()) return false;
    return std::all_of(fields.begin(), fields.end(), [](const std::string& f) {
        auto pos = f.find(':');
        return pos != std::string::npos && pos > 0 && pos < f.size() - 1;
    });
}

} // anonymous namespace

// ---------------------------------------------------------------------------
// YamlTestSuiteLoader::load_plan
// ---------------------------------------------------------------------------

std::vector<uint8_t> YamlTestSuiteLoader::load_plan(
        const std::filesystem::path& plan_path) {
    std::ifstream f(plan_path, std::ios::binary);
    if (!f) {
        throw LoaderError("Plan file not found: " + plan_path.string());
    }
    return std::vector<uint8_t>(
        std::istreambuf_iterator<char>(f),
        std::istreambuf_iterator<char>());
}

// ---------------------------------------------------------------------------
// YamlTestSuiteLoader::load_csv
// ---------------------------------------------------------------------------

TableData YamlTestSuiteLoader::load_csv(
        const std::filesystem::path& csv_path) {
    return load_typed_csv(csv_path);
}

namespace {

std::string lower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c) { return std::tolower(c); });
    return s;
}

std::vector<std::string> read_lines(const std::filesystem::path& csv_path) {
    std::ifstream f(csv_path);
    if (!f) throw LoaderError("CSV file not found: " + csv_path.string());
    std::vector<std::string> lines;
    for (std::string line; std::getline(f, line);) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        lines.push_back(line);
    }
    while (!lines.empty() && lines.back().empty()) lines.pop_back();
    return lines;
}

bool blank(const std::string& s) { return s.find_first_not_of(" \t") == std::string::npos; }

// One input value as its column's type; throws std::invalid_argument when it does not parse.
CellValue plan_value(const std::string& v, const std::string& type) {
    if (v.empty() || lower(v) == "null") return nullptr;
    size_t used = 0;
    auto whole = [&](size_t n) { if (n != v.size()) throw std::invalid_argument(v); };
    if (type == "integer") { long long n = std::stoll(v, &used); whole(used);
        if (n < INT32_MIN || n > INT32_MAX) throw std::out_of_range(v); return static_cast<int32_t>(n); }
    if (type == "bigint") { long long n = std::stoll(v, &used); whole(used); return static_cast<int64_t>(n); }
    if (type == "float") { float x = std::stof(v, &used); whole(used); return x; }
    if (type == "double") { double x = std::stod(v, &used); whole(used); return x; }
    if (type.rfind("decimal", 0) == 0) { (void)std::stod(v, &used); whole(used); return v; }
    if (type == "boolean") return lower(v) == "true";
    return v;
}

} // anonymous namespace

TableData load_input_table(const std::filesystem::path& csv_path, const std::vector<SchemaColumn>& columns) {
    auto lines = read_lines(csv_path);
    std::vector<ColumnMetadata> meta;
    for (const auto& c : columns) meta.emplace_back(c.name, c.type);
    TableData table(meta);
    if (lines.empty()) return table;
    char delim = detect_delimiter(lines[0]);
    auto header = parse_delimited_line(lines[0], delim);
    bool is_header = header.size() == columns.size();
    for (size_t i = 0; is_header && i < columns.size(); ++i) {
        is_header = lower(header[i].substr(0, header[i].find(':'))) == lower(columns[i].name);
    }
    table.rows().reserve(lines.size());
    for (size_t i = is_header ? 1 : 0; i < lines.size(); ++i) {
        if (blank(lines[i])) continue;
        auto fields = parse_delimited_line(lines[i], delim);
        if (fields.size() != columns.size()) {
            std::ostringstream ss;
            ss << csv_path.string() << " row " << (i + 1) << " has " << fields.size()
               << " values, expected " << columns.size();
            throw LoaderError(ss.str());
        }
        Row row;
        row.reserve(fields.size());
        for (size_t c = 0; c < fields.size(); ++c) {
            try {
                row.push_back(plan_value(fields[c], columns[c].type));
            } catch (const std::logic_error&) {
                std::ostringstream ss;
                ss << csv_path.string() << " row " << (i + 1) << ", column " << columns[c].name
                   << ": '" << fields[c] << "' is not a " << columns[c].type;
                throw LoaderError(ss.str());
            }
        }
        table.add_row(std::move(row));
    }
    return table;
}

TableCollection TestCase::load_input_data() const {
    TableCollection tables = input_data_;
    for (const auto& t : input_tables_) {
        tables[t.name] = t.columns ? load_input_table(t.file, *t.columns) : load_typed_csv(t.file);
    }
    return tables;
}

TableData load_typed_csv(const std::filesystem::path& csv_path) {
    std::ifstream f(csv_path);
    if (!f) {
        throw LoaderError("CSV file not found: " + csv_path.string());
    }

    std::vector<std::string> lines;
    for (std::string line; std::getline(f, line);) {
        // strip trailing \r on Windows line-endings
        if (!line.empty() && line.back() == '\r') line.pop_back();
        lines.push_back(line);
    }

    if (lines.empty()) {
        throw LoaderError("CSV file is empty: " + csv_path.string());
    }

    char delim = detect_delimiter(lines[0]);
    bool typed_header = looks_like_typed_header(lines[0], delim);

    std::vector<ColumnMetadata> columns;
    size_t data_start = 0;

    if (typed_header) {
        auto fields = parse_delimited_line(lines[0], delim);
        for (const auto& field : fields) {
            auto sep = field.find(':');
            std::string name = field.substr(0, sep);
            std::string type = field.substr(sep + 1);
            // normalize type to lower-case
            std::transform(type.begin(), type.end(), type.begin(), ::tolower);
            columns.emplace_back(name, type);
        }
        data_start = 1;
    } else {
        // No header: assign generic names, treat everything as string
        auto fields = parse_delimited_line(lines[0], delim);
        for (size_t i = 0; i < fields.size(); ++i) {
            columns.emplace_back("column_" + std::to_string(i + 1), "string");
        }
    }

    TableData table(columns);

    for (size_t i = data_start; i < lines.size(); ++i) {
        const std::string& line = lines[i];
        if (line.empty()) continue;

        auto fields = parse_delimited_line(line, delim);
        if (fields.size() != columns.size()) {
            std::ostringstream ss;
            ss << "CSV row " << (i + 1) << " in " << csv_path.string()
               << " has " << fields.size() << " fields but expected " << columns.size();
            throw LoaderError(ss.str());
        }

        Row row;
        row.reserve(fields.size());
        for (size_t c = 0; c < fields.size(); ++c) {
            row.push_back(cell_value::parse(fields[c], columns[c].type));
        }
        table.add_row(std::move(row));
    }

    return table;
}

// ---------------------------------------------------------------------------
// YamlTestSuiteLoader::load
// ---------------------------------------------------------------------------

TestSuitePtr YamlTestSuiteLoader::load(const std::filesystem::path& path) {
    if (!std::filesystem::exists(path)) {
        throw LoaderError("Test suite file not found: " + path.string());
    }

    YAML::Node root;
    try {
        root = YAML::LoadFile(path.string());
    } catch (const YAML::Exception& e) {
        throw LoaderError("Failed to parse YAML: " + std::string(e.what()));
    }

    std::filesystem::path base_dir = path.parent_path();

    std::string suite_name    = root["name"]    ? root["name"].as<std::string>()    : "unknown";
    std::string suite_version = root["version"] ? root["version"].as<std::string>() : "1.0";
    std::string suite_desc    = root["description"] ? root["description"].as<std::string>() : "";

    TestSuiteMetadata meta(suite_name, suite_version);
    meta.with_description(suite_desc);

    auto suite = make_test_suite(std::move(meta));

    if (!root["testCases"]) {
        return suite; // empty suite
    }

    for (const auto& tc_node : root["testCases"]) {
        std::string id = tc_node["id"] ? tc_node["id"].as<std::string>() : "";
        if (id.empty()) continue;

        // Load plan bytes (planBinary field)
        std::vector<uint8_t> plan_bytes;
        if (tc_node["planBinary"]) {
            std::filesystem::path plan_path =
                base_dir / tc_node["planBinary"].as<std::string>();
            try {
                plan_bytes = load_plan(plan_path);
            } catch (const LoaderError&) {
                // missing plan: produce empty plan bytes (test will be skipped by runner)
            }
        }

        TestCase tc(id, std::move(plan_bytes));

        if (tc_node["description"]) {
            tc.with_description(tc_node["description"].as<std::string>());
        }

        // The decimal scales the plan declares (planJson field), for the comparison
        if (tc_node["planJson"]) {
            if (auto scales = declared_scales_file(base_dir / tc_node["planJson"].as<std::string>())) {
                tc.with_declared_scales(std::move(*scales));
            }
        }

        // Input tables, typed by the schema the plan reads them with and read when the case
        // runs (TestCase::load_input_data)
        if (tc_node["inputTables"]) {
            std::unordered_map<std::string, std::vector<SchemaColumn>> schemas;
            if (tc_node["planJson"]) {
                auto plan_path = base_dir / tc_node["planJson"].as<std::string>();
                std::ifstream pf(plan_path);
                if (!pf) throw LoaderError("test case " + id + ": cannot read " + plan_path.string());
                std::stringstream text;
                text << pf.rdbuf();
                try {
                    schemas = named_table_schemas(text.str());
                } catch (const std::runtime_error& e) {
                    throw LoaderError("test case " + id + ": " + e.what());
                }
            }
            for (const auto& tbl_node : tc_node["inputTables"]) {
                std::string tbl_name = tbl_node["name"].as<std::string>();
                InputTable input{tbl_name, base_dir / tbl_node["file"].as<std::string>(), std::nullopt};
                for (const auto& [plan_name, columns] : schemas) {
                    if (lower(plan_name) == lower(tbl_name)) {
                        input.name = plan_name;
                        input.columns = columns;
                    }
                }
                tc.with_input_table(std::move(input));
            }
        }

        // Expected output
        if (tc_node["expectedOutput"]) {
            std::string exp_rel = tc_node["expectedOutput"].as<std::string>();
            std::filesystem::path exp_path = base_dir / exp_rel;
            if (std::filesystem::exists(exp_path)) {
                tc.with_expected_output(load_csv(exp_path));
            }
        }

        suite->add_test_case(std::move(tc));
    }

    return suite;
}

// ---------------------------------------------------------------------------
// JsonTestSuiteLoader::load — minimal stub (not used in TPC-H path)
// ---------------------------------------------------------------------------

TestSuitePtr JsonTestSuiteLoader::load(const std::filesystem::path& path) {
    throw LoaderError("JSON loader not implemented. Use YamlTestSuiteLoader.");
}

} // namespace substrait::compliance
