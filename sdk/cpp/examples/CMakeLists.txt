# Examples for Substrait Compliance C++ SDK

add_executable(basic_engine basic_engine.cpp)

# Set C++ standard for examples
set_target_properties(basic_engine PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
)

target_link_libraries(basic_engine
    PRIVATE
        substrait_compliance
)

# Install examples
install(TARGETS basic_engine
    RUNTIME DESTINATION bin/examples
)

install(FILES
    basic_engine.cpp
    DESTINATION share/substrait_compliance/examples
)