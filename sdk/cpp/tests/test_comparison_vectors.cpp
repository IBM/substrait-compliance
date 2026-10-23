// Query-output comparison: the vectors shared with the leaderboard's comparators
// (sdk/conformance/comparison-vectors.json), checked against describe_mismatch.

#include <gtest/gtest.h>
#include <substrait_compliance/comparator.h>
#include <yaml-cpp/yaml.h>

#include <string>

using namespace substrait::compliance;

namespace {

// JSON read as YAML: a quoted scalar is a string, a plain one a number, bool or null.
CellValue decode(const YAML::Node& v) {
    if (v.IsNull()) return nullptr;
    if (v.IsMap()) {
        if (v["double"]) return std::stod(v["double"].as<std::string>());
        if (v["decimal"]) return std::stod(v["decimal"].as<std::string>());
        throw std::runtime_error("unexpected value");
    }
    std::string s = v.as<std::string>();
    if (v.Tag() == "!") return s;  // quoted in the JSON
    if (s == "true" || s == "false") return s == "true";
    if (s.find_first_of(".eE") == std::string::npos) return static_cast<int64_t>(std::stoll(s));
    return std::stod(s);
}

TableData table(const YAML::Node& t) {
    TableData td;
    std::vector<ColumnMetadata> cols;
    for (size_t i = 0; i < t["names"].size(); ++i) {
        cols.emplace_back(t["names"][i].as<std::string>(), t["types"][i].as<std::string>());
    }
    td.set_columns(cols);
    for (const auto& r : t["rows"]) {
        Row row;
        for (const auto& v : r) row.push_back(decode(v));
        td.add_row(row);
    }
    return td;
}

} // namespace

TEST(ComparisonVectorsTest, DescribeMismatchAgreesWithEverySharedVector) {
    YAML::Node doc = YAML::LoadFile(std::string(REPO_ROOT) + "/sdk/conformance/comparison-vectors.json");
    const YAML::Node vectors = doc["vectors"];
    ASSERT_GT(vectors.size(), 0u);
    ResultComparator comparator;
    for (const auto& v : vectors) {
        std::vector<std::optional<int>> scales;
        if (v["declaredScales"]) {
            for (const auto& s : v["declaredScales"]) {
                scales.push_back(s.IsNull() ? std::nullopt : std::optional<int>(s.as<int>()));
            }
        }
        auto mismatch = comparator.describe_mismatch(table(v["expected"]), table(v["actual"]), scales);
        EXPECT_EQ(!mismatch.has_value(), v["match"].as<bool>())
            << v["name"].as<std::string>() << ": " << mismatch.value_or("");
    }
}

TEST(ComparisonVectorsTest, TextParsesAsANumberOnlyWhenItIsOne) {
    EXPECT_EQ(std::get<std::string>(cell_value::parse("13-555", "integer")), "13-555");
    EXPECT_EQ(std::get<int32_t>(cell_value::parse("13", "integer")), 13);
    EXPECT_EQ(std::get<double>(cell_value::parse("25.5", "decimal(15,2)")), 25.5);
}
