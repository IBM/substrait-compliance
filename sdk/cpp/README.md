# Substrait Compliance SDK - C++

Modern C++17 SDK for decentralized Substrait compliance testing.

## Features

- **Modern C++17**: Smart pointers, RAII, move semantics
- **Zero-copy operations**: Efficient handling of large plans and data
- **Type safety**: Strong typing prevents common errors
- **Performance**: Compiled to native code for maximum speed
- **Cross-platform**: Works on Linux, macOS, and Windows
- **Header-only option**: Can be used as header-only library

## Requirements

- C++17 compatible compiler (GCC 7+, Clang 5+, MSVC 2017+)
- CMake 3.15 or higher
- Protocol Buffers (libprotobuf)
- yaml-cpp library

## Installation

### Using CMake

```bash
# Clone the repository
git clone https://github.com/substrait-io/substrait-compliance.git
cd substrait-compliance/sdk/cpp

# Build and install
mkdir build && cd build
cmake ..
make
sudo make install
```

### Using vcpkg

```bash
vcpkg install substrait-compliance
```

### Using Conan

```bash
conan install substrait-compliance/1.0.0@
```

## Quick Start

```cpp
#include <substrait_compliance.h>
#include <iostream>

using namespace substrait::compliance;

// 1. Implement the ComplianceEngine interface
class MyEngine : public ComplianceEngine {
public:
    EngineInfo get_info() const override {
        return EngineInfo("MyEngine", "1.0.0", "MyCompany")
            .with_description("My Substrait query engine");
    }
    
    EngineCapabilities get_capabilities() const override {
        EngineCapabilities caps;
        caps.add_relation("read")
            .add_relation("filter")
            .add_relation("project")
            .add_function("add")
            .add_function("subtract")
            .add_function("multiply");
        return caps;
    }
    
    ComplianceResult execute_plan(
        const std::vector<uint8_t>& plan_bytes,
        const TableCollection& input_data
    ) override {
        try {
            // Execute your Substrait plan here
            auto output = execute_internal(plan_bytes, input_data);
            
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
        // Validate plan structure
        bool is_valid = validate_internal(plan_bytes);
        
        auto status = is_valid ? TestStatus::PASSED : TestStatus::FAILED;
        return ComplianceResult("validation", status);
    }

private:
    TableData execute_internal(
        const std::vector<uint8_t>& plan,
        const TableCollection& input
    ) {
        // Your execution logic here
        TableData result;
        // ... populate result ...
        return result;
    }
    
    bool validate_internal(const std::vector<uint8_t>& plan) {
        // Your validation logic here
        return true;
    }
};

int main() {
    // 2. Create engine instance
    auto engine = make_engine<MyEngine>();
    
    // 3. Load a test suite
    auto suite = load_test_suite("test-suites/tpch/metadata.yaml");
    
    // 4. Create and configure runner
    auto runner = RunnerBuilder(engine)
        .validate_plans(true)
        .compare_results(true)
        .parallel(4)  // Run 4 tests in parallel
        .on_progress([](const std::string& test_id, size_t current, size_t total) {
            std::cout << "Running test " << current << "/" << total 
                      << ": " << test_id << std::endl;
        })
        .build();
    
    // 5. Run tests
    auto report = runner.run_test_suite(*suite);
    
    // 6. Check results
    std::cout << "\n=== Test Results ===" << std::endl;
    std::cout << "Total: " << report.total_count() << std::endl;
    std::cout << "Passed: " << report.passed_count() << std::endl;
    std::cout << "Failed: " << report.failed_count() << std::endl;
    std::cout << "Pass Rate: " << report.pass_rate() << "%" << std::endl;
    std::cout << "Execution Time: " << report.total_execution_time_ms() << "ms" << std::endl;
    
    // Print failed tests
    for (const auto& result : report.results()) {
        if (!result.is_passed()) {
            std::cout << "\nFailed: " << result.test_id() << std::endl;
            if (result.error_message()) {
                std::cout << "  Error: " << *result.error_message() << std::endl;
            }
        }
    }
    
    return report.all_passed() ? 0 : 1;
}
```

## Building Your Project

### CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.15)
project(my_engine_tests)

set(CMAKE_CXX_STANDARD 17)

# Find the SDK
find_package(substrait_compliance REQUIRED)

# Your engine executable
add_executable(my_engine_tests
    src/main.cpp
    src/my_engine.cpp
)

target_link_libraries(my_engine_tests
    PRIVATE
        substrait::substrait_compliance
)
```

### Compile and Run

```bash
mkdir build && cd build
cmake ..
make
./my_engine_tests
```

## Advanced Usage

### Custom Result Comparison

```cpp
// Configure comparison behavior
ComparisonConfig config;
config.with_epsilon(1e-6)           // Floating point tolerance
      .ignore_order(true)            // Ignore row order
      .strict_nulls(false);          // Treat NULL and empty string as equal

ResultComparator comparator(config);

