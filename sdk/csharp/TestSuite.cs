using System;
using System.Collections.Generic;
using System.Linq;

namespace Substrait.Compliance
{
    /// <summary>
    /// Metadata about a test suite
    /// </summary>
    public record TestSuiteMetadata(
        string Name,
        string Version,
        string? Description = null,
        string? Category = null,
        IReadOnlyList<string>? Tags = null
    );

    /// <summary>
    /// A single test case within a test suite
    /// </summary>
    public class TestCase
    {
        public string Id { get; }
        public string? Description { get; }
        public string Plan { get; }
        public IReadOnlyDictionary<string, TableData>? InputData { get; }
        public TableData? ExpectedOutput { get; }
        public IReadOnlyList<string>? Tags { get; }
        public bool Skip { get; }
        public string? SkipReason { get; }

        public TestCase(
            string id,
            string plan,
            string? description = null,
            IReadOnlyDictionary<string, TableData>? inputData = null,
            TableData? expectedOutput = null,
            IReadOnlyList<string>? tags = null,
            bool skip = false,
            string? skipReason = null)
        {
            Id = id ?? throw new ArgumentNullException(nameof(id));
            Plan = plan ?? throw new ArgumentNullException(nameof(plan));
            Description = description;
            InputData = inputData;
            ExpectedOutput = expectedOutput;
            Tags = tags;
            Skip = skip;
            SkipReason = skipReason;
        }
    }

    /// <summary>
    /// Complete test suite definition
    /// </summary>
    public class TestSuite
    {
        private readonly List<TestCase> _testCases;

        public TestSuiteMetadata Metadata { get; }
        public IReadOnlyList<TestCase> TestCases => _testCases.AsReadOnly();

        public TestSuite(TestSuiteMetadata metadata, IEnumerable<TestCase> testCases)
        {
            Metadata = metadata ?? throw new ArgumentNullException(nameof(metadata));
            _testCases = new List<TestCase>(testCases ?? throw new ArgumentNullException(nameof(testCases)));
        }

        /// <summary>
        /// Get test case by ID
        /// </summary>
        public TestCase? GetTestCase(string id)
        {
            return _testCases.FirstOrDefault(tc => tc.Id == id);
        }

        /// <summary>
        /// Get all test case IDs
        /// </summary>
        public IEnumerable<string> GetTestCaseIds()
        {
            return _testCases.Select(tc => tc.Id);
        }

        /// <summary>
        /// Filter test cases by tag
        /// </summary>
        public IEnumerable<TestCase> FilterByTag(string tag)
        {
            return _testCases.Where(tc => tc.Tags?.Contains(tag) == true);
        }

        /// <summary>
        /// Get non-skipped test cases
        /// </summary>
        public IEnumerable<TestCase> GetActiveTestCases()
        {
            return _testCases.Where(tc => !tc.Skip);
        }

        /// <summary>
        /// Get skipped test cases
        /// </summary>
        public IEnumerable<TestCase> GetSkippedTestCases()
        {
            return _testCases.Where(tc => tc.Skip);
        }

        /// <summary>
        /// Get total number of test cases
        /// </summary>
        public int TotalCount => _testCases.Count;

        /// <summary>
        /// Get number of active test cases
        /// </summary>
        public int ActiveCount => _testCases.Count(tc => !tc.Skip);

        /// <summary>
        /// Get number of skipped test cases
        /// </summary>
        public int SkippedCount => _testCases.Count(tc => tc.Skip);
    }
}

