// The decimal scales a plan declares, as scripts/run_plan_compliance.py works them out.

#include <gtest/gtest.h>
#include <substrait_compliance/loader.h>
#include <substrait_compliance/plan_types.h>

#include <cstdio>
#include <string>

using namespace substrait::compliance;

namespace {
const std::string TPCH = std::string(REPO_ROOT) + "/test-suites/tpch/";
}

TEST(PlanTypesTest, Q01DeclaresDecimalAggregates) {
    auto s = declared_scales_file(TPCH + "plans/q01.json");
    ASSERT_TRUE(s.has_value());
    std::vector<std::optional<int>> expected = {std::nullopt, std::nullopt, 2, 2, 4, 6, 2, 2, 2, std::nullopt};
    EXPECT_EQ(*s, expected);
}

TEST(PlanTypesTest, BothEncodingsDeclareTheSameScales) {
    for (int q = 1; q <= 22; ++q) {
        char name[16];
        std::snprintf(name, sizeof(name), "q%02d.json", q);
        auto current = declared_scales_file(TPCH + "plans/" + name);
        ASSERT_TRUE(current.has_value()) << name;
        EXPECT_EQ(declared_scales_file(TPCH + "plans-substrait-0.81/" + name), current) << name;
    }
}

TEST(PlanTypesTest, UnknownRelationGivesNoScales) {
    EXPECT_FALSE(declared_scales(R"({"relations":[{"root":{"input":{"exchange":{}}}}]})").has_value());
}

TEST(PlanTypesTest, LoaderAttachesScales) {
    YamlTestSuiteLoader loader;
    auto suite = loader.load(TPCH + "metadata.yaml");
    const auto& q01 = suite->test_cases().front();
    ASSERT_EQ(q01.id(), "q01");
    ASSERT_EQ(q01.declared_scales().size(), 10u);
    EXPECT_EQ(q01.declared_scales()[6], std::optional<int>(2));
}
