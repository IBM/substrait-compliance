/**
 * @file basic_engine.cpp
 * @brief Basic example of implementing a ComplianceEngine
 * 
 * This example shows the minimal implementation needed to integrate
 * a query engine with the Substrait compliance framework.
 */

#include <substrait_compliance.h>
#include <iostream>
#include <iomanip>
#include <string>

using namespace substrait::compliance;

/**
 * @brief Simple example engine implementation
 */
class BasicEngine : public ComplianceEngine {
public:
    EngineInfo get_info() const override {
        return EngineInfo("BasicEngine", "1.0.0", "Example Corp")
            .with_description("A basic example Substrait engine");
    }
    
    EngineCapabilities get_capabilities() const override {
        EngineCapabilities caps;
        caps.add_relation("read")
            .add_relation("filter")
            .add_relation("project")
            .add_function("add")
            .add_function("subtract")
            .add_type("i32")
            .add_type("i64")
            .add_type("string");
        return caps;
    }
    
    ComplianceResult execute_plan(
        const std::vector<uint8_t>& plan_bytes,
        const TableCollection& input_data
    ) override {
        try {
            if (plan_bytes.empty()) {
                return ComplianceResult("test", TestStatus::FAILED)
                    .with_error("Plan is empty");
            }

            TableData output;
            if (!input_data.empty()) {
                const auto& first_entry = *input_data.begin();
                const auto& input_table = first_entry.second;

                output.set_columns(input_table.columns());
                for (const auto& row : input_table.rows()) {
                    output.add_row(row);
                }

                return ComplianceResult("test", TestStatus::PASSED)
                    .with_output(std::move(output))
                    .with_error_details("Echoed input table: " + first_entry.first);
            }

            output.set_columns({{"result", "INTEGER"}});
            output.add_row({static_cast<int64_t>(plan_bytes.size())});
            
            return ComplianceResult("test", TestStatus::PASSED)
                .with_output(std::move(output));
        } catch (const std::exception& e) {
            return ComplianceResult("test", TestStatus::ERROR)
                .with_error(e.what());
        }
    }
    
    ComplianceResult validate_plan(
        const std::vector<uint8_t>& plan_bytes
    ) override {
        if (plan_bytes.empty()) {
            return ComplianceResult("validation", TestStatus::FAILED)
                .with_error("Plan is empty");
        }
        return ComplianceResult("validation", TestStatus::PASSED);
    }
};

int main(int argc, char* argv[]) {
    try {
        std::cout << "=== Substrait Compliance Test ===" << std::endl;
        
        auto engine = make_engine<BasicEngine>();
        auto info = engine->get_info();
        std::cout << "Engine: " << info.name << " v" << info.version << std::endl;
        
        std::string suite_path = "test-suites/tpch/metadata.yaml";
        if (argc > 1) suite_path = argv[1];
        
        auto suite = load_test_suite(suite_path);
        std::cout << "Loaded " << suite->size() << " tests" << std::endl;
        
        auto runner = RunnerBuilder(engine)
            .validate_plans(true)
            .on_progress([](const std::string& id, size_t cur, size_t tot) {
                std::cout << "[" << cur << "/" << tot << "] " << id << std::endl;
            })
            .build();
        
        auto report = runner.run_test_suite(*suite);
        
        std::cout << "\n=== Results ===" << std::endl;
        std::cout << "Passed: " << report.passed_count() << "/" 
                  << report.total_count() << std::endl;
        std::cout << "Pass Rate: " << std::fixed << std::setprecision(1)
                  << report.pass_rate() << "%" << std::endl;
        
        return report.all_passed() ? 0 : 1;
        
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
        return 1;
    }
}

