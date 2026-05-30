cmake_minimum_required(VERSION 3.15)
project(DuckDBSubstraitCompliance CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find DuckDB
find_package(duckdb REQUIRED)

# Find Substrait
find_package(Protobuf REQUIRED)
find_package(substrait REQUIRED)

# Add the compliance engine library
add_library(duckdb_compliance_engine
    duckdb_compliance_engine.cpp
    duckdb_compliance_engine.hpp
)

target_link_libraries(duckdb_compliance_engine
    PUBLIC
        duckdb::duckdb
        substrait::substrait
        protobuf::libprotobuf
)

target_include_directories(duckdb_compliance_engine
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# Add example executable
add_executable(duckdb_compliance_example
    example_main.cpp
)

target_link_libraries(duckdb_compliance_example
    PRIVATE
        duckdb_compliance_engine
)

# Installation
install(TARGETS duckdb_compliance_engine duckdb_compliance_example
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)

install(FILES duckdb_compliance_engine.hpp
    DESTINATION include/substrait/compliance
)