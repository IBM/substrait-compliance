#pragma once

#include "substrait_compliance/engine.h"
#include "substrait_compliance/result.h"
#include "substrait_compliance/runner.h"
#include "substrait_compliance/test_suite.h"
#include "substrait_compliance/table_data.h"
#include "substrait_compliance/loader.h"
#include "substrait_compliance/error.h"
#include "substrait_compliance/comparator.h"

namespace substrait::compliance {

// Version information
constexpr const char* VERSION = "1.0.0";
constexpr int VERSION_MAJOR = 1;
constexpr int VERSION_MINOR = 0;
constexpr int VERSION_PATCH = 0;

} // namespace substrait::compliance

