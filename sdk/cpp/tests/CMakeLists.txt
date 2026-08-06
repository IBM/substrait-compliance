# Tests for Substrait Compliance C++ SDK

# Find Google Test using pkg-config
find_package(PkgConfig REQUIRED)
pkg_check_modules(GTEST REQUIRED gtest_main)

# All unit + integration tests in a single executable
add_executable(substrait_compliance_tests
    test_engine.cpp
    test_result.cpp
    test_table_data.cpp
    test_comparator.cpp
    test_pass_through.cpp
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

# Pass the repo root so tests can resolve test-suite files
target_compile_definitions(substrait_compliance_tests
    PRIVATE
        REPO_ROOT="${CMAKE_SOURCE_DIR}/../.."
)

# Register test
enable_testing()
add_test(NAME substrait_compliance_tests COMMAND substrait_compliance_tests)