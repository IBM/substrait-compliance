// test_pass_through.cpp — pass-through integration tests for runner + comparator + loader
//
// These tests mirror the Java ComplianceRunnerComparatorTest: a PassThroughEngine
// returns whatever TableData it's given as expected output, so 100% of tests PASS.
// A MismatchEngine returns a row with the wrong value, producing FAILED.
// Missing expected output → SKIPPED (never PASSED).

#include <gtest/gtest.h>
#include <substrait_compliance.h>

using namespace substrait::compliance;

// ---------------------------------------------------------------------------
// PassThroughEngine: echoes the expected output back as actual output.
// The runner hands us plan_bytes + input_data; we embed a pointer to the
// expected TableData via a callback set before each test.
// ---------------------------------------------------------------------------

class PassThroughEngine : public ComplianceEngine {
public:
    // Call before each test to tell the engine what to return
    void set_expected(const TableData* expected) { expected_ = expected; }

    EngineInfo get_info() const override {
        return EngineInfo("PassThrough", "1.0", "Test");
    }

    EngineCapabilities get_capabilities() const override {
        return EngineCapabilities{};
    }

    ComplianceResult execute_plan(
        const std::vector<uint8_t>&,
        const TableCollection&) override {
        ComplianceResult r("", TestStatus::PASSED);
        if (expected_) r.with_output(*expected_);
        return r;
    }

    ComplianceResult validate_plan(const std::vector<uint8_t>&) override {
        return ComplianceResult("", TestStatus::PASSED);
    }

private:
    const TableData* expected_ = nullptr;
};

// ---------------------------------------------------------------------------
// MismatchEngine: always returns a single integer row with value 999.
// ---------------------------------------------------------------------------

class MismatchEngine : public ComplianceEngine {
public:
    EngineInfo get_info() const override {
        return EngineInfo("Mismatch", "1.0", "Test");
    }
    EngineCapabilities get_capabilities() const override { return {}; }

    ComplianceResult execute_plan(
        const std::vector<uint8_t>&,
        const TableCollection&) override {
        TableData out({ColumnMetadata{"v", "double"}});
        out.add_row({static_cast<double>(999.0)});
        return ComplianceResult("", TestStatus::PASSED)
            .with_output(std::move(out));
    }

