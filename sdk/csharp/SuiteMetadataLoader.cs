using System;
using System.Collections;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json.Nodes;

namespace Substrait.Compliance
{
    /// <summary>
    /// Loads a suite in this repository's metadata format (test-suites/tpch/metadata.yaml and
    /// the like): <c>name</c>, <c>version</c>, <c>description</c> and <c>testCases</c>, each
    /// with <c>id</c>, <c>planBinary</c>, optional <c>planJson</c>, <c>inputTables</c>
    /// (<c>name</c>, <c>file</c>) and <c>expectedOutput</c>.
    /// </summary>
    /// <remarks>
    /// Inputs and expected outputs are read as scripts/run_plan_compliance.py reads them for the
    /// leaderboard:
    /// <list type="bullet">
    /// <item>Input tables are typed from the plan's read schemas (planJson) and keyed by the
    /// table name the plan reads. A first row that names the plan's columns is a header and
    /// skipped. Values are trimmed; an empty value or <c>null</c> is NULL. i8/i16/i32 are
    /// <see cref="int"/>, i64 <see cref="long"/>, fp32 <see cref="float"/>, fp64
    /// <see cref="double"/>, decimal <see cref="decimal"/> (its column carries precision and
    /// scale), bool <see cref="bool"/>; dates and strings are text. Without planJson, an input
    /// table is read like an expected output.</item>
    /// <item>Expected outputs have a typed header (<c>name:type|...</c>); values are parsed by
    /// type (integers as long, floats as double, booleans), the rest kept as text, as the Java
    /// SDK's loader does.</item>
    /// <item>The decimal scales the plan declares (planJson) are attached to each case.</item>
    /// </list>
    /// A data file read by several cases with the same schema is loaded once, and the cases
    /// share that TableData. The metadata is taken already parsed (string keys, lists and
    /// scalars, as YamlDotNet produces them), so this class needs no YAML library.
    /// </remarks>
    public static class SuiteMetadataLoader
    {
        /// <summary>Whether parsed metadata is in this format (it has a top-level <c>testCases</c> list).</summary>
        public static bool IsSuiteMetadata(IDictionary<string, object?> data) =>
            data.TryGetValue("testCases", out var cases) && cases is IList;

        /// <summary>The suite described by parsed metadata, with files relative to <paramref name="basePath"/>.</summary>
        public static TestSuite Load(IDictionary<string, object?> data, string basePath)
        {
            var tables = new Dictionary<string, TableData>();
            var cases = List(data.TryGetValue("testCases", out var c) ? c : null)
                .Select(tc => LoadCase(tc, basePath, tables)).ToList();
            var metadata = new TestSuiteMetadata(
                Str(data, "name") ?? "Unnamed Test Suite",
                Str(data, "version") ?? "1.0.0",
                Str(data, "description"),
                Str(data, "category"));
            return new TestSuite(metadata, cases);
        }

        private static TestCase LoadCase(object? tc, string basePath, Dictionary<string, TableData> tables)
        {
            var id = Str(tc, "id") ?? throw new InvalidOperationException("test case without id");
            try
            {
                var planBinary = Str(tc, "planBinary") ?? throw new InvalidOperationException("planBinary is required");
                var schemas = new Dictionary<string, IReadOnlyList<SchemaColumn>>();
                IReadOnlyList<int?>? scales = null;
                var planJson = Str(tc, "planJson");
                if (planJson != null)
                {
                    var text = File.ReadAllText(Path.Combine(basePath, planJson));
                    schemas = PlanTypes.NamedTableSchemas(PlanTypes.ParsePlanJson(text));
                    scales = PlanTypes.DeclaredScales(text);
                }
                var inputs = new Dictionary<string, TableData>();
                foreach (var t in List(Get(tc, "inputTables")))
                {
                    var name = Str(t, "name") ?? throw new InvalidOperationException("input table without name");
                    var file = Path.GetFullPath(Path.Combine(basePath,
                        Str(t, "file") ?? throw new InvalidOperationException($"no file for table {name}")));
                    var match = schemas.FirstOrDefault(s => string.Equals(s.Key, name, StringComparison.OrdinalIgnoreCase));
                    var columns = match.Value;
                    var key = file + "\0" + (columns == null ? "" : string.Join("|", columns.Select(col => col.Name + ":" + col.Type?.ToJsonString())));
                    if (!tables.TryGetValue(key, out var table))
                    {
                        table = columns != null ? LoadInputTable(file, columns) : LoadTypedCsv(file);
                        tables[key] = table;
                    }
                    inputs[match.Key ?? name] = table;
                }
                TableData? expected = null;
                var expectedOutput = Str(tc, "expectedOutput");
                if (expectedOutput != null && File.Exists(Path.Combine(basePath, expectedOutput)))
                    expected = LoadTypedCsv(Path.Combine(basePath, expectedOutput));
                return new TestCase(id, Path.GetFullPath(Path.Combine(basePath, planBinary)), Str(tc, "description"),
                    inputs, expected, List(Get(tc, "tags")).Select(x => x?.ToString() ?? "").ToList(),
                    string.Equals(Str(tc, "skip"), "true", StringComparison.OrdinalIgnoreCase), Str(tc, "skipReason"))
                {
                    DeclaredScales = scales,
                };
            }
            catch (Exception e) when (e is IOException || e is InvalidOperationException || e is FormatException
                                      || e is System.Text.Json.JsonException || e is UnauthorizedAccessException)
            {
                throw new InvalidOperationException($"test case {id}: {e.Message}", e);
            }
        }

