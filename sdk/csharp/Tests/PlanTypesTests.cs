using System;
using System.IO;
using FluentAssertions;
using Xunit;

namespace Substrait.Compliance.Tests
{
    /// <summary>The decimal scales a plan declares, as scripts/run_plan_compliance.py reads them.</summary>
    public class PlanTypesTests
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
        public void DeclaredScalesOfTpchQ01()
        {
            var path = RepoFile(Path.Combine("test-suites", "tpch", "plans", "q01.json"));
            if (path == null) return; // only in a full checkout
            PlanTypes.DeclaredScales(File.ReadAllText(path)).Should()
                .Equal(null, null, 2, 2, 4, 6, 2, 2, 2, null);
            PlanTypes.DeclaredScalesBeside(path.Replace(".json", ".bin")).Should().HaveCount(10);
        }

        [Fact]
        public void ProjectAndEmitSelectColumns()
        {
            const string plan = @"// comment line
{""relations"": [{""root"": {""input"": {""project"": {
  ""common"": {""emit"": {""outputMapping"": [2, 0]}},
  ""input"": {""read"": {""baseSchema"": {""struct"": {""types"": [
    {""decimal"": {""precision"": 15, ""scale"": 2}}, {""i64"": {}}]}}}},
  ""expressions"": [{""literal"": {""decimal"": {""precision"": 10, ""scale"": 4}}}]}}}}]}";
            PlanTypes.DeclaredScales(plan).Should().Equal(4, 2);
        }

        [Fact]
        public void UnknownRelationGivesNoScales()
        {
            PlanTypes.DeclaredScales(@"{""relations"": [{""root"": {""input"": {""exchange"": {}}}}]}").Should().BeNull();
            PlanTypes.DeclaredScales("not json").Should().BeNull();
        }
    }
}