    ComplianceResult validate_plan(const std::vector<uint8_t>&) override {
        return ComplianceResult("", TestStatus::PASSED);
    }
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

static TableData make_table(double v) {
    TableData t({ColumnMetadata{"v", "double"}});
    t.add_row({v});
    return t;
}

static TestSuite make_suite_with_expected(const std::string& id, TableData expected) {
    TestSuite suite(TestSuiteMetadata{"test_suite", "1.0"});
    std::vector<uint8_t> dummy_plan = {0x01, 0x02, 0x03};
    TestCase tc(id, dummy_plan);
    tc.with_expected_output(std::move(expected));
    suite.add_test_case(std::move(tc));
    return suite;
}

static TestSuite make_suite_no_expected(const std::string& id) {
    TestSuite suite(TestSuiteMetadata{"test_suite", "1.0"});
    std::vector<uint8_t> dummy_plan = {0x01};
    suite.add_test_case(TestCase(id, dummy_plan));
    return suite;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

TEST(PassThroughTest, IdenticalOutputPasses) {
    auto engine = make_engine<PassThroughEngine>();

    TableData expected = make_table(42.0);
    auto eng = std::static_pointer_cast<PassThroughEngine>(engine);
    eng->set_expected(&expected);

    TestSuite suite = make_suite_with_expected("t1", make_table(42.0));
    // Give the engine the same table
    eng->set_expected(&suite.test_cases()[0].expected_output().value());

    ComplianceRunner runner(engine);
    ComplianceReport report = runner.run_test_suite(suite);

    EXPECT_EQ(report.total_count(), 1u);
    EXPECT_EQ(report.passed_count(), 1u);
    EXPECT_EQ(report.failed_count(), 0u);
    EXPECT_DOUBLE_EQ(report.pass_rate(), 100.0);
}

TEST(PassThroughTest, ValueMismatchFails) {
    auto engine = make_engine<MismatchEngine>();

    TestSuite suite = make_suite_with_expected("t2", make_table(42.0));

    ComplianceRunner runner(engine);
    ComplianceReport report = runner.run_test_suite(suite);

    EXPECT_EQ(report.total_count(), 1u);
    EXPECT_EQ(report.failed_count(), 1u);
    EXPECT_EQ(report.passed_count(), 0u);
}

TEST(PassThroughTest, EpsilonWithinTolerance) {
    auto engine = make_engine<PassThroughEngine>();

    // Build expected with 1.0 but engine will return 1.0 + 5e-10 (within 1e-9)
    TableData expected({ColumnMetadata{"v", "double"}});
    expected.add_row({static_cast<double>(1.0)});

    TableData near({ColumnMetadata{"v", "double"}});
    near.add_row({static_cast<double>(1.0 + 5e-10)});

    TestSuite suite(TestSuiteMetadata{"s", "1.0"});
    std::vector<uint8_t> plan = {0x01};
    TestCase tc("eps_in", plan);
    tc.with_expected_output(expected);
    suite.add_test_case(std::move(tc));

    auto eng = std::static_pointer_cast<PassThroughEngine>(engine);
    eng->set_expected(&near); // engine returns the "near" value

    ComplianceRunner runner(engine);
    ComplianceReport report = runner.run_test_suite(suite);

    EXPECT_EQ(report.passed_count(), 1u) << "Should PASS: delta 5e-10 < epsilon 1e-9";
}

TEST(PassThroughTest, EpsilonOutsideTolerance) {
    auto engine = make_engine<PassThroughEngine>();

    TableData expected({ColumnMetadata{"v", "double"}});
    expected.add_row({static_cast<double>(1.0)});

    TableData far({ColumnMetadata{"v", "double"}});
    far.add_row({static_cast<double>(1.0 + 2e-9)});

    TestSuite suite(TestSuiteMetadata{"s", "1.0"});
    std::vector<uint8_t> plan = {0x01};
    TestCase tc("eps_out", plan);
    tc.with_expected_output(expected);
    suite.add_test_case(std::move(tc));

    auto eng = std::static_pointer_cast<PassThroughEngine>(engine);
    eng->set_expected(&far); // engine returns too-far value

    ComplianceRunner runner(engine);
    ComplianceReport report = runner.run_test_suite(suite);

    EXPECT_EQ(report.failed_count(), 1u) << "Should FAIL: delta 2e-9 > epsilon 1e-9";
}

TEST(PassThroughTest, MissingExpectedOutputIsSkippedNotPassed) {
    auto engine = make_engine<PassThroughEngine>();
    auto eng = std::static_pointer_cast<PassThroughEngine>(engine);
    eng->set_expected(nullptr); // engine returns nothing

    TestSuite suite = make_suite_no_expected("no_expected");

    ComplianceRunner runner(engine);
    ComplianceReport report = runner.run_test_suite(suite);

    EXPECT_EQ(report.total_count(), 1u);
    EXPECT_EQ(report.passed_count(), 0u)
        << "Missing expected output must NOT count as passed";
    EXPECT_EQ(report.skipped_count(), 1u)
        << "Missing expected output must produce SKIPPED";
}

// ---------------------------------------------------------------------------
// ResultComparator unit tests (comparator.cpp)
// ---------------------------------------------------------------------------

TEST(ComparatorTest, NullEqualsNull) {
    ResultComparator cmp;
    EXPECT_TRUE(cmp.compare_values(nullptr, nullptr, "string"));
}

TEST(ComparatorTest, NullNotEqualValue) {
    ResultComparator cmp;
    CellValue v = std::string("x");
    EXPECT_FALSE(cmp.compare_values(nullptr, v, "string"));
}

TEST(ComparatorTest, IntegerEquality) {
    ResultComparator cmp;
    CellValue a = static_cast<int32_t>(7);
    CellValue b = static_cast<int32_t>(7);
    EXPECT_TRUE(cmp.compare_values(a, b, "integer"));
}

TEST(ComparatorTest, DoubleEpsilonInTolerance) {
    ResultComparator cmp;
    CellValue a = 1.0;
    CellValue b = 1.0 + 5e-10;
    EXPECT_TRUE(cmp.compare_values(a, b, "double"));
}

TEST(ComparatorTest, DoubleEpsilonOutOfTolerance) {
    ResultComparator cmp;
    CellValue a = 1.0;
    CellValue b = 1.0 + 2e-9;
    EXPECT_FALSE(cmp.compare_values(a, b, "double"));
}

TEST(ComparatorTest, StringEquality) {
    ResultComparator cmp;
    CellValue a = std::string("hello");
    CellValue b = std::string("hello");
    EXPECT_TRUE(cmp.compare_values(a, b, "string"));
    CellValue c = std::string("world");
    EXPECT_FALSE(cmp.compare_values(a, c, "string"));
}

TEST(ComparatorTest, RowCountMismatch) {
    ResultComparator cmp;
    TableData actual({ColumnMetadata{"id", "integer"}});
    actual.add_row({static_cast<int32_t>(1)});

    TableData expected({ColumnMetadata{"id", "integer"}});
    expected.add_row({static_cast<int32_t>(1)});
    expected.add_row({static_cast<int32_t>(2)});

    auto result = cmp.compare_tables(actual, expected);
    EXPECT_FALSE(result.matches);
}
