using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Substrait.Compliance
{
    /// <summary>One column of a table a plan reads: its name and Substrait type (JSON form).</summary>
    public record SchemaColumn(string Name, JsonNode? Type);

    /// <summary>
    /// The output column types a Substrait plan declares, from its JSON form, so decimal columns
    /// can be compared at the scale the plan declares (<see cref="ResultComparator"/>). Mirrors
    /// output_types / declared_scales in scripts/run_plan_compliance.py.
    /// </summary>
    /// <remarks>
    /// Types are propagated relation by relation: a read's base schema; filter, sort and fetch
    /// keep their input's columns, project adds its expressions; an aggregate yields its grouping
    /// expressions, then its measures; joins concatenate both sides; emit mappings select and
    /// reorder. An expression's type is its declared outputType (functions, aggregates), the
    /// referenced column's type, a cast's target or a literal's type.
    /// </remarks>
    public static class PlanTypes
    {
        private sealed class UnknownPlanException : Exception { }

        /// <summary>
        /// The scale of each output column the plan declares as decimal (null for other columns),
        /// or null when the plan's output types cannot be worked out. The JSON may start with //
        /// comment lines, as the suites' plan files do.
        /// </summary>
        public static IReadOnlyList<int?>? DeclaredScales(string planJson)
        {
            try
            {
                var plan = ParsePlanJson(planJson);
                return OutputTypes(plan).Select(t =>
                    Get(Get(t, "decimal"), "scale") is JsonValue v && v.TryGetValue<int>(out var s) ? (int?)s : null).ToList();
            }
            catch (Exception e) when (e is UnknownPlanException || e is JsonException || e is InvalidOperationException
                                      || e is FormatException)
            {
                return null;
            }
        }

        /// <summary>
        /// The declared scales of the plan whose binary is at <paramref name="planPath"/>, read from
        /// the JSON form beside it (plans/q01.bin -> plans/q01.json), or null if there is none.
        /// </summary>
        public static IReadOnlyList<int?>? DeclaredScalesBeside(string planPath)
        {
            if (!planPath.EndsWith(".bin", StringComparison.Ordinal)) return null;
            var json = planPath.Substring(0, planPath.Length - 4) + ".json";
            return File.Exists(json) ? DeclaredScales(File.ReadAllText(json)) : null;
        }

        /// <summary>
        /// A plan's JSON form, parsed; leading // comment lines, as the suites' plan files have,
        /// are ignored.
        /// </summary>
        public static JsonNode? ParsePlanJson(string planJson)
        {
            var text = string.Join("\n", planJson.Split('\n').Where(l => !l.TrimStart().StartsWith("//")));
            // Plans nest deeply (TPC-DS q95: 63 levels); the default limit is 64.
            return JsonNode.Parse(text, documentOptions: new JsonDocumentOptions { MaxDepth = 1000 });
        }

        /// <summary>
        /// For every named table the plan reads, the columns it reads it with. Throws when one
        /// table is read with two different schemas.
        /// </summary>
        public static Dictionary<string, IReadOnlyList<SchemaColumn>> NamedTableSchemas(JsonNode? plan)
        {
            var found = new Dictionary<string, IReadOnlyList<SchemaColumn>>();
            void Walk(JsonNode? node)
            {
                if (node is JsonArray array)
                {
                    foreach (var v in array) Walk(v);
                }
                else if (node is JsonObject obj)
                {
                    var read = Get(obj, "read");
                    var named = Get(read, "namedTable");
                    var schema = Get(read, "baseSchema");
                    if (named != null && schema != null)
                    {
                        var name = string.Join(".", List(named, "names").Select(n => n?.ToString()));
                        var types = List(Get(schema, "struct"), "types");
                        var columns = List(schema, "names")
                            .Select((n, i) => new SchemaColumn(n?.ToString() ?? "", i < types.Count ? types[i] : null)).ToList();
                        if (found.TryGetValue(name, out var prev) && !prev.Select(c => c.Name).SequenceEqual(columns.Select(c => c.Name)))
                            throw new InvalidOperationException($"table {name} is read with two different schemas");
                        found[name] = columns;
                    }
                    foreach (var kv in obj) Walk(kv.Value);
                }
            }
            Walk(plan);
            return found;
        }

        /// <summary>The plan root's output types (an entry is null where it cannot be determined).</summary>
        public static List<JsonNode?> OutputTypes(JsonNode? plan)
        {
            foreach (var r in List(plan, "relations"))
            {
                if (Get(r, "root") is JsonNode root) return RelTypes(Req(root, "input"));
            }
            throw new UnknownPlanException();
        }

        private static JsonNode? Get(JsonNode? v, string key) => v is JsonObject o && o.TryGetPropertyValue(key, out var x) ? x : null;

        private static JsonNode Req(JsonNode? v, string key) => Get(v, key) ?? throw new UnknownPlanException();

        private static List<JsonNode?> List(JsonNode? v, string key) => Get(v, key) is JsonArray a ? a.ToList() : new List<JsonNode?>();

        private static (string, JsonNode?) OnlyEntry(JsonNode? v)
        {
            if (v is JsonObject o && o.Count > 0)
            {
                var first = o.First();
                return (first.Key, first.Value);
            }
            throw new UnknownPlanException();
        }

        private static JsonNode KindType(string kind) => new JsonObject { [kind] = new JsonObject() };

        private static List<JsonNode?> RelTypes(JsonNode? node)
        {
            var (kind, rel) = OnlyEntry(node);
            List<JsonNode?> cols;
            switch (kind)
            {
                case "read":
                    if (Req(Req(Req(rel, "baseSchema"), "struct"), "types") is not JsonArray types) throw new UnknownPlanException();
                    cols = types.ToList();
                    break;
                case "filter":
                case "sort":
                case "fetch":
                    cols = RelTypes(Req(rel, "input"));
                    break;
                case "project":
                {
                    var b = RelTypes(Req(rel, "input"));
                    cols = b.Concat(List(rel, "expressions").Select(e => ExprType(e, b))).ToList();
                    break;
                }
                case "aggregate":
                {
                    var b = RelTypes(Req(rel, "input"));
                    var groupings = List(rel, "groupings");
                    var keys = List(rel, "groupingExpressions"); // 0.107: aggregate-level expressions
                    if (keys.Count == 0)
                    {
                        if (groupings.Count > 1) throw new UnknownPlanException(); // grouping sets in the older encoding
                        keys = groupings.Count == 1 ? List(groupings[0], "groupingExpressions") : new List<JsonNode?>();
                    }
                    cols = keys.Select(e => ExprType(e, b))
                        .Concat(List(rel, "measures").Select(m => Get(Req(m, "measure"), "outputType"))).ToList();
                    break;
                }
                case "join":
                case "cross":
                    cols = RelTypes(Req(rel, "left")).Concat(RelTypes(Req(rel, "right"))).ToList();
                    break;
                default:
                    throw new UnknownPlanException();
            }
            var mapping = Get(Get(Get(rel, "common"), "emit"), "outputMapping");
            if (mapping == null) return cols;
            if (mapping is not JsonArray idx) throw new UnknownPlanException();
            return idx.Select(i =>
            {
                var n = i is JsonValue v && v.TryGetValue<int>(out var k) ? k : -1;
                if (n < 0 || n >= cols.Count) throw new UnknownPlanException();
                return cols[n];
            }).ToList();
        }

        private static JsonNode? ExprType(JsonNode? e, List<JsonNode?> inputs)
        {
            if (Get(e, "selection") is JsonNode sel)
            {
                var sf = Get(Get(sel, "directReference"), "structField");
                if (Get(sel, "outerReference") != null || sf == null || Get(sf, "child") != null) return null;
                var i = Get(sf, "field") is JsonValue fv && fv.TryGetValue<int>(out var f) ? f : 0;
                return i < inputs.Count ? inputs[i] : null;
            }
            foreach (var key in new[] { "scalarFunction", "windowFunction" })
            {
                if (Get(e, key) is JsonNode fn) return Get(fn, "outputType");
            }
            if (Get(e, "literal") is JsonNode lit) return LiteralType(lit);
            if (Get(e, "cast") is JsonNode cast) return Get(cast, "type");
            if (Get(e, "ifThen") is JsonNode it)
            {
                var ifs = List(it, "ifs");
                return ifs.Count > 0 ? ExprType(Req(ifs[0], "then"), inputs) : ExprType(Req(it, "else"), inputs);
            }
            if (Get(e, "singularOrList") != null) return KindType("bool");
            if (Get(e, "subquery") is JsonNode sq)
            {
                if (Get(sq, "scalar") is JsonNode scalar)
                {
                    var o = RelTypes(Req(scalar, "input"));
                    return o.Count > 0 ? o[0] : null;
                }
                return KindType("bool");
            }
            return null;
        }

        private static JsonNode? LiteralType(JsonNode lit)
        {
            var (key, value) = OnlyEntry(lit);
            switch (key)
            {
                case "boolean": return KindType("bool");
                case "i8": case "i16": case "i32": case "i64": case "fp32": case "fp64": case "string": case "date": case "timestamp":
                    return KindType(key);
                case "decimal":
                    var p = Get(value, "precision");
                    var s = Get(value, "scale");
                    return p == null || s == null ? null
                        : new JsonObject { ["decimal"] = new JsonObject { ["precision"] = p.DeepClone(), ["scale"] = s.DeepClone() } };
                case "varChar": return KindType("varchar");
                case "fixedChar": return KindType("fixedChar");
                case "null": return value?.DeepClone();
                default: return null;
            }
        }
    }
}
