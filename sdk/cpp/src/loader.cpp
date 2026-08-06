// loader.cpp — YamlTestSuiteLoader implementation

#include "substrait_compliance/loader.h"
#include "substrait_compliance/comparator.h"  // cell_value::parse
#include "substrait_compliance/error.h"
#include <yaml-cpp/yaml.h>
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <algorithm>

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

        // Input tables
        if (tc_node["inputTables"]) {
            TableCollection input;
            for (const auto& tbl_node : tc_node["inputTables"]) {
                std::string tbl_name = tbl_node["name"].as<std::string>();
                std::string tbl_file = tbl_node["file"].as<std::string>();
                std::filesystem::path tbl_path = base_dir / tbl_file;
                if (std::filesystem::exists(tbl_path)) {
                    input[tbl_name] = load_csv(tbl_path);
                }
            }
            tc.with_input_data(std::move(input));
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
