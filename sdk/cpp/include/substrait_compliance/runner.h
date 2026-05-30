#pragma once

#include "engine.h"
#include "test_suite.h"
#include "result.h"
#include "comparator.h"
#include <memory>
#include <functional>
#include <chrono>

namespace substrait::compliance {

/**
 * @brief Configuration for test execution
 */
struct RunnerConfig {
    bool stop_on_first_failure = false;
    bool validate_plans = true;
    bool compare_results = true;
    int max_parallel_tests = 1;  // Sequential by default
    int timeout_seconds = 300;    // 5 minutes default
    
    RunnerConfig() = default;
    
    RunnerConfig& with_stop_on_failure(bool stop) {
        stop_on_first_failure = stop;
        return *this;
    }
    
    RunnerConfig& with_validation(bool validate) {
        validate_plans = validate;
        return *this;
    }
    
    RunnerConfig& with_comparison(bool compare) {
        compare_results = compare;
        return *this;
    }
    
    RunnerConfig& with_parallelism(int threads) {
        max_parallel_tests = threads;
        return *this;
    }
    
    RunnerConfig& with_timeout(int seconds) {
        timeout_seconds = seconds;
        return *this;
    }
};

/**
 * @brief Callback for test progress updates
 */
using ProgressCallback = std::function<void(
    const std::string& test_id,
    size_t current,
    size_t total
)>;

/**
 * @brief Executes compliance tests against an engine
 */
class ComplianceRunner {
public:
    /**
     * @brief Construct a runner with an engine
     */
    explicit ComplianceRunner(EnginePtr engine)
        : engine_(std::move(engine))
        , config_()
        , comparator_(std::make_unique<ResultComparator>()) {}
    
    /**
     * @brief Construct with engine and config
     */
    ComplianceRunner(EnginePtr engine, RunnerConfig config)
        : engine_(std::move(engine))
        , config_(std::move(config))
        , comparator_(std::make_unique<ResultComparator>()) {}
    
    /**
     * @brief Run all tests in a test suite
     * 
     * @param suite Test suite to execute
     * @return Aggregated report of all test results
     */
    ComplianceReport run_test_suite(const TestSuite& suite);
    
    /**
     * @brief Run all tests with progress callback
     */
    ComplianceReport run_test_suite(
        const TestSuite& suite,
        ProgressCallback callback
    );
    
    /**
     * @brief Run a single test case
     * 
     * @param test_case Test to execute
     * @return Result of the test execution
     */
    ComplianceResult run_test_case(const TestCase& test_case);
    
    /**
     * @brief Set progress callback
     */
    void set_progress_callback(ProgressCallback callback) {
        progress_callback_ = std::move(callback);
    }
    
    /**
     * @brief Get runner configuration
     */
    const RunnerConfig& config() const { return config_; }
    RunnerConfig& config() { return config_; }
    
    /**
     * @brief Get the engine being tested
     */
    const EnginePtr& engine() const { return engine_; }

private:
    /**
     * @brief Execute a test with timing
     */
    ComplianceResult execute_with_timing(const TestCase& test_case);
    
    /**
     * @brief Validate a plan before execution
     */
    ComplianceResult validate_test_plan(const TestCase& test_case);
    
    /**
     * @brief Compare actual output with expected output
     */
    ComplianceResult compare_outputs(
        const TestCase& test_case,
        const ComplianceResult& result
    );
    
    /**
     * @brief Notify progress callback if set
     */
    void notify_progress(const std::string& test_id, size_t current, size_t total);
    
    EnginePtr engine_;
    RunnerConfig config_;
    std::unique_ptr<ResultComparator> comparator_;
    ProgressCallback progress_callback_;
};

/**
 * @brief Builder for creating configured runners
 */
class RunnerBuilder {
public:
    explicit RunnerBuilder(EnginePtr engine)
        : engine_(std::move(engine)) {}
    
    RunnerBuilder& stop_on_failure(bool stop = true) {
        config_.stop_on_first_failure = stop;
        return *this;
    }
    
    RunnerBuilder& validate_plans(bool validate = true) {
        config_.validate_plans = validate;
        return *this;
    }
    
    RunnerBuilder& compare_results(bool compare = true) {
        config_.compare_results = compare;
        return *this;
    }
    
    RunnerBuilder& parallel(int threads) {
        config_.max_parallel_tests = threads;
        return *this;
    }
    
    RunnerBuilder& timeout(int seconds) {
        config_.timeout_seconds = seconds;
        return *this;
    }
    
    RunnerBuilder& on_progress(ProgressCallback callback) {
        progress_callback_ = std::move(callback);
        return *this;
    }
    
    ComplianceRunner build() {
        auto runner = ComplianceRunner(engine_, config_);
        if (progress_callback_) {
            runner.set_progress_callback(progress_callback_);
        }
        return runner;
    }

private:
    EnginePtr engine_;
    RunnerConfig config_;
    ProgressCallback progress_callback_;
};

} // namespace substrait::compliance

// Made with Bob
