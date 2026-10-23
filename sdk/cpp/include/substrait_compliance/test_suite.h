#pragma once

#include "table_data.h"
#include "plan_types.h"
#include <algorithm>
#include <filesystem>
#include <string>
#include <vector>
#include <memory>
#include <optional>

namespace substrait::compliance {

/**
 * @brief Metadata for a test suite
 */
struct TestSuiteMetadata {
    std::string name;
    std::string version;
    std::string description;
    std::vector<std::string> tags;
    
    TestSuiteMetadata() = default;
    
    TestSuiteMetadata(std::string n, std::string v = "1.0")
        : name(std::move(n)), version(std::move(v)) {}
    
    TestSuiteMetadata& with_description(std::string desc) {
        description = std::move(desc);
        return *this;
    }
    
    TestSuiteMetadata& add_tag(std::string tag) {
        tags.push_back(std::move(tag));
        return *this;
    }
};

/**
 * @brief A single test case within a test suite
 */
/**
 * @brief An input table read from a file when its case runs (TestCase::load_input_data)
 */
struct InputTable {
    std::string name;               ///< the table name the plan reads
    std::filesystem::path file;
    /// the columns the plan reads it with; nullopt to read the file like an expected output
    std::optional<std::vector<SchemaColumn>> columns;
};

class TestCase {
public:
    TestCase(std::string id, std::vector<uint8_t> plan)
        : id_(std::move(id))
        , plan_bytes_(std::move(plan)) {}
    
    // Getters
    const std::string& id() const { return id_; }
    const std::vector<uint8_t>& plan_bytes() const { return plan_bytes_; }
    const TableCollection& input_data() const { return input_data_; }
    const std::optional<TableData>& expected_output() const { return expected_output_; }
    const std::optional<std::string>& description() const { return description_; }
    const std::vector<std::string>& tags() const { return tags_; }
    const std::vector<std::optional<int>>& declared_scales() const { return declared_scales_; }
    /**
     * @brief Input tables read from files when the case runs, added to input_data(). The
     *        loader fills these rather than input_data() so a suite's data is not held in
     *        memory for every case at once (TPC-DS reads about 1.4 GB of CSV across 99 cases).
     */
    const std::vector<InputTable>& input_tables() const { return input_tables_; }

    /**
     * @brief input_data() plus each of input_tables() read from its file (see loader.h).
     *        Throws LoaderError when a file cannot be read.
     */
    TableCollection load_input_data() const;
    
    // Setters (builder pattern)
    TestCase& with_input_data(TableCollection data) {
        input_data_ = std::move(data);
        return *this;
    }
    
    TestCase& with_expected_output(TableData output) {
        expected_output_ = std::move(output);
        return *this;
    }
    
    TestCase& with_description(std::string desc) {
        description_ = std::move(desc);
        return *this;
    }
    
    /**
     * @brief Per output column, the scale of the decimal type the plan declares (nullopt for
     *        other columns); see plan_types.h. Empty when unknown.
     */
    TestCase& with_declared_scales(std::vector<std::optional<int>> scales) {
        declared_scales_ = std::move(scales);
        return *this;
    }

    TestCase& with_input_table(InputTable table) {
        input_tables_.push_back(std::move(table));
        return *this;
    }

    TestCase& add_tag(std::string tag) {
        tags_.push_back(std::move(tag));
        return *this;
    }
    
    /**
     * @brief Check if test has a specific tag
     */
    bool has_tag(const std::string& tag) const {
        return std::find(tags_.begin(), tags_.end(), tag) != tags_.end();
    }

private:
    std::string id_;
    std::vector<uint8_t> plan_bytes_;
    TableCollection input_data_;
    std::optional<TableData> expected_output_;
    std::optional<std::string> description_;
    std::vector<std::string> tags_;
    std::vector<std::optional<int>> declared_scales_;
    std::vector<InputTable> input_tables_;
};

/**
 * @brief A collection of related test cases
 */
class TestSuite {
public:
    TestSuite() = default;
    
    explicit TestSuite(TestSuiteMetadata metadata)
        : metadata_(std::move(metadata)) {}
    
    /**
     * @brief Add a test case to the suite
     */
    void add_test_case(TestCase test_case) {
        test_cases_.push_back(std::move(test_case));
    }
    
    /**
     * @brief Get all test cases
     */
    const std::vector<TestCase>& test_cases() const { return test_cases_; }
    
    /**
     * @brief Get test suite metadata
     */
    const TestSuiteMetadata& metadata() const { return metadata_; }
    TestSuiteMetadata& metadata() { return metadata_; }
    
    /**
     * @brief Get number of test cases
     */
    size_t size() const { return test_cases_.size(); }
    
    /**
     * @brief Check if suite is empty
     */
    bool empty() const { return test_cases_.empty(); }
    
    /**
     * @brief Get test cases with a specific tag
     */
    std::vector<const TestCase*> get_tests_by_tag(const std::string& tag) const {
        std::vector<const TestCase*> result;
        for (const auto& test : test_cases_) {
            if (test.has_tag(tag)) {
                result.push_back(&test);
            }
        }
        return result;
    }
    
    /**
     * @brief Find a test case by ID
     */
    const TestCase* find_test(const std::string& id) const {
        for (const auto& test : test_cases_) {
            if (test.id() == id) {
                return &test;
            }
        }
        return nullptr;
    }

private:
    TestSuiteMetadata metadata_;
    std::vector<TestCase> test_cases_;
};

/**
 * @brief Smart pointer type for test suites
 */
using TestSuitePtr = std::shared_ptr<TestSuite>;

/**
 * @brief Create a shared pointer to a test suite
 */
inline TestSuitePtr make_test_suite(TestSuiteMetadata metadata = TestSuiteMetadata()) {
    return std::make_shared<TestSuite>(std::move(metadata));
}

} // namespace substrait::compliance

