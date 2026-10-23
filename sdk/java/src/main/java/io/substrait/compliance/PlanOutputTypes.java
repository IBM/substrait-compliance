package io.substrait.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The output column types a Substrait plan declares, from its JSON form, used to compare
 * decimal columns at the scale the plan declares ({@link ComplianceRunner}). Mirrors
 * {@code output_types} and {@code declared_scales} in scripts/run_plan_compliance.py.
 *
 * <p>Types are propagated relation by relation: a read's base schema; filter, sort and fetch
 * keep their input's columns, project adds its expressions; an aggregate yields its grouping
 * expressions, then its measures; joins concatenate both sides; emit mappings select and
 * reorder. An expression's type is its declared outputType (functions, aggregates), the
 * referenced column's type, a cast's target or a literal's type.
 */
public final class PlanOutputTypes {

    private PlanOutputTypes() {
    }

    /**
     * The scale of each output column the plan declares as decimal (null for other columns),
     * or null when the plan's output types cannot be worked out.
     */
    public static List<Integer> declaredScales(JsonNode plan) {
        List<JsonNode> types;
        try {
            types = outputTypes(plan);
        } catch (RuntimeException e) {
            return null;
        }
        List<Integer> scales = new ArrayList<>();
        for (JsonNode t : types) {
            JsonNode dec = t == null ? null : t.get("decimal");
            scales.add(dec != null && dec.has("scale") ? dec.get("scale").asInt() : null);
        }
        return scales;
    }

    /** The plan root's output types (an entry is null where it cannot be determined). */
    public static List<JsonNode> outputTypes(JsonNode plan) {
        for (JsonNode rel : plan.path("relations")) {
            if (rel.has("root")) {
                return relTypes(rel.get("root").get("input"));
            }
        }
        throw new IllegalArgumentException("plan has no root relation");
    }

    static List<JsonNode> relTypes(JsonNode node) {
        Map.Entry<String, JsonNode> only = node.fields().next();
        String kind = only.getKey();
        JsonNode rel = only.getValue();
        List<JsonNode> cols = new ArrayList<>();
        switch (kind) {
            case "read":
                rel.get("baseSchema").get("struct").get("types").forEach(cols::add);
                break;
            case "filter":
            case "sort":
            case "fetch":
                cols.addAll(relTypes(rel.get("input")));
                break;
            case "project": {
                List<JsonNode> base = relTypes(rel.get("input"));
                cols.addAll(base);
                for (JsonNode e : rel.path("expressions")) {
                    cols.add(exprType(e, base));
                }
                break;
            }
            case "aggregate": {
                List<JsonNode> base = relTypes(rel.get("input"));
                JsonNode groupings = rel.path("groupings");
                JsonNode keys;
                if (rel.path("groupingExpressions").size() > 0) {
                    keys = rel.get("groupingExpressions"); // 0.107: aggregate-level expressions
                } else if (groupings.size() <= 1) {
                    keys = groupings.size() == 1 ? groupings.get(0).path("groupingExpressions") : null;
                } else {
                    throw new IllegalArgumentException("grouping sets in the older encoding");
                }
                if (keys != null) {
                    for (JsonNode e : keys) {
                        cols.add(exprType(e, base));
                    }
                }
                for (JsonNode m : rel.path("measures")) {
                    cols.add(m.get("measure").get("outputType"));
                }
                break;
            }
            case "join":
            case "cross":
                cols.addAll(relTypes(rel.get("left")));
                cols.addAll(relTypes(rel.get("right")));
                break;
            default:
                throw new IllegalArgumentException("unsupported relation " + kind);
        }
        JsonNode mapping = rel.path("common").path("emit").path("outputMapping");
        if (mapping.isArray()) {
            List<JsonNode> emitted = new ArrayList<>();
            for (JsonNode i : mapping) {
                emitted.add(cols.get(i.asInt()));
            }
            return emitted;
        }
        return cols;
    }

    static JsonNode exprType(JsonNode e, List<JsonNode> inputs) {
        if (e.has("selection")) {
            JsonNode sel = e.get("selection");
            JsonNode field = sel.path("directReference").path("structField");
            if (sel.has("outerReference") || field.isMissingNode() || field.has("child")) {
                return null;
            }
            int i = field.path("field").asInt(0);
            return i < inputs.size() ? inputs.get(i) : null;
        }
        if (e.has("scalarFunction")) {
            return e.get("scalarFunction").get("outputType");
        }
        if (e.has("windowFunction")) {
            return e.get("windowFunction").get("outputType");
        }
        if (e.has("literal")) {
            return literalType(e.get("literal"));
        }
        if (e.has("cast")) {
            return e.get("cast").get("type");
        }
        if (e.has("ifThen")) {
            JsonNode ifs = e.get("ifThen").path("ifs");
            return ifs.size() > 0 ? exprType(ifs.get(0).get("then"), inputs)
                : exprType(e.get("ifThen").get("else"), inputs);
        }
        if (e.has("singularOrList")) {
            return BOOL;
        }
        if (e.has("subquery")) {
            JsonNode sq = e.get("subquery");
            if (sq.has("scalar")) {
                List<JsonNode> out = relTypes(sq.get("scalar").get("input"));
                return out.isEmpty() ? null : out.get(0);
            }
            return BOOL;
        }
        return null;
    }

    private static final JsonNode BOOL = typeNode("bool");

    private static JsonNode typeNode(String kind) {
        com.fasterxml.jackson.databind.node.ObjectNode n =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        n.putObject(kind);
        return n;
    }

    static JsonNode literalType(JsonNode lit) {
        Iterator<Map.Entry<String, JsonNode>> fields = lit.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> f = fields.next();
            switch (f.getKey()) {
                case "boolean":
                    return BOOL;
                case "i8":
                case "i16":
                case "i32":
                case "i64":
                case "fp32":
                case "fp64":
                case "string":
                case "date":
                case "timestamp":
                    return typeNode(f.getKey());
                case "decimal": {
                    com.fasterxml.jackson.databind.node.ObjectNode n =
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
                    com.fasterxml.jackson.databind.node.ObjectNode d = n.putObject("decimal");
                    d.set("precision", f.getValue().get("precision"));
                    d.set("scale", f.getValue().get("scale"));
                    return n;
                }
                case "varChar":
                    return typeNode("varchar");
                case "fixedChar":
                    return typeNode("fixedChar");
                case "null":
                    return f.getValue();
                default:
                    break;
            }
        }
        return null;
    }
}
