#include <gtest/gtest.h>
#include <substrait_compliance/result.h>

using namespace substrait::compliance;

TEST(ComplianceResultTest, BasicConstruction) {
    ComplianceResult result("test1", TestStatus::PASSED);
    
    EXPECT_EQ(result.test_id(), "test1");
    EXPECT_EQ(result.status(), TestStatus::PASSED);
    EXPECT_TRUE(result.is_passed());
    EXPECT_FALSE(result.is_failed());
}

TEST(ComplianceResultTest, WithError) {
    ComplianceResult result("test2", TestStatus::FAILED);
    result.with_error("Something went wrong");
    
    EXPECT_TRUE(result.is_failed());
    ASSERT_TRUE(result.error_message().has_value());
    EXPECT_EQ(*result.error_message(), "Something went wrong");
}

TEST(ComplianceResultTest, WithOutput) {
    TableData output;
    output.set_columns({{"id", "INTEGER"}, {"name", "STRING"}});
    output.add_row({1, std::string("Alice")});
    
    ComplianceResult result("test3", TestStatus::PASSED);
    result.with_output(std::move(output));
    
    ASSERT_TRUE(result.output_data().has_value());
    EXPECT_EQ(result.output_data()->row_count(), 1);
}

TEST(ComplianceReportTest, Aggregation) {
    ComplianceReport report;
    
    report.add_result(ComplianceResult("test1", TestStatus::PASSED));
    report.add_result(ComplianceResult("test2", TestStatus::FAILED));
    report.add_result(ComplianceResult("test3", TestStatus::PASSED));
    
    EXPECT_EQ(report.total_count(), 3);
    EXPECT_EQ(report.passed_count(), 2);
    EXPECT_EQ(report.failed_count(), 1);
    EXPECT_DOUBLE_EQ(report.pass_rate(), 66.666666666666671);
    EXPECT_FALSE(report.all_passed());
}

TEST(ComplianceReportTest, AllPassed) {
    ComplianceReport report;
    
    report.add_result(ComplianceResult("test1", TestStatus::PASSED));
    report.add_result(ComplianceResult("test2", TestStatus::PASSED));
    
    EXPECT_TRUE(report.all_passed());
    EXPECT_DOUBLE_EQ(report.pass_rate(), 100.0);
}

TEST(TestStatusTest, ToString) {
    EXPECT_EQ(to_string(TestStatus::PASSED), "PASSED");
    EXPECT_EQ(to_string(TestStatus::FAILED), "FAILED");
    EXPECT_EQ(to_string(TestStatus::SKIPPED), "SKIPPED");
    EXPECT_EQ(to_string(TestStatus::ERROR), "ERROR");
    EXPECT_EQ(to_string(TestStatus::UNSUPPORTED), "UNSUPPORTED");
}

// Made with Bob
