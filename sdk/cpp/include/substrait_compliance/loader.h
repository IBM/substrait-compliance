#pragma once

#include "test_suite.h"
#include "error.h"
#include <string>
#include <memory>
#include <filesystem>

namespace substrait::compliance {

/**
 * @brief Abstract base class for test suite loaders
 */
class TestSuiteLoader {
public:
    virtual ~TestSuiteLoader() = default;
    
    /**
     * @brief Load a test suite from a file
     * 
     * @param path Path to the test suite file
     * @return Loaded test suite
     * @throws LoaderError if loading fails
     */
    virtual TestSuitePtr load(const std::filesystem::path& path) = 0;
    
    /**
     * @brief Check if this loader supports the given file
     * 
     * @param path Path to check
     * @return true if this loader can handle the file
     */
    virtual bool supports(const std::filesystem::path& path) const = 0;
};

/**
 * @brief Loader for YAML-based test suites
 */
class YamlTestSuiteLoader : public TestSuiteLoader {
public:
    YamlTestSuiteLoader() = default;
    
    /**
     * @brief Load a test suite from a YAML file
     * 
     * @param path Path to the YAML file (typically metadata.yaml)
     * @return Loaded test suite
     * @throws LoaderError if loading fails
     */
    TestSuitePtr load(const std::filesystem::path& path) override;
    
    /**
     * @brief Check if file is a YAML file
     */
    bool supports(const std::filesystem::path& path) const override {
        auto ext = path.extension().string();
        return ext == ".yaml" || ext == ".yml";
    }

private:
    /**
     * @brief Load a Substrait plan from file
     */
    std::vector<uint8_t> load_plan(const std::filesystem::path& plan_path);
    
    /**
     * @brief Load table data from CSV file
     */
    TableData load_csv(const std::filesystem::path& csv_path);
    
    /**
     * @brief Parse YAML metadata file
     */
    TestSuiteMetadata parse_metadata(const std::filesystem::path& path);
};

/**
 * @brief Loader for JSON-based test suites
 */
/**
 * @brief Read a CSV typed by the columns the plan reads it with (an input table)
 *
 * As scripts/run_plan_compliance.py reads input tables for the leaderboard: a first row that
 * names the plan's columns is a header and skipped; values are trimmed; an empty value or
 * "null" is NULL. integer is int32_t, bigint int64_t, float float, double double, boolean
 * bool; a decimal stays exact as its text (the column type keeps precision and scale, e.g.
 * decimal(15,2)); dates and strings are text. Throws LoaderError for a value that does not
 * parse as its column's type or a row with the wrong number of values.
 */
TableData load_input_table(const std::filesystem::path& csv_path, const std::vector<SchemaColumn>& columns);

/**
 * @brief Read a CSV with a typed header (name:type|...), or untyped columns column_1.. of
 *        text, as expected outputs are read
 */
TableData load_typed_csv(const std::filesystem::path& csv_path);

class JsonTestSuiteLoader : public TestSuiteLoader {
public:
    JsonTestSuiteLoader() = default;
    
    /**
     * @brief Load a test suite from a JSON file
     */
    TestSuitePtr load(const std::filesystem::path& path) override;
    
    /**
     * @brief Check if file is a JSON file
     */
    bool supports(const std::filesystem::path& path) const override {
        return path.extension() == ".json";
    }
};

/**
 * @brief Auto-detecting loader that tries multiple formats
 */
class AutoTestSuiteLoader : public TestSuiteLoader {
public:
    AutoTestSuiteLoader() {
        register_loader(std::make_unique<YamlTestSuiteLoader>());
        register_loader(std::make_unique<JsonTestSuiteLoader>());
    }
    
    /**
     * @brief Register a custom loader
     */
    void register_loader(std::unique_ptr<TestSuiteLoader> loader) {
        loaders_.push_back(std::move(loader));
    }
    
    /**
     * @brief Load using the first compatible loader
     */
    TestSuitePtr load(const std::filesystem::path& path) override {
        for (const auto& loader : loaders_) {
            if (loader->supports(path)) {
                return loader->load(path);
            }
        }
        throw LoaderError("No loader found for file: " + path.string());
    }
    
    /**
     * @brief Check if any loader supports the file
     */
    bool supports(const std::filesystem::path& path) const override {
        for (const auto& loader : loaders_) {
            if (loader->supports(path)) {
                return true;
            }
        }
        return false;
    }

private:
    std::vector<std::unique_ptr<TestSuiteLoader>> loaders_;
};

/**
 * @brief Helper function to load a test suite with auto-detection
 */
inline TestSuitePtr load_test_suite(const std::filesystem::path& path) {
    AutoTestSuiteLoader loader;
    return loader.load(path);
}

} // namespace substrait::compliance

