using System;
using System.Globalization;
using System.IO;
using System.Linq;
using FluentAssertions;
using Xunit;

namespace Substrait.Compliance.Tests
{
    /// <summary>The pass-rate vectors every SDK shares (sdk/conformance/pass-rate-vectors.csv).</summary>
    public class PassRateVectorsTests
    {
        private static string VectorsPath()
        {
            var dir = new DirectoryInfo(AppContext.BaseDirectory);
            while (dir != null)
            {
                var candidate = Path.Combine(dir.FullName, "conformance", "pass-rate-vectors.csv");
                if (File.Exists(candidate)) return candidate;
                candidate = Path.Combine(dir.FullName, "sdk", "conformance", "pass-rate-vectors.csv");
                if (File.Exists(candidate)) return candidate;
                dir = dir.Parent;
            }
            throw new FileNotFoundException("sdk/conformance/pass-rate-vectors.csv not found");
        }

        [Fact]
        public void PassRateMatchesTheSharedVectors()
        {
            var rows = File.ReadAllLines(VectorsPath())
                .Select(l => l.Trim())
                .Where(l => l.Length > 0 && !l.StartsWith("#"))
                .Skip(1)
                .ToList();
            rows.Should().NotBeEmpty();
            var statuses = new[] { TestStatus.Passed, TestStatus.Failed, TestStatus.Skipped, TestStatus.Error, TestStatus.Unsupported };
            foreach (var row in rows)
            {
                var f = row.Split(',');
                f.Should().HaveCount(6, row);
                var report = new ComplianceReport();
                var id = 0;
                for (var s = 0; s < 5; s++)
                    for (var n = int.Parse(f[s], CultureInfo.InvariantCulture); n > 0; n--)
                        report.AddResult(new ComplianceResult($"t{id++}", statuses[s]));
                var expected = double.Parse(f[5], CultureInfo.InvariantCulture);
                report.PassRate.Should().BeApproximately(expected, 1e-9, row);
            }
        }
    }
}
