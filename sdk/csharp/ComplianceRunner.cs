using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace Substrait.Compliance
{
    /// <summary>
    /// Configuration options for the test runner
    /// </summary>
    public class RunnerOptions
    {
        /// <summary>
        /// Stop execution on first failure
        /// </summary>
        public bool FailFast { get; set; }

        /// <summary>
        /// Run tests in parallel
        /// </summary>
        public bool Parallel { get; set; }

        /// <summary>
        /// Maximum number of parallel tests
        /// </summary>
        public int MaxParallel { get; set; } = 4;

        /// <summary>
        /// Filter tests by tags
        /// </summary>
        public List<string>? Tags { get; set; }

        /// <summary>
        /// Filter tests by ID pattern (regex)
        /// </summary>
        public string? TestIdPattern { get; set; }

        /// <summary>
        /// Verbose output
        /// </summary>
        public bool Verbose { get; set; }
    }

    /// <summary>
    /// Compares an engine's output with the expected output, as the leaderboard's comparators do
    /// (sdk/conformance/comparison-vectors.json): same row and column counts, column names
    /// case-insensitively, column types by family (all numerics are one family; dates and times
    /// compare as text), rows in order, numbers within max(1e-9, 1e-9 * magnitude), everything
    /// else as text. NULL matches only NULL; a numeric string matches the number.
    /// </summary>
    public class ResultComparator
    {
        /// <summary>Absolute tolerance near zero.</summary>
        public const double NumericAbsTolerance = 1e-9;
        /// <summary>Relative tolerance for larger magnitudes.</summary>
        public const double NumericRelTolerance = 1e-9;

        /// <summary>
        /// Compare two TableData objects
        /// </summary>
        public virtual bool Compare(TableData expected, TableData actual) => DescribeMismatch(expected, actual) == null;

        /// <summary>
        /// null when the outputs match, otherwise a description of the first difference.
        /// <paramref name="declaredScales"/> holds per column the scale of the decimal type the
        /// plan declares (null for other columns); such a column also matches within 10^-scale.
        /// </summary>
        public virtual string? DescribeMismatch(TableData expected, TableData actual, IReadOnlyList<int?>? declaredScales = null)
        {
            string Names(TableData t) => "[" + string.Join(", ", t.Columns.Select(c => c.Name)) + "]";
            string Types(TableData t) => "[" + string.Join(", ", t.Columns.Select(c => c.Type)) + "]";
            if (expected.RowCount != actual.RowCount)
                return $"expected {expected.RowCount} rows, got {actual.RowCount}";
            if (expected.ColumnCount != actual.ColumnCount)
                return $"expected {expected.ColumnCount} columns {Names(expected)}, got {actual.ColumnCount} {Names(actual)}";
            for (int i = 0; i < expected.ColumnCount; i++)
            {
                if (!string.Equals(expected.Columns[i].Name.Trim(), actual.Columns[i].Name.Trim(), StringComparison.OrdinalIgnoreCase))
                    return $"expected column names {Names(expected)}, got {Names(actual)}";
            }
            for (int i = 0; i < expected.ColumnCount; i++)
            {
                if (Family(expected.Columns[i].Type) != Family(actual.Columns[i].Type))
                    return $"expected column types {Types(expected)}, got {Types(actual)}";
            }
            for (int r = 0; r < expected.RowCount; r++)
            {
                var e = expected.Rows[r];
                var a = actual.Rows[r];
                for (int c = 0; c < e.Length && c < a.Length; c++)
                {
                    int? scale = declaredScales != null && c < declaredScales.Count ? declaredScales[c] : null;
                    bool numeric = Family(expected.Columns[c].Type) == "numeric";
                    if (!CompareValues(e[c], a[c], scale, numeric))
                        return $"row {r}, column {expected.Columns[c].Name}: expected {Show(e[c])}, got {Show(a[c])}";
                }
            }
            return null;
        }

        /// <summary>
        /// Numbers within max(1e-9, 1e-9 * magnitude) and, for a declared decimal scale S, within
        /// 10^-S. NaN matches NaN and infinity only the same infinity.
        /// </summary>
        public static bool NumbersClose(double expected, double actual, int? declaredScale = null)
        {
            if (double.IsNaN(expected) || double.IsNaN(actual)) return double.IsNaN(expected) && double.IsNaN(actual);
            if (double.IsInfinity(expected) || double.IsInfinity(actual)) return expected == actual;
            var magnitude = Math.Max(Math.Abs(expected), Math.Abs(actual));
            var tolerance = Math.Max(NumericAbsTolerance, NumericRelTolerance * magnitude);
            if (declaredScale.HasValue)
                tolerance = Math.Max(tolerance, Math.Pow(10, -declaredScale.Value) * (1 + NumericRelTolerance));
            return Math.Abs(expected - actual) <= tolerance;
        }

        private static string Family(ColumnType type) => type switch
        {
            ColumnType.Integer or ColumnType.Float or ColumnType.Decimal => "numeric",
            ColumnType.String or ColumnType.Date or ColumnType.Timestamp or ColumnType.Time => "string",
            _ => type.ToString().ToLowerInvariant(),
        };

        private static bool TryNumber(object? v, out double d)
        {
            switch (v)
            {
                case double x: d = x; return true;
                case float x: d = x; return true;
                case decimal x: d = (double)x; return true;
                case int x: d = x; return true;
                case long x: d = x; return true;
                case short x: d = x; return true;
                case byte x: d = x; return true;
                case string s:
                    return double.TryParse(s.Trim(), System.Globalization.NumberStyles.Float,
                        System.Globalization.CultureInfo.InvariantCulture, out d);
                default: d = 0; return false;
            }
        }

        private static string Show(object? v) => v switch
        {
            null => "NULL",
            string s => $"'{s}'",
            double d => d.ToString("R", System.Globalization.CultureInfo.InvariantCulture),
            IFormattable f => f.ToString(null, System.Globalization.CultureInfo.InvariantCulture),
            _ => v.ToString() ?? "",
        };

        /// <summary>
        /// Compare two cells (no declared scale; numbers only when held as numbers).
        /// </summary>
        protected virtual bool CompareValues(object? expected, object? actual) => CompareValues(expected, actual, null, false);

        /// <summary>
        /// Compare two cells. Text is read as a number in a numeric column or against a number,
        /// as the Java runner does, so "001" and "1" in a text column stay different.
        /// </summary>
        protected virtual bool CompareValues(object? expected, object? actual, int? declaredScale, bool numericColumn)
        {
            if (expected == null || actual == null) return expected == null && actual == null;
            if (expected is bool || actual is bool)
            {
                static bool Truthy(object v) => v is bool b ? b : string.Equals(Convert.ToString(v, System.Globalization.CultureInfo.InvariantCulture), "true", StringComparison.OrdinalIgnoreCase);
                return Truthy(expected) == Truthy(actual);
            }
            if (numericColumn || expected is not string || actual is not string)
            {
                if (TryNumber(expected, out var e) && TryNumber(actual, out var a))
                    return NumbersClose(e, a, declaredScale);
            }
            if (expected is DateTime ed && actual is DateTime ad) return ed == ad;
            return string.Equals(Convert.ToString(expected, System.Globalization.CultureInfo.InvariantCulture),
                                 Convert.ToString(actual, System.Globalization.CultureInfo.InvariantCulture), StringComparison.Ordinal);
        }
    }

    /// <summary>
    /// Test runner for executing compliance test suites
    /// </summary>
    public class ComplianceRunner
    {
        private readonly IComplianceEngine _engine;
        private readonly RunnerOptions _options;

        public ResultComparator Comparator { get; set; }

        public ComplianceRunner(IComplianceEngine engine, RunnerOptions? options = null)
        {
            _engine = engine ?? throw new ArgumentNullException(nameof(engine));
            _options = options ?? new RunnerOptions();
            Comparator = new ResultComparator();
        }

        /// <summary>
        /// Run a complete test suite
        /// </summary>
        public async Task<ComplianceReport> RunTestSuiteAsync(TestSuite suite)
        {
            var report = new ComplianceReport();
            var testCases = suite.GetActiveTestCases().ToList();

            // Filter by tags if specified
            if (_options.Tags?.Count > 0)
            {
                testCases = testCases
                    .Where(tc => _options.Tags.Any(tag => tc.Tags?.Contains(tag) == true))
                    .ToList();
            }

            // Filter by test ID pattern if specified
            if (!string.IsNullOrEmpty(_options.TestIdPattern))
            {
                var pattern = new Regex(_options.TestIdPattern);
                testCases = testCases.Where(tc => pattern.IsMatch(tc.Id)).ToList();
            }

            if (_options.Parallel)
            {
                await RunTestsParallelAsync(testCases, report);
            }
            else
            {
                await RunTestsSequentialAsync(testCases, report);
            }

            return report;
        }

        /// <summary>
        /// Run a single test case
        /// </summary>
        public async Task<ComplianceResult> RunTestCaseAsync(TestCase testCase)
        {
            try
            {
                // Read the plan file
                var planBytes = await File.ReadAllBytesAsync(testCase.Plan);

                // Execute the plan
                var result = await _engine.ExecutePlanAsync(
                    planBytes,
                    testCase.InputData ?? new Dictionary<string, TableData>());

                // No expected output — cannot verify correctness; return SKIPPED
                // rather than letting the engine's raw Passed status flow through.
                if (testCase.ExpectedOutput == null)
                {
                    return new ComplianceResult(
                        testCase.Id,
                        TestStatus.Skipped,
                        result.OutputData,
                        "No expected output — cannot verify correctness",
                        null,
                        result.ExecutionTimeMs);
                }

                // Compare against expected output
                if (result.OutputData != null)
                {
                    // Decimal columns compare at the scale the plan declares: from the test case,
                    // or from the plan's JSON form beside its binary.
                    var scales = testCase.DeclaredScales ?? PlanTypes.DeclaredScalesBeside(testCase.Plan);
                    var mismatch = Comparator.DescribeMismatch(testCase.ExpectedOutput, result.OutputData, scales);

                    if (mismatch != null && result.Status == TestStatus.Passed)
                    {
                        // Override status if output doesn't match
                        return new ComplianceResult(
                            testCase.Id,
                            TestStatus.Failed,
                            result.OutputData,
                            "Output mismatch: " + mismatch,
                            null,
                            result.ExecutionTimeMs);
                    }
                }

                // Return result with correct test ID
                return new ComplianceResult(
                    testCase.Id,
                    result.Status,
                    result.OutputData,
                    result.ErrorMessage,
                    result.ErrorDetails,
                    result.ExecutionTimeMs);
            }
            catch (Exception ex)
            {
                return new ComplianceResult(
                    testCase.Id,
                    TestStatus.Error,
                    null,
                    ex.Message,
                    ex.StackTrace,
                    0);
            }
        }

        private async Task RunTestsSequentialAsync(List<TestCase> testCases, ComplianceReport report)
        {
            foreach (var testCase in testCases)
            {
                if (_options.Verbose)
                {
                    Console.WriteLine($"Running test: {testCase.Id}");
                }

                var result = await RunTestCaseAsync(testCase);
                report.AddResult(result);

                if (_options.Verbose)
                {
                    Console.WriteLine($"  Status: {result.Status}");
                    if (result.ErrorMessage != null)
                    {
                        Console.WriteLine($"  Error: {result.ErrorMessage}");
                    }
                }

                if (_options.FailFast && !result.IsPassed)
                {
                    break;
                }
            }
        }

        private async Task RunTestsParallelAsync(List<TestCase> testCases, ComplianceReport report)
        {
            var maxParallel = _options.MaxParallel;
            var chunks = new List<List<TestCase>>();

            // Split test cases into chunks
            for (int i = 0; i < testCases.Count; i += maxParallel)
            {
                chunks.Add(testCases.Skip(i).Take(maxParallel).ToList());
            }

            // Execute chunks sequentially, tests within chunk in parallel
            foreach (var chunk in chunks)
            {
                var tasks = chunk.Select(tc => RunTestCaseAsync(tc)).ToArray();
                var results = await Task.WhenAll(tasks);

                foreach (var result in results)
                {
                    report.AddResult(result);

                    if (_options.Verbose)
                    {
                        Console.WriteLine($"Test {result.TestId}: {result.Status}");
                    }

                    if (_options.FailFast && !result.IsPassed)
                    {
                        return;
                    }
                }
            }
        }
    }
}

