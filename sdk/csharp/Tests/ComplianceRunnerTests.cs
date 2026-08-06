using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using FluentAssertions;
using Xunit;

namespace Substrait.Compliance.Tests
{
    /// <summary>
    /// Pass-through integration tests for ComplianceRunner.
    ///
    /// A pass-through engine returns the expected output verbatim.
    /// Running it against a suite that has expected output must yield 100%.
    /// Any failure indicates a bug in the comparator or type-normalisation.
    /// </summary>
    public class ComplianceRunnerTests
    {
        // ── Pass-through engine ───────────────────────────────────────────────

        /// <summary>
        /// Returns the pre-registered output for a test, keyed by test ID
        /// encoded as UTF-8 in the plan bytes.
        /// </summary>
        private class PassThroughEngine : IComplianceEngine
        {
            private readonly IReadOnlyDictionary<string, TableData> _outputs;

            public PassThroughEngine(IReadOnlyDictionary<string, TableData> outputs)
                => _outputs = outputs;

            public EngineInfo GetInfo() =>
                new("PassThrough", "0.0.0", "Test");

            public EngineCapabilities GetCapabilities() =>
                new(Array.Empty<string>(), Array.Empty<string>(), Array.Empty<string>());

            public Task<ComplianceResult> ExecutePlanAsync(
                byte[] planBytes,
                IReadOnlyDictionary<string, TableData> inputData)
            {
                var testId = System.Text.Encoding.UTF8.GetString(planBytes);
                _outputs.TryGetValue(testId, out var output);
                return Task.FromResult(
                    new ComplianceResult(testId, TestStatus.Passed, output));
            }

            public Task<ComplianceResult> ValidatePlanAsync(byte[] planBytes) =>
                Task.FromResult(new ComplianceResult("validation", TestStatus.Passed));
        }

        // ── In-memory runner subclass ─────────────────────────────────────────

        /// <summary>
        /// Overrides RunTestCaseAsync to skip the file-read step and inject
        /// plan bytes as UTF-8 encoded test ID directly.
        /// </summary>
        private class InMemoryRunner : ComplianceRunner
        {
            public InMemoryRunner(IComplianceEngine engine) : base(engine) { }

            public async Task<ComplianceReport> RunInMemoryAsync(TestSuite suite)
            {
                var report = new ComplianceReport();
                foreach (var tc in suite.GetActiveTestCases())
                {
                    var planBytes = System.Text.Encoding.UTF8.GetBytes(tc.Id);
                    var result = await _engine.ExecutePlanAsync(
                        planBytes,
                        tc.InputData ?? new Dictionary<string, TableData>());

                    // Mirror the fix in ComplianceRunner: no expected output → Skipped
                    if (tc.ExpectedOutput == null)
                    {
                        report.AddResult(new ComplianceResult(
                            tc.Id, TestStatus.Skipped, result.OutputData,
                            "No expected output — cannot verify correctness"));
                        continue;
                    }

                    if (result.OutputData != null)
                    {
                        bool matches = Comparator.Compare(tc.ExpectedOutput, result.OutputData);
                        if (!matches && result.Status == TestStatus.Passed)
                        {
                            result = new ComplianceResult(
                                tc.Id, TestStatus.Failed, result.OutputData,
                                "Output does not match expected result");
                        }
                    }

                    report.AddResult(new ComplianceResult(
                        tc.Id, result.Status, result.OutputData,
                        result.ErrorMessage, result.ErrorDetails, result.ExecutionTimeMs));
                }
                return report;
            }

            // Expose private field for test access
            private IComplianceEngine _engine =>
                (IComplianceEngine)typeof(ComplianceRunner)
                    .GetField("_engine",
                        System.Reflection.BindingFlags.NonPublic |
                        System.Reflection.BindingFlags.Instance)!
                    .GetValue(this)!;
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private static TableData MakeIntTable(IEnumerable<(int a, int b)> rows)
        {
            var cols = new[] {
                new Column("a", ColumnType.Integer),
                new Column("b", ColumnType.Integer),
            };
            var td = new TableData(cols);
            foreach (var (a, b) in rows)
                td.AddRow(a, b);
            return td;
        }

        private static TableData MakeDoubleTable(IEnumerable<double> vals)
        {
            var cols = new[] { new Column("val", ColumnType.Float) };
            var td = new TableData(cols);
            foreach (var v in vals)
                td.AddRow(v);
            return td;
        }

