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
    /// Comparator for comparing expected and actual results
    /// </summary>
    public class ResultComparator
    {
        /// <summary>
        /// Compare two TableData objects for equality
        /// </summary>
        public virtual bool Compare(TableData expected, TableData actual)
        {
            // Check column count
            if (expected.ColumnCount != actual.ColumnCount)
                return false;

            // Check row count
            if (expected.RowCount != actual.RowCount)
                return false;

            // Check column definitions
            for (int i = 0; i < expected.ColumnCount; i++)
            {
                if (expected.Columns[i].Name != actual.Columns[i].Name)
                    return false;
                if (expected.Columns[i].Type != actual.Columns[i].Type)
                    return false;
            }

            // Check row data
            for (int i = 0; i < expected.RowCount; i++)
            {
                if (!CompareRows(expected.Rows[i], actual.Rows[i]))
                    return false;
            }

            return true;
        }

        protected virtual bool CompareRows(object?[] expected, object?[] actual)
        {
            if (expected.Length != actual.Length)
                return false;

            for (int i = 0; i < expected.Length; i++)
            {
                if (!CompareValues(expected[i], actual[i]))
                    return false;
            }

            return true;
        }

        protected virtual bool CompareValues(object? expected, object? actual)
        {
            // Handle null values
            if (expected == null && actual == null) return true;
            if (expected == null || actual == null) return false;

            // Handle floating point comparison with tolerance
            if (expected is double expectedDouble && actual is double actualDouble)
            {
                if (double.IsNaN(expectedDouble) && double.IsNaN(actualDouble)) return true;
                if (double.IsNaN(expectedDouble) || double.IsNaN(actualDouble)) return false;
                const double tolerance = 1e-10;
                return Math.Abs(expectedDouble - actualDouble) < tolerance;
            }

            if (expected is float expectedFloat && actual is float actualFloat)
            {
                if (float.IsNaN(expectedFloat) && float.IsNaN(actualFloat)) return true;
                if (float.IsNaN(expectedFloat) || float.IsNaN(actualFloat)) return false;
                const float tolerance = 1e-6f;
                return Math.Abs(expectedFloat - actualFloat) < tolerance;
            }

            // Handle dates
            if (expected is DateTime expectedDate && actual is DateTime actualDate)
            {
                return expectedDate == actualDate;
            }

            // Default comparison
            return expected.Equals(actual);
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

                // If we have expected output, compare it
                if (testCase.ExpectedOutput != null && result.OutputData != null)
                {
                    var matches = Comparator.Compare(testCase.ExpectedOutput, result.OutputData);

                    if (!matches && result.Status == TestStatus.Passed)
                    {
                        // Override status if output doesn't match
                        return new ComplianceResult(
                            testCase.Id,
                            TestStatus.Failed,
                            result.OutputData,
                            "Output does not match expected result",
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

// Made with Bob
