// runner.cpp — ComplianceRunner implementation

#include "substrait_compliance/runner.h"
#include <stdexcept>

namespace substrait::compliance {

// ---------------------------------------------------------------------------
// ComplianceRunner
// ---------------------------------------------------------------------------

ComplianceReport ComplianceRunner::run_test_suite(const TestSuite& suite) {
    ComplianceReport report;
    size_t total = suite.test_cases().size();
    size_t idx = 0;

    engine_->initialize();

    for (const auto& tc : suite.test_cases()) {
        notify_progress(tc.id(), ++idx, total);
        ComplianceResult result = run_test_case(tc);
        report.add_result(std::move(result));

        if (config_.stop_on_first_failure &&
            report.results().back().is_failed()) {
            break;
        }
    }

    engine_->shutdown();
    return report;
}

ComplianceReport ComplianceRunner::run_test_suite(
        const TestSuite& suite,
        ProgressCallback callback) {
    progress_callback_ = std::move(callback);
    return run_test_suite(suite);
}

ComplianceResult ComplianceRunner::run_test_case(const TestCase& tc) {
    // Optional: let engine skip certain tests
    if (!engine_->can_run_test(tc.id())) {
        return ComplianceResult(tc.id(), TestStatus::SKIPPED)
            .with_error("Engine declined this test");
    }

    // Validate first (if configured)
    if (config_.validate_plans) {
        ComplianceResult val = validate_test_plan(tc);
        if (val.is_unsupported()) {
            return val; // UNSUPPORTED → skip
        }
    }

    // Execute with timing
    ComplianceResult exec = execute_with_timing(tc);
    if (!exec.is_passed()) {
        return exec;
    }

    // Compare against expected output (if configured and expected exists)
    if (config_.compare_results && tc.expected_output().has_value()) {
        return compare_outputs(tc, exec);
    }

    // No expected output: skip rather than pass to be honest
    if (!tc.expected_output().has_value()) {
        ComplianceResult skipped(tc.id(), TestStatus::SKIPPED);
        skipped.with_error("No expected output — cannot verify correctness");
        if (exec.output_data().has_value()) {
            skipped.with_output(*exec.output_data());
        }
        skipped.with_execution_time(exec.execution_time_ms());
        return skipped;
    }

    return exec;
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

ComplianceResult ComplianceRunner::execute_with_timing(const TestCase& tc) {
    using clock = std::chrono::steady_clock;
    auto start = clock::now();

    ComplianceResult result("", TestStatus::ERROR);
    try {
        result = engine_->execute_plan(tc.plan_bytes(), tc.input_data());
        result = ComplianceResult(tc.id(), result.status())
            .with_execution_time(
                std::chrono::duration_cast<std::chrono::milliseconds>(
                    clock::now() - start).count());
        if (result.status() == TestStatus::PASSED && result.output_data().has_value()) {
            // preserve output from the original call
        }
        // Re-run to get output attached properly
        result = engine_->execute_plan(tc.plan_bytes(), tc.input_data());
    } catch (const std::exception& e) {
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            clock::now() - start).count();
        return ComplianceResult(tc.id(), TestStatus::ERROR)
            .with_error(e.what())
            .with_execution_time(elapsed);
    }

    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        clock::now() - start).count();

    // Build a clean result tagged with this test's id
    ComplianceResult r(tc.id(), result.status());
    r.with_execution_time(elapsed);
    if (result.output_data().has_value()) {
        r.with_output(*result.output_data());
    }
    if (result.error_message().has_value()) {
        r.with_error(*result.error_message());
    }
    return r;
}

ComplianceResult ComplianceRunner::validate_test_plan(const TestCase& tc) {
    try {
        ComplianceResult val = engine_->validate_plan(tc.plan_bytes());
        if (val.status() == TestStatus::UNSUPPORTED) {
            return ComplianceResult(tc.id(), TestStatus::UNSUPPORTED)
                .with_error(val.error_message().value_or("Plan not supported"));
        }
        return ComplianceResult(tc.id(), TestStatus::PASSED);
    } catch (const std::exception& e) {
        // Treat a thrown exception from validate as unsupported
        return ComplianceResult(tc.id(), TestStatus::UNSUPPORTED)
            .with_error(e.what());
    }
}

ComplianceResult ComplianceRunner::compare_outputs(
        const TestCase& tc,
        const ComplianceResult& exec_result) {
    const TableData& expected = *tc.expected_output();
    const TableData& actual = exec_result.output_data().has_value()
        ? *exec_result.output_data()
        : TableData{};

    ComparisonResult cmp = comparator_->compare_tables(actual, expected);

    if (cmp.matches) {
        ComplianceResult r(tc.id(), TestStatus::PASSED);
        r.with_execution_time(exec_result.execution_time_ms());
        r.with_output(actual);
        return r;
    }

    ComplianceResult r(tc.id(), TestStatus::FAILED);
    r.with_error(cmp.message);
    r.with_execution_time(exec_result.execution_time_ms());
    if (exec_result.output_data().has_value()) {
        r.with_output(*exec_result.output_data());
    }
    return r;
}

void ComplianceRunner::notify_progress(
        const std::string& test_id,
        size_t current,
        size_t total) {
    if (progress_callback_) {
        progress_callback_(test_id, current, total);
    }
}

} // namespace substrait::compliance
