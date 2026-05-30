cmake_minimum_required(VERSION 3.15)
project(VeloxSubstraitCompliance CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find Velox
find_package(velox REQUIRED)

# Find Substrait
find_package(Protobuf REQUIRED)
find_package(substrait REQUIRED)

# Add the compliance engine library
add_library(velox_compliance_engine
    velox_compliance_engine.cpp
    velox_compliance_engine.hpp
)

target_link_libraries(velox_compliance_engine
    PUBLIC
        velox::velox_core
        velox::velox_exec
        velox::velox_functions_prestosql
        velox::velox_substrait
        velox::velox_vector
        velox::velox_memory
        substrait::substrait
        protobuf::libprotobuf
)

target_include_directories(velox_compliance_engine
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# Add example executable
add_executable(velox_compliance_example
    example_main.cpp
)

target_link_libraries(velox_compliance_example
    PRIVATE
        velox_compliance_engine
)

# Installation
install(TARGETS velox_compliance_engine velox_compliance_example
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)

install(FILES velox_compliance_engine.hpp
    DESTINATION include/substrait/compliance
)