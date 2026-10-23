using System;
using System.IO;
using System.Linq;
using System.Text.Json.Nodes;
using FluentAssertions;
using Xunit;

namespace Substrait.Compliance.Tests
{
    /// <summary>Input tables and expected outputs read as the leaderboard's runner reads them.</summary>
    public class SuiteMetadataLoaderTests
    {
        private static string TempFile(string content)
        {
            var path = Path.Combine(Path.GetTempPath(), Path.GetRandomFileName() + ".csv");
            File.WriteAllText(path, content);
            return path;
        }

        [Fact]
        public void InputTableSkipsHeaderHonoursQuotesAndReadsNulls()
        {
            var file = TempFile("id,name,ok,price\n1,\"a, b\",true,1.50\n2,null,,\n");
            var columns = new[]
            {
                new SchemaColumn("ID", JsonNode.Parse("{\"i32\": {}}")),
                new SchemaColumn("NAME", JsonNode.Parse("{\"string\": {}}")),
                new SchemaColumn("OK", JsonNode.Parse("{\"bool\": {}}")),
                new SchemaColumn("PRICE", JsonNode.Parse("{\"decimal\": {\"precision\": 15, \"scale\": 2}}")),
            };
            var t = SuiteMetadataLoader.LoadInputTable(file, columns);
            t.RowCount.Should().Be(2);
            t.Rows[0][0].Should().Be(1);
            t.Rows[0][1].Should().Be("a, b");
            t.Rows[0][2].Should().Be(true);
            t.Rows[0][3].Should().Be(1.50m);
            t.Rows[1][1].Should().BeNull();
            t.Rows[1][2].Should().BeNull();
            t.Columns[3].Precision.Should().Be(15);
            t.Columns[3].Scale.Should().Be(2);
            Assert.Throws<FormatException>(() => SuiteMetadataLoader.LoadInputTable(file, columns.Take(1).ToList()));
        }

        [Fact]
        public void ExpectedValuesAreParsedByHeaderType()
        {
            var t = SuiteMetadataLoader.LoadTypedCsv(TempFile("n:bigint|x:double|b:boolean|s:varchar\n7|2.5|TRUE|001\n|null|false|x\n"));
            t.Rows[0][0].Should().Be(7L);
            t.Rows[0][1].Should().Be(2.5);
            t.Rows[0][2].Should().Be(true);
            t.Rows[0][3].Should().Be("001");
            t.Rows[1][0].Should().BeNull();
            t.Columns[3].Type.Should().Be(ColumnType.String);
        }
    }
}
