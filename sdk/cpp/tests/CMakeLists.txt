# Tests for Substrait Compliance C++ SDK

# Find Google Test
find_package(GTest REQUIRED)
include(GoogleTest)

# Test executable
add_executable(substrait_compliance_tests
    test_engine.cpp
    test_result.cpp
    test_table_data.cpp
    test_comparator.cpp
)

target_link_libraries(substrait_compliance_tests
    PRIVATE
        substrait_compliance
        GTest::GTest
        GTest::Main
)

# Discover tests
gtest_discover_tests(substrait_compliance_tests)