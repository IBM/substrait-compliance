using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using FluentAssertions;
using Xunit;

namespace Substrait.Compliance.Tests
{
    /// <summary>The repository's suites load through <see cref="TestSuiteLoader"/>.</summary>
    public class SuiteMetadataYamlTests
    {
        private static string? RepoFile(string rel)
        {
            var dir = new DirectoryInfo(AppContext.BaseDirectory);
            while (dir != null)
            {
                var candidate = Path.Combine(dir.FullName, rel);
                if (File.Exists(candidate)) return candidate;
                dir = dir.Parent;
            }
            return null;
        }

        [Fact]
        public async Task TpchLoadsWithInputTablesTypedByThePlan()
        {
            var path = RepoFile(Path.Combine("test-suites", "tpch", "metadata.yaml"));
            if (path == null) return; // only in a full checkout
            var suite = await new TestSuiteLoader().LoadFromFileAsync(path);
            suite.TestCases.Should().HaveCount(22);
            var q01 = suite.GetTestCase("q01")!;
            var lineitem = q01.InputData!["LINEITEM"];
            lineitem.RowCount.Should().Be(60175);
            lineitem.Columns[4].Name.Should().Be("L_QUANTITY");
            lineitem.Columns[4].Type.Should().Be(ColumnType.Decimal);
            lineitem.Rows[0][4].Should().BeOfType<decimal>();
            q01.ExpectedOutput!.RowCount.Should().Be(4);
            q01.DeclaredScales.Should().Equal(null, null, 2, 2, 4, 6, 2, 2, 2, null);
            q01.Plan.Should().EndWith(Path.Combine("plans", "q01.bin"));
            // Cases reading the same file with the same schema share the table.
            suite.GetTestCase("q06")!.InputData!["LINEITEM"].Should().BeSameAs(lineitem);
        }
    }
}