// Manual comparison
auto result = comparator.compare_tables(actual_output, expected_output);
if (!result.matches) {
    std::cout << "Mismatch: " << result.message << std::endl;
    if (result.row_index) {
        std::cout << "  At row: " << *result.row_index << std::endl;
    }
}
```

### Filtering Tests by Tag

```cpp
auto suite = load_test_suite("test-suites/functions/metadata.yaml");

// Get only arithmetic tests
auto arithmetic_tests = suite->get_tests_by_tag("arithmetic");

// Run filtered tests
for (const auto* test : arithmetic_tests) {
    auto result = runner.run_test_case(*test);
    // Process result...
}
```

### Custom Test Suite Loader

```cpp
class MyCustomLoader : public TestSuiteLoader {
public:
    TestSuitePtr load(const std::filesystem::path& path) override {
        auto suite = make_test_suite();
        
        // Load your custom format
        // ...
        
        return suite;
    }
    
    bool supports(const std::filesystem::path& path) const override {
        return path.extension() == ".custom";
    }
};

// Use custom loader
MyCustomLoader loader;
auto suite = loader.load("my-tests.custom");
```

### Async Execution

```cpp
#include <future>
#include <vector>

// Run tests in parallel using std::async
std::vector<std::future<ComplianceResult>> futures;

for (const auto& test : suite->test_cases()) {
    futures.push_back(std::async(std::launch::async, [&]() {
        return runner.run_test_case(test);
    }));
}

// Collect results
ComplianceReport report;
for (auto& future : futures) {
    report.add_result(future.get());
}
```

## API Reference

### Core Classes

#### `ComplianceEngine`
Main interface for query engines. Implement this to integrate your engine.

**Methods:**
- `get_info()` - Return engine metadata
- `get_capabilities()` - Return supported features
- `execute_plan(plan_bytes, input_data)` - Execute a Substrait plan
- `validate_plan(plan_bytes)` - Validate a plan without execution
- `initialize()` - Optional setup before tests
- `shutdown()` - Optional cleanup after tests

#### `ComplianceRunner`
Executes test suites against an engine.

**Methods:**
- `run_test_suite(suite)` - Run all tests in a suite
- `run_test_case(test_case)` - Run a single test
- `set_progress_callback(callback)` - Set progress notification

#### `TestSuite`
Collection of related test cases.

**Methods:**
- `add_test_case(test)` - Add a test to the suite
- `test_cases()` - Get all tests
- `get_tests_by_tag(tag)` - Filter tests by tag
- `find_test(id)` - Find test by ID

#### `ComplianceResult`
Result of a single test execution.

**Methods:**
- `test_id()` - Get test identifier
- `status()` - Get execution status
- `output_data()` - Get output table (if available)
- `error_message()` - Get error message (if failed)
- `execution_time_ms()` - Get execution time

#### `ComplianceReport`
Aggregated results for a test suite.

**Methods:**
- `total_count()` - Total number of tests
- `passed_count()` - Number of passed tests
- `failed_count()` - Number of failed tests
- `pass_rate()` - Pass rate percentage
- `all_passed()` - Check if all tests passed

### Data Types

#### `TableData`
Represents tabular data with schema.

```cpp
TableData table;
table.set_columns({
    {"id", "INTEGER"},
    {"name", "VARCHAR"},
    {"value", "DOUBLE"}
});

table.add_row({1, "Alice", 3.14});
table.add_row({2, "Bob", 2.71});
```

#### `CellValue`
Variant type for table cells.

```cpp
CellValue null_value = nullptr;
CellValue int_value = 42;
CellValue float_value = 3.14;
CellValue string_value = std::string("hello");
```

## Error Handling

The SDK uses exceptions for error handling:

```cpp
try {
    auto suite = load_test_suite("invalid-path.yaml");
} catch (const LoaderError& e) {
    std::cerr << "Failed to load suite: " << e.what() << std::endl;
} catch (const ComplianceError& e) {
    std::cerr << "Compliance error: " << e.what() << std::endl;
}
```

## Performance Tips

1. **Use move semantics**: Pass large objects by rvalue reference
2. **Reserve capacity**: Pre-allocate vectors when size is known
3. **Parallel execution**: Use `RunnerConfig::with_parallelism()`
4. **Minimize copies**: Use references and const where possible
5. **Profile your code**: Use tools like `perf` or `valgrind`

## Testing

```bash
# Build with tests
cmake -DBUILD_TESTS=ON ..
make

# Run tests
ctest --output-on-failure

# Or run directly
./tests/substrait_compliance_tests
```

## Examples

See the `examples/` directory for complete examples:

- `basic_engine.cpp` - Minimal engine implementation
- `advanced_engine.cpp` - Full-featured engine with all capabilities
- `custom_loader.cpp` - Custom test suite loader
- `parallel_execution.cpp` - Parallel test execution

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](../../CONTRIBUTING.md).

## License

Apache License 2.0

## Support

- GitHub Issues: https://github.com/substrait-io/substrait-compliance/issues
- Documentation: https://substrait.io/compliance
- Slack: #substrait-compliance