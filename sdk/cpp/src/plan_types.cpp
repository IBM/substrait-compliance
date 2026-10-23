// plan_types.cpp — the output types a Substrait plan declares (see plan_types.h)

#include "substrait_compliance/plan_types.h"

#include <yaml-cpp/yaml.h>

#include <fstream>
#include <sstream>
#include <stdexcept>

namespace substrait::compliance {

namespace {

struct UnknownPlan : std::runtime_error {
    UnknownPlan() : std::runtime_error("plan output types cannot be worked out") {}
};

using Types = std::vector<YAML::Node>;  // an undefined (Null) node: type not known

bool has(const YAML::Node& v, const char* key) {
    return v.IsMap() && v[key] && !v[key].IsNull();
}

YAML::Node req(const YAML::Node& v, const char* key) {
    if (!has(v, key)) throw UnknownPlan();
    return v[key];
}

std::vector<YAML::Node> list(const YAML::Node& v, const char* key) {
    std::vector<YAML::Node> out;
    if (has(v, key) && v[key].IsSequence()) {
        for (const auto& x : v[key]) out.push_back(x);
    }
    return out;
}

std::pair<std::string, YAML::Node> only_entry(const YAML::Node& v) {
    if (!v.IsMap() || v.size() == 0) throw UnknownPlan();
    auto it = v.begin();
    return {it->first.as<std::string>(), it->second};
}

YAML::Node kind_type(const std::string& kind) {
    YAML::Node n(YAML::NodeType::Map);
    n[kind] = YAML::Node(YAML::NodeType::Map);
    return n;
}

YAML::Node none() { return YAML::Node(YAML::NodeType::Null); }

Types rel_types(const YAML::Node& node);

YAML::Node literal_type(const YAML::Node& lit) {
    auto [key, value] = only_entry(lit);
    if (key == "boolean") return kind_type("bool");
    if (key == "i8" || key == "i16" || key == "i32" || key == "i64" || key == "fp32" || key == "fp64" ||
        key == "string" || key == "date" || key == "timestamp") return kind_type(key);
    if (key == "decimal") {
        if (!has(value, "precision") || !has(value, "scale")) return none();
        YAML::Node d(YAML::NodeType::Map);
        d["precision"] = value["precision"];
        d["scale"] = value["scale"];
        YAML::Node n(YAML::NodeType::Map);
        n["decimal"] = d;
        return n;
    }
    if (key == "varChar") return kind_type("varchar");
    if (key == "fixedChar") return kind_type("fixedChar");
    if (key == "null") return value;
    return none();
}

YAML::Node expr_type(const YAML::Node& e, const Types& inputs) {
    if (has(e, "selection")) {
        YAML::Node sel = e["selection"];
        if (has(sel, "outerReference")) return none();
        if (!sel.IsMap() || !sel["directReference"] || !sel["directReference"].IsMap() ||
            !sel["directReference"]["structField"]) return none();
        YAML::Node sf = sel["directReference"]["structField"];
        if (has(sf, "child")) return none();
        size_t i = has(sf, "field") ? sf["field"].as<size_t>() : 0;
        return i < inputs.size() ? inputs[i] : none();
    }
    for (const char* key : {"scalarFunction", "windowFunction"}) {
        if (has(e, key)) return has(e[key], "outputType") ? e[key]["outputType"] : none();
    }
    if (has(e, "literal")) return literal_type(e["literal"]);
    if (has(e, "cast")) return has(e["cast"], "type") ? e["cast"]["type"] : none();
    if (has(e, "ifThen")) {
        auto ifs = list(e["ifThen"], "ifs");
        return ifs.empty() ? expr_type(req(e["ifThen"], "else"), inputs) : expr_type(req(ifs[0], "then"), inputs);
    }
    if (has(e, "singularOrList")) return kind_type("bool");
    if (has(e, "subquery")) {
        if (has(e["subquery"], "scalar")) {
            Types out = rel_types(req(e["subquery"]["scalar"], "input"));
            return out.empty() ? none() : out[0];
        }
        return kind_type("bool");
    }
    return none();
}

Types rel_types(const YAML::Node& node) {
    auto [kind, rel] = only_entry(node);
    Types cols;
    if (kind == "read") {
        YAML::Node types = req(req(req(rel, "baseSchema"), "struct"), "types");
        if (!types.IsSequence()) throw UnknownPlan();
        for (const auto& t : types) cols.push_back(t);
    } else if (kind == "filter" || kind == "sort" || kind == "fetch") {
        cols = rel_types(req(rel, "input"));
    } else if (kind == "project") {
        Types base = rel_types(req(rel, "input"));
        cols = base;
        for (const auto& e : list(rel, "expressions")) cols.push_back(expr_type(e, base));
    } else if (kind == "aggregate") {
        Types base = rel_types(req(rel, "input"));
        auto groupings = list(rel, "groupings");
        auto keys = list(rel, "groupingExpressions");  // 0.107: aggregate-level expressions
        if (keys.empty()) {
            if (groupings.size() > 1) throw UnknownPlan();  // grouping sets in the older encoding
            if (groupings.size() == 1) keys = list(groupings[0], "groupingExpressions");
        }
        for (const auto& e : keys) cols.push_back(expr_type(e, base));
        for (const auto& m : list(rel, "measures")) {
            YAML::Node measure = req(m, "measure");
            cols.push_back(has(measure, "outputType") ? measure["outputType"] : none());
        }
    } else if (kind == "join" || kind == "cross") {
        cols = rel_types(req(rel, "left"));
        Types right = rel_types(req(rel, "right"));
        cols.insert(cols.end(), right.begin(), right.end());
    } else {
        throw UnknownPlan();
    }
    if (has(rel, "common") && has(rel["common"], "emit") && has(rel["common"]["emit"], "outputMapping")) {
        YAML::Node mapping = rel["common"]["emit"]["outputMapping"];
        if (!mapping.IsSequence()) throw UnknownPlan();
        Types out;
        for (const auto& i : mapping) {
            long n = i.as<long>();
            if (n < 0 || static_cast<size_t>(n) >= cols.size()) throw UnknownPlan();
            out.push_back(cols[static_cast<size_t>(n)]);
        }
        return out;
    }
    return cols;
}

} // namespace

std::optional<std::vector<std::optional<int>>> declared_scales(const std::string& plan_json) {
    std::istringstream in(plan_json);
    std::ostringstream text;
    for (std::string line; std::getline(in, line);) {
        size_t first = line.find_first_not_of(" \t");
        if (first != std::string::npos && line.compare(first, 2, "//") == 0) continue;
        text << line << '\n';
    }
    try {
        YAML::Node plan = YAML::Load(text.str());
        for (const auto& r : list(plan, "relations")) {
            if (!has(r, "root")) continue;
            Types types = rel_types(req(r["root"], "input"));
            std::vector<std::optional<int>> scales;
            for (const auto& t : types) {
                if (has(t, "decimal") && has(t["decimal"], "scale")) scales.push_back(t["decimal"]["scale"].as<int>());
                else scales.push_back(std::nullopt);
            }
            return scales;
        }
        return std::nullopt;
    } catch (const std::exception&) {
        return std::nullopt;  // UnknownPlan, or a plan yaml-cpp cannot read
    }
}

std::optional<std::vector<std::optional<int>>> declared_scales_file(const std::filesystem::path& path) {
    std::ifstream f(path);
    if (!f) return std::nullopt;
    std::ostringstream ss;
    ss << f.rdbuf();
    return declared_scales(ss.str());
}

namespace {

std::string schema_type(const YAML::Node& t) {
    if (!t.IsMap() || t.size() == 0) return "string";
    auto kind = t.begin()->first.as<std::string>();
    if (kind == "i8" || kind == "i16" || kind == "i32") return "integer";
    if (kind == "i64") return "bigint";
    if (kind == "fp32") return "float";
    if (kind == "fp64") return "double";
    if (kind == "bool") return "boolean";
    if (kind == "date" || kind == "time" || kind == "timestamp") return kind;
    if (kind == "timestampTz" || kind == "precisionTimestamp" || kind == "precisionTimestampTz") return "timestamp";
    if (kind == "precisionTime") return "time";
    if (kind == "binary" || kind == "fixedBinary") return "binary";
    if (kind == "decimal") {
        auto d = t.begin()->second;
        return "decimal(" + d["precision"].as<std::string>() + "," + d["scale"].as<std::string>() + ")";
    }
    return "string";
}

void walk_reads(const YAML::Node& node, std::unordered_map<std::string, std::vector<SchemaColumn>>& found) {
    if (node.IsSequence()) {
        for (const auto& v : node) walk_reads(v, found);
        return;
    }
    if (!node.IsMap()) return;
    if (has(node, "read")) {
        auto read = node["read"];
        if (has(read, "namedTable") && has(read, "baseSchema")) {
            std::string name;
            for (const auto& n : list(read["namedTable"], "names")) {
                name += (name.empty() ? "" : ".") + n.as<std::string>();
            }
            auto schema = read["baseSchema"];
            auto types = has(schema, "struct") ? list(schema["struct"], "types") : std::vector<YAML::Node>{};
            std::vector<SchemaColumn> columns;
            auto names = list(schema, "names");
            for (size_t i = 0; i < names.size(); ++i) {
                columns.push_back({names[i].as<std::string>(), i < types.size() ? schema_type(types[i]) : "string"});
            }
            auto prev = found.find(name);
            if (prev != found.end()) {
                bool same = prev->second.size() == columns.size();
                for (size_t i = 0; same && i < columns.size(); ++i) same = prev->second[i].name == columns[i].name;
                if (!same) throw std::runtime_error("table " + name + " is read with two different schemas");
            }
            found[name] = std::move(columns);
        }
    }
    for (const auto& kv : node) walk_reads(kv.second, found);
}

} // namespace

std::unordered_map<std::string, std::vector<SchemaColumn>> named_table_schemas(const std::string& plan_json) {
    std::istringstream in(plan_json);
    std::string text, line;
    while (std::getline(in, line)) {
        auto first = line.find_first_not_of(" \t");
        if (first != std::string::npos && line.compare(first, 2, "//") == 0) continue;
        text += line + "\n";
    }
    YAML::Node plan;
    try {
        plan = YAML::Load(text);
    } catch (const YAML::Exception& e) {
        throw std::runtime_error(std::string("plan JSON: ") + e.what());
    }
    std::unordered_map<std::string, std::vector<SchemaColumn>> found;
    walk_reads(plan, found);
    return found;
}

} // namespace substrait::compliance
