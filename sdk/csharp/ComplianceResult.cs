using System;
using System.Collections.Generic;
using System.Linq;

namespace Substrait.Compliance
{
    /// <summary>
    /// Test execution status
    /// </summary>
    public enum TestStatus
    {
        Passed,
        Failed,
        Skipped,
        Error,
        Unsupported
    }

    /// <summary>
    /// Result of a single test case execution
    /// </summary>
    public class ComplianceResult
    {
        public string TestId { get; }
        public TestStatus Status { get; }
        public TableData? OutputData { get; }
        public string? ErrorMessage { get; }
        public string? ErrorDetails { get; }
        public long ExecutionTimeMs { get; }

        public ComplianceResult(
            string testId,
            TestStatus status,
            TableData? outputData = null,
            string? errorMessage = null,
            string? errorDetails = null,
            long executionTimeMs = 0)
        {
            TestId = testId ?? throw new ArgumentNullException(nameof(testId));
            Status = status;
            OutputData = outputData;
            ErrorMessage = errorMessage;
            ErrorDetails = errorDetails;
            ExecutionTimeMs = executionTimeMs;
        }

        /// <summary>
        /// Create a result with output data
        /// </summary>
        public ComplianceResult WithOutput(TableData data)
        {
            return new ComplianceResult(TestId, Status, data, ErrorMessage, ErrorDetails, ExecutionTimeMs);
        }

        /// <summary>
        /// Create a result with error message
        /// </summary>
        public ComplianceResult WithError(string message)
        {
            return new ComplianceResult(TestId, Status, OutputData, message, ErrorDetails, ExecutionTimeMs);
        }

        /// <summary>
        /// Create a result with error details
        /// </summary>
        public ComplianceResult WithErrorDetails(string details)
        {
            return new ComplianceResult(TestId, Status, OutputData, ErrorMessage, details, ExecutionTimeMs);
        }

        /// <summary>
        /// Create a result with execution time
        /// </summary>
        public ComplianceResult WithExecutionTime(long ms)
        {
            return new ComplianceResult(TestId, Status, OutputData, ErrorMessage, ErrorDetails, ms);
        }

        /// <summary>
        /// Check if test passed
        /// </summary>
        public bool IsPassed => Status == TestStatus.Passed;

        /// <summary>
        /// Check if test failed
        /// </summary>
        public bool IsFailed => Status == TestStatus.Failed;

        /// <summary>
        /// Check if test was skipped
        /// </summary>
        public bool IsSkipped => Status == TestStatus.Skipped;

        /// <summary>
        /// Check if test had an error
        /// </summary>
        public bool IsError => Status == TestStatus.Error;

        /// <summary>
        /// Check if test is unsupported
        /// </summary>
        public bool IsUnsupported => Status == TestStatus.Unsupported;
    }

    /// <summary>
    /// Aggregated report for a test suite execution
    /// </summary>
    public class ComplianceReport
    {
        private readonly List<ComplianceResult> _results = new();

        /// <summary>
        /// Add a test result to the report
        /// </summary>
        public void AddResult(ComplianceResult result)
        {
            _results.Add(result ?? throw new ArgumentNullException(nameof(result)));
        }

        /// <summary>
        /// Get all results
        /// </summary>
        public IReadOnlyList<ComplianceResult> Results => _results.AsReadOnly();

        /// <summary>
        /// Get total number of tests
        /// </summary>
        public int TotalCount => _results.Count;

        /// <summary>
        /// Get number of passed tests
        /// </summary>
        public int PassedCount => CountByStatus(TestStatus.Passed);

        /// <summary>
        /// Get number of failed tests
        /// </summary>
        public int FailedCount => CountByStatus(TestStatus.Failed);

        /// <summary>
        /// Get number of skipped tests
        /// </summary>
        public int SkippedCount => CountByStatus(TestStatus.Skipped);

        /// <summary>
        /// Get number of error tests
        /// </summary>
        public int ErrorCount => CountByStatus(TestStatus.Error);

        /// <summary>
        /// Get number of unsupported tests
        /// </summary>
        public int UnsupportedCount => CountByStatus(TestStatus.Unsupported);

        /// <summary>
        /// Calculate pass rate (0-100)
        /// </summary>
        public double PassRate => _results.Count == 0 ? 0 : (PassedCount / (double)_results.Count) * 100;

        /// <summary>
        /// Get total execution time in milliseconds
        /// </summary>
        public long TotalExecutionTimeMs => _results.Sum(r => r.ExecutionTimeMs);

        /// <summary>
        /// Check if all tests passed
        /// </summary>
        public bool AllPassed => PassedCount == TotalCount && TotalCount > 0;

        /// <summary>
        /// Get summary string
        /// </summary>
        public string Summary => $"Passed: {PassedCount}/{TotalCount} ({PassRate:F1}%)";

        private int CountByStatus(TestStatus status)
        {
            return _results.Count(r => r.Status == status);
        }
    }
}

// Made with Bob
