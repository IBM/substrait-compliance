# Tests for Substrait Compliance C++ SDK

# Strategy: try to find a system-installed GTest first (macOS Homebrew,
# distribution packages that ship a compiled lib, etc.).  On Ubuntu,
# libgtest-dev ships headers + source only (no compiled .a/.so), so
# find_package will fail and we fall back to building GTest from source
# via FetchContent.

find_package(GTest QUIET)

if(NOT GTest_FOUND)
    message(STATUS "GTest not found via find_package — fetching from source")
    include(FetchContent)
    set(FETCHCONTENT_UPDATES_DISCONNECTED ON)

    FetchContent_Declare(
        googletest
        GIT_REPOSITORY https://github.com/google/googletest.git
        GIT_TAG        v1.14.0
    )

    # Prevent GTest from overriding compiler/linker settings on Windows
    set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
    FetchContent_MakeAvailable(googletest)
else()
    message(STATUS "Using system GTest: ${GTest_VERSION}")
endif()

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
        ${CMAKE_SOURCE_DIR}/include
)

target_link_libraries(substrait_compliance_tests
    PRIVATE
        substrait_compliance
        GTest::gtest_main
)

# Pass the repo root so tests can resolve test-suite files
target_compile_definitions(substrait_compliance_tests
    PRIVATE
        REPO_ROOT="${CMAKE_SOURCE_DIR}/../.."
)

# Register tests with CTest
enable_testing()
include(GoogleTest)
gtest_discover_tests(substrait_compliance_tests)
