// The repository's suites load with input tables typed by the plan, as the leaderboard's
// runner reads them.

#include "substrait_compliance/loader.h"

#include <gtest/gtest.h>

#include <filesystem>
#include <fstream>

using namespace substrait::compliance;

namespace {

std::filesystem::path write_temp(const std::string& name, const std::string& content) {
    auto path = std::filesystem::temp_directory_path() / name;
    std::ofstream(path) << content;
    return path;
}

} // namespace

TEST(SuiteLoaderTest, TpchInputTablesAreTypedByThePlanAndReadWhenTheCaseRuns) {
    auto suite = load_test_suite(std::string(REPO_ROOT) + "/test-suites/tpch/metadata.yaml");
    const TestCase* q01 = nullptr;
    for (const auto& tc : suite->test_cases()) if (tc.id() == "q01") q01 = &tc;
    ASSERT_NE(q01, nullptr);
    EXPECT_TRUE(q01->input_data().empty()) << "files are read when the case runs";
    ASSERT_EQ(q01->input_tables().size(), 1u);
    EXPECT_EQ(q01->input_tables()[0].name, "LINEITEM");

    auto inputs = q01->load_input_data();
    const auto& lineitem = inputs.at("LINEITEM");
    EXPECT_EQ(lineitem.row_count(), 60175u);
    EXPECT_EQ(lineitem.columns()[4].name, "L_QUANTITY");
    EXPECT_EQ(lineitem.columns()[4].type, "decimal(15,2)");
    EXPECT_TRUE(std::holds_alternative<int32_t>(lineitem.get_cell(0, 0)));
    EXPECT_TRUE(std::holds_alternative<std::string>(lineitem.get_cell(0, 4))) << "a decimal stays exact";
    ASSERT_TRUE(q01->expected_output().has_value());
    EXPECT_EQ(q01->expected_output()->row_count(), 4u);
}

TEST(SuiteLoaderTest, InputTableSkipsHeaderHonoursQuotesAndChecksValues) {
    auto file = write_temp("sc_cpp_input.csv", "id,name,ok\n1,\"a, b\",TRUE\n2,null,\n");
    std::vector<SchemaColumn> columns{{"ID", "integer"}, {"NAME", "string"}, {"OK", "boolean"}};
    auto t = load_input_table(file, columns);
    ASSERT_EQ(t.row_count(), 2u);
    EXPECT_EQ(std::get<int32_t>(t.get_cell(0, 0)), 1);
    EXPECT_EQ(std::get<std::string>(t.get_cell(0, 1)), "a, b");
    EXPECT_TRUE(std::get<bool>(t.get_cell(0, 2)));
    EXPECT_TRUE(cell_value::is_null(t.get_cell(1, 1)));
    EXPECT_TRUE(cell_value::is_null(t.get_cell(1, 2)));

    auto bad = write_temp("sc_cpp_bad.csv", "x,y,z\n");
    EXPECT_THROW(load_input_table(bad, columns), LoaderError) << "'x' is not an integer";
    EXPECT_THROW(load_input_table(file, {{"ID", "integer"}}), LoaderError) << "wrong number of values";
}
