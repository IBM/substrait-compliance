cmake_minimum_required(VERSION 3.15)
project(substrait_compliance_cpp VERSION 0.1.1 LANGUAGES CXX)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Ensure macOS C++ standard library headers are found.
# On macOS 15+ (darwin25) the SDK path returned by xcrun may not be in clang's
# default include search path.  We force-add it so that <string>, <vector> etc.
# are always resolved.
if(APPLE)
    execute_process(
        COMMAND xcrun --show-sdk-path
        OUTPUT_VARIABLE MACOS_SDK_PATH
        OUTPUT_STRIP_TRAILING_WHITESPACE
    )
    set(CMAKE_OSX_SYSROOT ${MACOS_SDK_PATH})
    # Explicitly add libc++ and system headers so that clang can always find them
    add_compile_options(
        -stdlib=libc++
        -I${MACOS_SDK_PATH}/usr/include/c++/v1
        -I${MACOS_SDK_PATH}/usr/include
    )
    add_link_options(-stdlib=libc++)
endif()

# Options
option(BUILD_TESTS "Build unit tests" ON)
option(BUILD_EXAMPLES "Build examples" ON)
option(BUILD_SHARED_LIBS "Build shared libraries" ON)

# Find dependencies
find_package(Protobuf REQUIRED)
find_package(yaml-cpp REQUIRED)

# yaml-cpp installed via Homebrew may not export its lib path via CMake config.
# Fall back to pkg-config if the imported target has no LOCATION.
if(APPLE)
    execute_process(
        COMMAND /opt/homebrew/bin/pkg-config --libs-only-L yaml-cpp
        OUTPUT_VARIABLE YAML_CPP_LIBDIR
        OUTPUT_STRIP_TRAILING_WHITESPACE
        ERROR_QUIET
    )
    if(YAML_CPP_LIBDIR)
        string(REPLACE "-L" "" YAML_CPP_LIBDIR "${YAML_CPP_LIBDIR}")
        string(STRIP "${YAML_CPP_LIBDIR}" YAML_CPP_LIBDIR)
        link_directories(${YAML_CPP_LIBDIR})
    else()
        # Homebrew default
        link_directories(/opt/homebrew/lib)
    endif()
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${Protobuf_INCLUDE_DIRS}
)

# Implementation sources (runner, loader, comparator)
set(SUBSTRAIT_COMPLIANCE_SRCS
    src/runner.cpp
    src/loader.cpp
    src/comparator.cpp
)

# Build as a static library (replaces the header-only INTERFACE target)
add_library(substrait_compliance STATIC ${SUBSTRAIT_COMPLIANCE_SRCS})

target_include_directories(substrait_compliance
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:include>
)

target_link_libraries(substrait_compliance
    PUBLIC
        yaml-cpp
        ${Protobuf_LIBRARIES}
)

# Set library properties
set_target_properties(substrait_compliance PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    PUBLIC_HEADER "include/substrait_compliance.h"
)

# Examples - temporarily disabled due to compiler issues
# if(BUILD_EXAMPLES)
#     add_subdirectory(examples)
# endif()

# Tests
if(BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()

# Installation
install(TARGETS substrait_compliance
    EXPORT substrait_compliance_targets
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
    PUBLIC_HEADER DESTINATION include
)

install(DIRECTORY include/
    DESTINATION include
    FILES_MATCHING PATTERN "*.h"
)

# Export targets
install(EXPORT substrait_compliance_targets
    FILE substrait_compliance_targets.cmake
    NAMESPACE substrait::
    DESTINATION lib/cmake/substrait_compliance
)

# Create config file
include(CMakePackageConfigHelpers)
configure_package_config_file(
    ${CMAKE_CURRENT_SOURCE_DIR}/cmake/substrait_compliance_config.cmake.in
    ${CMAKE_CURRENT_BINARY_DIR}/substrait_compliance_config.cmake
    INSTALL_DESTINATION lib/cmake/substrait_compliance
)

write_basic_package_version_file(
    ${CMAKE_CURRENT_BINARY_DIR}/substrait_compliance_config_version.cmake
    VERSION ${PROJECT_VERSION}
    COMPATIBILITY SameMajorVersion
)

install(FILES
    ${CMAKE_CURRENT_BINARY_DIR}/substrait_compliance_config.cmake
    ${CMAKE_CURRENT_BINARY_DIR}/substrait_compliance_config_version.cmake
    DESTINATION lib/cmake/substrait_compliance
)