        // ── input tables, typed by the plan ────────────────────────────────────

        private static string Kind(JsonNode? t) => t is JsonObject o && o.Count > 0 ? o.First().Key : "string";

        private static Column PlanColumn(SchemaColumn c)
        {
            var kind = Kind(c.Type);
            var type = kind switch
            {
                "i8" or "i16" or "i32" or "i64" => ColumnType.Integer,
                "fp32" or "fp64" => ColumnType.Float,
                "decimal" => ColumnType.Decimal,
                "bool" => ColumnType.Boolean,
                "date" => ColumnType.Date,
                "time" or "precisionTime" => ColumnType.Time,
                "timestamp" or "timestampTz" or "precisionTimestamp" or "precisionTimestampTz" => ColumnType.Timestamp,
                "binary" or "fixedBinary" => ColumnType.Binary,
                _ => ColumnType.String,
            };
            if (kind != "decimal") return new Column(c.Name, type);
            var d = c.Type!["decimal"];
            return new Column(c.Name, type)
            {
                Precision = (int?)d?["precision"],
                Scale = (int?)d?["scale"],
            };
        }

        private static object? PlanValue(string raw, JsonNode? t)
        {
            var v = raw.Trim();
            if (v.Length == 0 || v.Equals("null", StringComparison.OrdinalIgnoreCase)) return null;
            var inv = CultureInfo.InvariantCulture;
            return Kind(t) switch
            {
                "i8" or "i16" or "i32" => int.Parse(v, NumberStyles.AllowLeadingSign, inv),
                "i64" => long.Parse(v, NumberStyles.AllowLeadingSign, inv),
                "fp32" => (float)ParseDouble(v),
                "fp64" => ParseDouble(v),
                "decimal" => decimal.Parse(v, NumberStyles.Float, inv),
                "bool" => v.Equals("true", StringComparison.OrdinalIgnoreCase),
                _ => v,
            };
        }

        /// <summary>A CSV typed by the plan's read schema for the table.</summary>
        public static TableData LoadInputTable(string file, IReadOnlyList<SchemaColumn> columns)
        {
            var lines = ReadLines(file);
            var table = new TableData(columns.Select(PlanColumn));
            if (lines.Count == 0) return table;
            var delimiter = DetectDelimiter(lines[0]);
            var header = SplitLine(lines[0], delimiter).Select(h => h.Split(':')[0]).ToList();
            var isHeader = header.Count == columns.Count
                && header.Zip(columns, (h, c) => string.Equals(h, c.Name, StringComparison.OrdinalIgnoreCase)).All(x => x);
            for (int i = isHeader ? 1 : 0; i < lines.Count; i++)
            {
                if (lines[i].Trim().Length == 0) continue;
                var values = SplitLine(lines[i], delimiter);
                if (values.Count != columns.Count)
                    throw new FormatException($"{file} row {i + 1} has {values.Count} values, expected {columns.Count}");
                var row = new object?[values.Count];
                for (int j = 0; j < values.Count; j++)
                {
                    try
                    {
                        row[j] = PlanValue(values[j], columns[j].Type);
                    }
                    catch (Exception e) when (e is FormatException || e is OverflowException)
                    {
                        throw new FormatException($"{file} row {i + 1}, column {columns[j].Name}: '{values[j]}' is not a {Kind(columns[j].Type)}", e);
                    }
                }
                table.AddRow(row);
            }
            return table;
        }

        // ── expected outputs (typed header, as the Java SDK's loader reads them) ───

