#pragma once

#include <filesystem>
#include <optional>
#include <string>
#include <vector>

namespace substrait::compliance {

/**
 * @brief The decimal scales a Substrait plan declares for its output columns
 *
 * From the plan's JSON form, so decimal columns can be compared at the scale the plan
 * declares (ResultComparator::describe_mismatch). Mirrors output_types / declared_scales in
 * scripts/run_plan_compliance.py.
 *
 * Types are propagated relation by relation: a read's base schema; filter, sort and fetch
 * keep their input's columns, project adds its expressions; an aggregate yields its grouping
 * expressions, then its measures; joins concatenate both sides; emit mappings select and
 * reorder. An expression's type is its declared outputType (functions, aggregates), the
 * referenced column's type, a cast's target or a literal's type.
 *
 * The JSON is read with yaml-cpp (JSON is valid YAML), already a dependency of this SDK; it
 * may start with // comment lines, as the suites' plan files do.
 *
 * @return per output column the declared decimal scale (nullopt for other columns), or
 *         nullopt when the plan's output types cannot be worked out
 */
std::optional<std::vector<std::optional<int>>> declared_scales(const std::string& plan_json);

/** @brief declared_scales of a plan JSON file (nullopt if it cannot be read) */
std::optional<std::vector<std::optional<int>>> declared_scales_file(const std::filesystem::path& path);

} // namespace substrait::compliance
