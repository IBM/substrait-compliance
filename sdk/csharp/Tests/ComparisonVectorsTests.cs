using System;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text.Json.Nodes;
using FluentAssertions;
using Xunit;

namespace Substrait.Compliance.Tests
{
    /// <summary>
    /// The comparison vectors every SDK shares (sdk/conformance/comparison-vectors.json): the
    /// comparator agrees with the leaderboard on whether an output matches.
    /// </summary>
    public class ComparisonVectorsTests
    {
        private static string VectorsPath()
        {
            var dir = new DirectoryInfo(AppContext.BaseDirectory);
            while (dir != null)
            {
                foreach (var rel in new[] { "conformance", Path.Combine("sdk", "conformance") })
                {
                    var candidate = Path.Combine(dir.FullName, rel, "comparison-vectors.json");
                    if (File.Exists(candidate)) return candidate;
                }
                dir = dir.Parent;
            }
            throw new FileNotFoundException("sdk/conformance/comparison-vectors.json not found");
        }

        // {"double": "<text>"} is a double (e.g. NaN), {"decimal": "<text>"} an exact decimal.
        private static object? Cell(JsonNode? c) => c switch
        {
            null => null,
            JsonObject o when o["double"] != null => double.Parse((string)o["double"]!, CultureInfo.InvariantCulture),
            JsonObject o when o["decimal"] != null => decimal.Parse((string)o["decimal"]!, NumberStyles.Float, CultureInfo.InvariantCulture),
            JsonValue v when v.TryGetValue<bool>(out var b) => b,
            JsonValue v when v.TryGetValue<string>(out var s) => s,
            JsonValue v when v.TryGetValue<long>(out var l) => l,
            JsonValue v => v.GetValue<double>(),
            _ => throw new FormatException($"unexpected cell {c}"),
        };

        private static TableData Table(JsonNode t)
        {
            var names = t["names"]!.AsArray();
            var types = t["types"]!.AsArray();
            var table = new TableData(names.Select((n, i) => new Column((string)n!, ColumnTypes.FromName((string)types[i]!))));
            foreach (var row in t["rows"]!.AsArray()) table.AddRow(row!.AsArray().Select(Cell).ToArray());
            return table;
        }

        [Fact]
        public void ComparatorAgreesWithTheSharedVectors()
        {
            var vectors = JsonNode.Parse(File.ReadAllText(VectorsPath()))!["vectors"]!.AsArray();
            vectors.Should().NotBeEmpty();
            var comparator = new ResultComparator();
            foreach (var v in vectors)
            {
                var scales = v!["declaredScales"] is JsonArray a ? a.Select(x => x == null ? (int?)null : (int)x).ToList() : null;
                var mismatch = comparator.DescribeMismatch(Table(v["expected"]!), Table(v["actual"]!), scales);
                (mismatch == null).Should().Be((bool)v["match"]!, $"{v["name"]}: {mismatch}");
            }
        }

        [Fact]
        public void NumbersCloseUsesTheSharedTolerances()
        {
            ResultComparator.NumbersClose(1e12, 1e12 + 100).Should().BeTrue();
            ResultComparator.NumbersClose(1.0, 1.0001).Should().BeFalse();
            ResultComparator.NumbersClose(25.58, 25.575154, 2).Should().BeTrue();
            ResultComparator.NumbersClose(double.NaN, double.NaN).Should().BeTrue();
            ResultComparator.NumbersClose(double.PositiveInfinity, double.NegativeInfinity).Should().BeFalse();
        }

        [Fact]
        public void ColumnTypesIgnoreTypeParameters()
        {
            ColumnTypes.FromName("decimal(15,2)").Should().Be(ColumnType.Decimal);
            ColumnTypes.FromName("varchar(25)").Should().Be(ColumnType.String);
            ColumnTypes.FromName("BIGINT").Should().Be(ColumnType.Integer);
            ColumnTypes.FromName("interval").Should().Be(ColumnType.String);
        }
    }
}
