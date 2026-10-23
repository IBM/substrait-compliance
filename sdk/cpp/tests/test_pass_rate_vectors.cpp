// The pass-rate vectors every SDK shares (sdk/conformance/pass-rate-vectors.csv).
#include <gtest/gtest.h>
#include <substrait_compliance/result.h>

#include <cmath>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

using namespace substrait::compliance;

#ifndef REPO_ROOT
#define REPO_ROOT "../.."
#endif

TEST(PassRateVectors, MatchTheSharedVectors) {
    std::ifstream in(std::string(REPO_ROOT) + "/sdk/conformance/pass-rate-vectors.csv");
    ASSERT_TRUE(in.good()) << "cannot read pass-rate-vectors.csv";
    std::string line;
    bool header = true;
    int rows = 0;
    while (std::getline(in, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.empty() || line[0] == '#') continue;
        if (header) { header = false; continue; }
        std::stringstream ss(line);
        std::string field;
        std::vector<std::string> f;
        while (std::getline(ss, field, ',')) f.push_back(field);
        ASSERT_EQ(f.size(), 6u) << line;
        const TestStatus statuses[] = {TestStatus::PASSED, TestStatus::FAILED, TestStatus::SKIPPED,
                                       TestStatus::ERROR, TestStatus::UNSUPPORTED};
        ComplianceReport report;
        int id = 0;
        for (int s = 0; s < 5; ++s) {
            for (int n = std::stoi(f[s]); n > 0; --n) {
                report.add_result(ComplianceResult("t" + std::to_string(id++), statuses[s]));
            }
        }
        EXPECT_NEAR(report.pass_rate(), std::stod(f[5]), 1e-9) << "vector " << line;
        ++rows;
    }
    EXPECT_GT(rows, 0);
}
