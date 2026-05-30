using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Threading.Tasks;
using Substrait.Compliance;

namespace Substrait.Compliance.Examples
{
    /// <summary>
    /// Example implementation of a ComplianceEngine
    /// This is a mock engine that demonstrates the interface
    /// </summary>
    public class ExampleEngine : IComplianceEngine
    {
        public EngineInfo GetInfo()
        {
            return new EngineInfo(
                Name: "Example Query Engine",
                Version: "1.0.0",
                Vendor: "Example Corp",
                Description: "A simple example engine for demonstration"
            );
        }

        public EngineCapabilities GetCapabilities()
        {
            return new EngineCapabilities(
                SupportedRelations: new[] { "read", "filter", "project", "aggregate", "join" },
                SupportedFunctions: new[] { "add", "subtract", "multiply", "divide", "equal", "greater_than" },
                SupportedTypes: new[] { "i32", "i64", "fp32", "fp64", "string", "boolean" },
                Extensions: new Dictionary<string, string>
                {
                    ["custom_feature"] = "enabled"
                }
            );
        }

        public async Task<ComplianceResult> ExecutePlanAsync(
            byte[] planBytes,
            IReadOnlyDictionary<string, TableData> inputData)
        {
            var stopwatch = Stopwatch.StartNew();

            try
            {
                // In a real implementation, this would:
                // 1. Parse the Substrait plan (JSON or binary)
                // 2. Execute the plan against the input tables
                // 3. Return the result as ComplianceResult

                // Simulate some work
                await Task.Delay(10);

                // For this example, we'll just return a simple result
                var columns = new[]
                {
                    new Column("id", ColumnType.Integer, false),
                    new Column("value", ColumnType.String, true)
                };

                var outputData = new TableData(columns);
                outputData.AddRow(1, "example");
                outputData.AddRow(2, "result");

                stopwatch.Stop();

                return new ComplianceResult(
                    "example-test",
                    TestStatus.Passed,
                    outputData,
                    null,
                    null,
                    stopwatch.ElapsedMilliseconds
                );
            }
            catch (Exception ex)
            {
                stopwatch.Stop();

                return new ComplianceResult(
                    "example-test",
                    TestStatus.Error,
                    null,
                    ex.Message,
                    ex.StackTrace,
                    stopwatch.ElapsedMilliseconds
                );
            }
        }

        public async Task<ComplianceResult> ValidatePlanAsync(byte[] planBytes)
        {
            await Task.CompletedTask;

            try
            {
                // In a real implementation, this would validate the plan structure
                // For this example, we'll just check if the plan is not empty
                var isValid = planBytes.Length > 0;

                return new ComplianceResult(
                    "validation",
                    isValid ? TestStatus.Passed : TestStatus.Failed,
                    null,
                    isValid ? null : "Plan is empty",
                    null,
                    0
                );
            }
            catch (Exception ex)
            {
                return new ComplianceResult(
                    "validation",
                    TestStatus.Error,
                    null,
                    ex.Message,
                    ex.StackTrace,
                    0
                );
            }
        }
    }

    /// <summary>
    /// Example usage of the SDK
    /// </summary>
    public class Program
    {
        public static async Task Main(string[] args)
        {
            Console.WriteLine("=== Substrait Compliance SDK Example ===\n");

            // Create an instance of your engine
            var engine = new ExampleEngine();

            // Get engine information
            var info = engine.GetInfo();
            Console.WriteLine("Engine Info:");
            Console.WriteLine($"  Name: {info.Name}");
            Console.WriteLine($"  Version: {info.Version}");
            Console.WriteLine($"  Vendor: {info.Vendor}");
            Console.WriteLine($"  Description: {info.Description}\n");

            // Get engine capabilities
            var capabilities = engine.GetCapabilities();
            Console.WriteLine("Engine Capabilities:");
            Console.WriteLine($"  Supported Relations: {string.Join(", ", capabilities.SupportedRelations)}");
            Console.WriteLine($"  Supported Functions: {string.Join(", ", capabilities.SupportedFunctions)}");
            Console.WriteLine($"  Supported Types: {string.Join(", ", capabilities.SupportedTypes)}\n");

            // Create a test runner
            var runner = new ComplianceRunner(engine, new RunnerOptions
            {
                Verbose = true,
                Parallel = false
            });

            // Load and run a test suite
            try
            {
                var testSuitePath = args.Length > 0
                    ? args[0]
                    : "../../test-suites/functions/comparison/equal.test";

                Console.WriteLine($"Loading test suite: {testSuitePath}\n");

                var loader = new TestSuiteLoader();
                var suite = await loader.LoadFromFileAsync(testSuitePath);

                Console.WriteLine($"Test Suite: {suite.Metadata.Name}");
                Console.WriteLine($"Version: {suite.Metadata.Version}");
                Console.WriteLine($"Total Tests: {suite.TotalCount}\n");

                Console.WriteLine("Running tests...\n");
                var report = await runner.RunTestSuiteAsync(suite);

                // Display results
                Console.WriteLine("\n=== Test Results ===");
                Console.WriteLine($"Total: {report.TotalCount}");
                Console.WriteLine($"Passed: {report.PassedCount}");
                Console.WriteLine($"Failed: {report.FailedCount}");
                Console.WriteLine($"Errors: {report.ErrorCount}");
                Console.WriteLine($"Skipped: {report.SkippedCount}");
                Console.WriteLine($"Pass Rate: {report.PassRate:F1}%");
                Console.WriteLine($"Total Time: {report.TotalExecutionTimeMs}ms");

                // Show individual test results
                Console.WriteLine("\nIndividual Results:");
                foreach (var result in report.Results)
                {
                    var status = result.IsPassed ? "✓" : "✗";
                    Console.WriteLine($"  {status} {result.TestId} ({result.ExecutionTimeMs}ms)");
                    if (result.ErrorMessage != null)
                    {
                        Console.WriteLine($"    Error: {result.ErrorMessage}");
                    }
                }
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"Error running tests: {ex.Message}");
                Environment.Exit(1);
            }
        }
    }
}

// Made with Bob
