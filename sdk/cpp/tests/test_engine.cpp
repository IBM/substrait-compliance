#include <gtest/gtest.h>
#include <substrait_compliance/engine.h>

using namespace substrait::compliance;

class MockEngine : public ComplianceEngine {
public:
    EngineInfo get_info() const override {
        return EngineInfo("MockEngine", "1.0.0", "Test");
    }
    
    EngineCapabilities get_capabilities() const override {
        EngineCapabilities caps;
        caps.add_relation("read").add_function("add");
        return caps;
    }
    
    ComplianceResult execute_plan(
        const std::vector<uint8_t>&,
        const TableCollection&
    ) override {
        return ComplianceResult("test", TestStatus::PASSED);
    }
    
    ComplianceResult validate_plan(const std::vector<uint8_t>&) override {
        return ComplianceResult("validation", TestStatus::PASSED);
    }
};

TEST(EngineInfoTest, Construction) {
    EngineInfo info("TestEngine", "2.0.0", "TestCorp");
    
    EXPECT_EQ(info.name, "TestEngine");
    EXPECT_EQ(info.version, "2.0.0");
    EXPECT_EQ(info.vendor, "TestCorp");
}

TEST(EngineCapabilitiesTest, AddRelations) {
    EngineCapabilities caps;
    caps.add_relation("read").add_relation("filter");
    
    EXPECT_TRUE(caps.supports_relation("read"));
    EXPECT_TRUE(caps.supports_relation("filter"));
    EXPECT_FALSE(caps.supports_relation("join"));
}

TEST(EngineCapabilitiesTest, AddFunctions) {
    EngineCapabilities caps;
    caps.add_function("add").add_function("subtract");
    
    EXPECT_TRUE(caps.supports_function("add"));
    EXPECT_FALSE(caps.supports_function("multiply"));
}

TEST(MockEngineTest, BasicInterface) {
    auto engine = make_engine<MockEngine>();
    
    auto info = engine->get_info();
    EXPECT_EQ(info.name, "MockEngine");
    
    auto caps = engine->get_capabilities();
    EXPECT_TRUE(caps.supports_relation("read"));
}

// Made with Bob
