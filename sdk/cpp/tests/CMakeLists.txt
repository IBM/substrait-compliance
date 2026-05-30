# Tests for Substrait Compliance C++ SDK

# Find Google Test using pkg-config
find_package(PkgConfig REQUIRED)
pkg_check_modules(GTEST REQUIRED gtest_main)

# Test executable
add_executable(substrait_compliance_tests
    test_engine.cpp
    test_result.cpp
    test_table_data.cpp
    test_comparator.cpp
)

target_include_directories(substrait_compliance_tests
    PRIVATE
        ${GTEST_INCLUDE_DIRS}
)

target_link_libraries(substrait_compliance_tests
    PRIVATE
        substrait_compliance
        ${GTEST_LIBRARIES}
)

target_link_directories(substrait_compliance_tests
    PRIVATE
        ${GTEST_LIBRARY_DIRS}
)

# Add test
enable_testing()
add_test(NAME substrait_compliance_tests COMMAND substrait_compliance_tests)