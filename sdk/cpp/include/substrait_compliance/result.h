#pragma once

#include "table_data.h"
#include <string>
#include <memory>
#include <optional>
#include <chrono>

namespace substrait::compliance {

/**
 * @brief Test execution status
 */
enum class TestStatus {
    PASSED,
    FAILED,
    SKIPPED,
    ERROR,
    UNSUPPORTED
};

/**
 * @brief Convert TestStatus to string
 */
inline std::string to_string(TestStatus status) {
    switch (status) {
        case TestStatus::PASSED: return "PASSED";
        case TestStatus::FAILED: return "FAILED";
        case TestStatus::SKIPPED: return "SKIPPED";
        case TestStatus::ERROR: return "ERROR";
        case TestStatus::UNSUPPORTED: return "UNSUPPORTED";
        default: return "UNKNOWN";
    }
}

/**
 * @brief Result of a single test case execution
 */
class ComplianceResult {
public:
    ComplianceResult(std::string test_id, TestStatus status)
        : test_id_(std::move(test_id))
        , status_(status)
        , execution_time_ms_(0) {}
    
    // Getters
    const std::string& test_id() const { return test_id_; }
    TestStatus status() const { return status_; }
    const std::optional<TableData>& output_data() const { return output_data_; }
    const std::optional<std::string>& error_message() const { return error_message_; }
    const std::optional<std::string>& error_details() const { return error_details_; }
    int64_t execution_time_ms() const { return execution_time_ms_; }
    
    // Setters (builder pattern)
    ComplianceResult& with_output(TableData data) {
        output_data_ = std::move(data);
        return *this;
    }
    
    ComplianceResult& with_error(std::string message) {
        error_message_ = std::move(message);
        return *this;
    }
    
    ComplianceResult& with_error_details(std::string details) {
        error_details_ = std::move(details);
        return *this;
    }
    
    ComplianceResult& with_execution_time(int64_t ms) {
        execution_time_ms_ = ms;
        return *this;
    }
    
    // Status checks
    bool is_passed() const { return status_ == TestStatus::PASSED; }
    bool is_failed() const { return status_ == TestStatus::FAILED; }
    bool is_skipped() const { return status_ == TestStatus::SKIPPED; }
    bool is_error() const { return status_ == TestStatus::ERROR; }
    bool is_unsupported() const { return status_ == TestStatus::UNSUPPORTED; }

private:
    std::string test_id_;
    TestStatus status_;
    std::optional<TableData> output_data_;
    std::optional<std::string> error_message_;
    std::optional<std::string> error_details_;
    int64_t execution_time_ms_;
};

/**
 * @brief Aggregated report for a test suite execution
 */
class ComplianceReport {
public:
    ComplianceReport() = default;
    
    /**
     * @brief Add a test result to the report
     */
    void add_result(ComplianceResult result) {
        results_.push_back(std::move(result));
    }
    
    /**
     * @brief Get all results
     */
    const std::vector<ComplianceResult>& results() const { return results_; }
    
    /**
     * @brief Get total number of tests
     */
    size_t total_count() const { return results_.size(); }
    
    /**
     * @brief Get number of passed tests
     */
    size_t passed_count() const {
        return count_by_status(TestStatus::PASSED);
    }
    
    /**
     * @brief Get number of failed tests
     */
    size_t failed_count() const {
        return count_by_status(TestStatus::FAILED);
    }
    
    /**
     * @brief Get number of skipped tests
     */
    size_t skipped_count() const {
        return count_by_status(TestStatus::SKIPPED);
    }
    
    /**
     * @brief Get number of error tests
     */
    size_t error_count() const {
        return count_by_status(TestStatus::ERROR);
    }
    
    /**
     * @brief Get number of unsupported tests
     */
    size_t unsupported_count() const {
        return count_by_status(TestStatus::UNSUPPORTED);
    }
    
    /**
     * @brief Calculate pass rate (0-100)
     */
    double pass_rate() const {
        if (results_.empty()) return 0.0;
        return (static_cast<double>(passed_count()) / results_.size()) * 100.0;
    }
    
    /**
     * @brief Get total execution time in milliseconds
     */
    int64_t total_execution_time_ms() const {
        int64_t total = 0;
        for (const auto& result : results_) {
            total += result.execution_time_ms();
        }
        return total;
    }
    
    /**
     * @brief Check if all tests passed
     */
    bool all_passed() const {
        return passed_count() == total_count();
    }
    
    /**
     * @brief Get summary string
     */
    std::string summary() const {
        return "Passed: " + std::to_string(passed_count()) + "/" + 
               std::to_string(total_count()) + 
               " (" + std::to_string(static_cast<int>(pass_rate())) + "%)";
    }

private:
    size_t count_by_status(TestStatus status) const {
        size_t count = 0;
        for (const auto& result : results_) {
            if (result.status() == status) {
                ++count;
            }
        }
        return count;
    }
    
    std::vector<ComplianceResult> results_;
};

} // namespace substrait::compliance

