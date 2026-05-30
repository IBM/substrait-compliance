# Examples for Substrait Compliance C++ SDK

add_executable(basic_engine basic_engine.cpp)
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