        private static double ParseDouble(string v)
        {
            var l = v.ToLowerInvariant().TrimStart('+');
            if (l == "nan" || l == "-nan") return double.NaN;
            if (l == "inf" || l == "infinity") return double.PositiveInfinity;
            if (l == "-inf" || l == "-infinity") return double.NegativeInfinity;
            return double.Parse(v, NumberStyles.Float, CultureInfo.InvariantCulture);
        }

        private static object? HeaderValue(string raw, string type)
        {
            var v = raw.Trim();
            if (v.Length == 0 || v.Equals("null", StringComparison.OrdinalIgnoreCase)) return null;
            try
            {
                switch (type)
                {
                    case "int": case "integer": case "i8": case "i16": case "i32": case "long": case "bigint": case "i64":
                        return long.Parse(v, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture);
                    case "float": case "fp32": case "double": case "fp64": case "decimal":
                        return ParseDouble(v);
                    case "bool": case "boolean":
                        return v.Equals("true", StringComparison.OrdinalIgnoreCase);
                    default:
                        return v;
                }
            }
            catch (Exception e) when (e is FormatException || e is OverflowException)
            {
                return v;
            }
        }

        /// <summary>A CSV with a typed header (<c>name:type|...</c>), or untyped columns column_1.. of text.</summary>
        public static TableData LoadTypedCsv(string file)
        {
            var lines = ReadLines(file);
            if (lines.Count == 0) throw new FormatException($"CSV file is empty: {file}");
            var delimiter = DetectDelimiter(lines[0]);
            var first = SplitLine(lines[0], delimiter);
            var typed = first.All(h => h.IndexOf(':') > 0 && h.IndexOf(':') < h.Length - 1);
            var names = typed ? first.Select(h => h.Substring(0, h.IndexOf(':')).Trim()).ToList()
                              : first.Select((_, i) => $"column_{i + 1}").ToList();
            var types = typed ? first.Select(h => h.Substring(h.IndexOf(':') + 1).Trim().ToLowerInvariant()).ToList()
                              : first.Select(_ => "string").ToList();
            var table = new TableData(names.Select((n, i) => new Column(n, ColumnTypes.FromName(types[i]))));
            for (int i = typed ? 1 : 0; i < lines.Count; i++)
            {
                if (lines[i].Trim().Length == 0) continue;
                var values = SplitLine(lines[i], delimiter);
                if (values.Count != names.Count)
                    throw new FormatException($"CSV row {i + 1} in {file} has {values.Count} values but expected {names.Count}");
                table.AddRow(values.Select((v, j) => HeaderValue(v, types[j])).ToArray());
            }
            return table;
        }

        // ── CSV and metadata helpers ─────────────────────────────────────────────

        private static List<string> ReadLines(string file)
        {
            var lines = File.ReadAllText(file).Replace("\r\n", "\n").Split('\n').ToList();
            while (lines.Count > 0 && lines[^1].Length == 0) lines.RemoveAt(lines.Count - 1);
            return lines;
        }

        private static char DetectDelimiter(string line) =>
            line.Count(ch => ch == '|') > line.Count(ch => ch == ',') ? '|' : ',';

        /// <summary>One line, split on the delimiter outside double quotes; "" inside quotes is a quote.</summary>
        private static List<string> SplitLine(string line, char delimiter)
        {
            var values = new List<string>();
            var current = new StringBuilder();
            var inQuotes = false;
            for (int i = 0; i < line.Length; i++)
            {
                var ch = line[i];
                if (ch == '"')
                {
                    if (inQuotes && i + 1 < line.Length && line[i + 1] == '"') { current.Append('"'); i++; }
                    else inQuotes = !inQuotes;
                }
                else if (ch == delimiter && !inQuotes)
                {
                    values.Add(current.ToString().Trim());
                    current.Clear();
                }
                else current.Append(ch);
            }
            values.Add(current.ToString().Trim());
            return values.Select(v => v.Length >= 2 && v[0] == '"' && v[^1] == '"' ? v.Substring(1, v.Length - 2) : v).ToList();
        }

        private static object? Get(object? v, string key) => v switch
        {
            IDictionary<string, object?> d => d.TryGetValue(key, out var x) ? x : null,
            IDictionary d => d.Contains(key) ? d[key] : null,
            _ => null,
        };

        private static string? Str(object? v, string key) => Get(v, key)?.ToString();

        private static List<object?> List(object? v) => v is IList l ? l.Cast<object?>().ToList() : new List<object?>();
    }
}