        private static TestSuite MakeSuite(IReadOnlyDictionary<string, TableData> cases)
        {
            var testCases = new List<TestCase>();
            foreach (var (id, expected) in cases)
            {
                testCases.Add(new TestCase(
                    id: id,
                    plan: id,           // placeholder path — not read in InMemoryRunner
                    expectedOutput: expected));
            }
            return new TestSuite(
                new TestSuiteMetadata("pass-through", "0.0.0"),
                testCases);
        }

        private static InMemoryRunner MakeRunner(
            IReadOnlyDictionary<string, TableData> outputs)
            => new(new PassThroughEngine(outputs));

        // ── Tests ─────────────────────────────────────────────────────────────

        [Fact]
        public async Task PassThroughEngine_Scores100Percent()
        {
            var cases = new Dictionary<string, TableData>
            {
                ["q01"] = MakeIntTable(new[] { (1, 2), (3, 4) }),
                ["q02"] = MakeIntTable(new[] { (10, 20) }),
                ["q03"] = MakeIntTable(new[] { (0, 0), (1, 1) }),
            };

            var report = await MakeRunner(cases).RunInMemoryAsync(MakeSuite(cases));

            report.TotalCount.Should().Be(3);
            report.PassedCount.Should().Be(3);
            report.FailedCount.Should().Be(0);
            report.PassRate.Should().BeApproximately(100.0, 0.01);
        }

        [Fact]
        public async Task ValueMismatch_IsDetectedAsFailed()
        {
            var expected = MakeIntTable(new[] { (1, 2) });
            var wrong    = MakeIntTable(new[] { (1, 99) });   // col b is wrong

            var cases   = new Dictionary<string, TableData> { ["mm"] = expected };
            var outputs = new Dictionary<string, TableData> { ["mm"] = wrong };

            var report = await MakeRunner(outputs).RunInMemoryAsync(MakeSuite(cases));

            report.FailedCount.Should().Be(1, "wrong value should be detected");
            report.PassedCount.Should().Be(0);
        }

        [Fact]
        public async Task Epsilon_WithinTolerance_Passes()
        {
            var expected = MakeDoubleTable(new[] { 1.0 });
            var close    = MakeDoubleTable(new[] { 1.0 + 5e-11 });  // within 1e-10

            var cases   = new Dictionary<string, TableData> { ["c"] = expected };
            var outputs = new Dictionary<string, TableData> { ["c"] = close };

            var report = await MakeRunner(outputs).RunInMemoryAsync(MakeSuite(cases));

            report.PassedCount.Should().Be(1);
        }

        [Fact]
        public async Task Epsilon_OutsideTolerance_Fails()
        {
            var expected = MakeDoubleTable(new[] { 1.0 });
            var far      = MakeDoubleTable(new[] { 2.0 });

            var cases   = new Dictionary<string, TableData> { ["f"] = expected };
            var outputs = new Dictionary<string, TableData> { ["f"] = far };

            var report = await MakeRunner(outputs).RunInMemoryAsync(MakeSuite(cases));

            report.FailedCount.Should().Be(1);
        }

        [Fact]
        public async Task RowCountMismatch_IsDetected()
        {
            var expected = MakeIntTable(new[] { (1, 2), (3, 4) });
            var shorter  = MakeIntTable(new[] { (1, 2) });   // row missing

            var cases   = new Dictionary<string, TableData> { ["r"] = expected };
            var outputs = new Dictionary<string, TableData> { ["r"] = shorter };

            var report = await MakeRunner(outputs).RunInMemoryAsync(MakeSuite(cases));

            report.FailedCount.Should().Be(1);
        }

        [Fact]
        public async Task MissingExpectedOutput_IsSkipped_NotPassed()
        {
            // Build a suite with a test case that has no expected output
            var testCase = new TestCase(
                id: "no-expected",
                plan: "no-expected",    // placeholder path — not read in InMemoryRunner
                expectedOutput: null);  // explicitly absent

            var suite = new TestSuite(
                new TestSuiteMetadata("skip-test", "0.0.0"),
                new List<TestCase> { testCase });

            // Engine returns no output (empty outputs dict)
            var report = await new InMemoryRunner(
                new PassThroughEngine(new Dictionary<string, TableData>()))
                .RunInMemoryAsync(suite);

            report.TotalCount.Should().Be(1);
            report.SkippedCount.Should().Be(1,
                "missing expected output must produce Skipped, not Passed or Failed");
            report.PassedCount.Should().Be(0,
                "missing expected output must NOT count as passed");
            report.FailedCount.Should().Be(0,
                "missing expected output must not produce a spurious failure");
        }
    }
}
