cmake_minimum_required(VERSION 3.15)
project(substrait_compliance_cpp VERSION 1.0.0 LANGUAGES CXX)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Set macOS SDK path for C++ standard library
if(APPLE)
    execute_process(
        COMMAND xcrun --show-sdk-path
        OUTPUT_VARIABLE MACOS_SDK_PATH
        OUTPUT_STRIP_TRAILING_WHITESPACE
    )
    set(CMAKE_OSX_SYSROOT ${MACOS_SDK_PATH})
endif()

# Options
option(BUILD_TESTS "Build unit tests" ON)
option(BUILD_EXAMPLES "Build examples" ON)
option(BUILD_SHARED_LIBS "Build shared libraries" ON)

# Find dependencies
find_package(Protobuf REQUIRED)
find_package(yaml-cpp REQUIRED)

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${Protobuf_INCLUDE_DIRS}
)

# Create header-only interface library
add_library(substrait_compliance INTERFACE)

target_include_directories(substrait_compliance
    INTERFACE
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:include>
)

target_link_libraries(substrait_compliance
    INTERFACE
        ${Protobuf_LIBRARIES}
        yaml-cpp
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