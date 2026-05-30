# Substrait Compliance C#/.NET SDK

A C#/.NET SDK for implementing and testing Substrait compliance in query engines.

## Features

- 🎯 **Type-Safe Interface**: Full C# support with nullable reference types
- 🔄 **Async/Await**: Modern async patterns with Task-based APIs
- 📦 **Easy Integration**: Simple interface for implementing compliance engines
- 🧪 **Test Runner**: Built-in test runner with parallel execution support
- 📊 **Rich Results**: Detailed test results and reporting
- 🔍 **YAML Support**: Load test suites from YAML files using YamlDotNet
- ⚡ **Performance**: Efficient execution with configurable parallelism
- 🏢 **Enterprise Ready**: Built on .NET 6+ with full cross-platform support

## Requirements

- .NET 6.0 or higher
- C# 10.0 or higher

## Installation

### Via NuGet (when published)

```bash
dotnet add package Substrait.Compliance
```

### From Source

```bash
cd sdk/csharp
dotnet build
dotnet test
```

## Quick Start

### 1. Implement the IComplianceEngine Interface

```csharp
using Substrait.Compliance;

public class MyQueryEngine : IComplianceEngine
{
    public EngineInfo GetInfo()
    {
        return new EngineInfo(
            Name: "My Query Engine",
            Version: "1.0.0",
            Vendor: "My Company",
            Description: "A high-performance query engine"
        );
    }

    public EngineCapabilities GetCapabilities()
    {
        return new EngineCapabilities(
            SupportedRelations: new[] { "read", "filter", "project", "aggregate", "join" },
            SupportedFunctions: new[] { "add", "subtract", "equal", "greater_than" },
            SupportedTypes: new[] { "i32", "i64", "fp64", "string", "boolean" }
        );
    }

    public async Task<ComplianceResult> ExecutePlanAsync(
        byte[] planBytes,
        IReadOnlyDictionary<string, TableData> inputData)
    {
        try
        {
            // Parse and execute the Substrait plan
            var result = await ExecuteSubstraitPlanAsync(planBytes, inputData);
            
            return new ComplianceResult(
                "test-id",
                TestStatus.Passed,
                result,
                null,
                null,
                executionTimeMs
            );
        }
        catch (Exception ex)
        {
            return new ComplianceResult(
                "test-id",
                TestStatus.Error,
                null,
                ex.Message,
                ex.StackTrace,
                0
            );
        }
    }

    public async Task<ComplianceResult> ValidatePlanAsync(byte[] planBytes)
    {
        // Validate plan structure without executing
        var isValid = await ValidateSubstraitPlanAsync(planBytes);
        
        return new ComplianceResult(
            "validation",
            isValid ? TestStatus.Passed : TestStatus.Failed,
            null,
            isValid ? null : "Invalid plan structure",
            null,
            0
        );
    }
}
```

### 2. Run Compliance Tests

```csharp
using Substrait.Compliance;

public class Program
{
    public static async Task Main(string[] args)
    {
        var engine = new MyQueryEngine();
        
        // Create a test runner
        var runner = new ComplianceRunner(engine, new RunnerOptions
        {
            Verbose = true,
            Parallel = true,
            MaxParallel = 4
        });

        // Load a test suite
        var loader = new TestSuiteLoader();
        var suite = await loader.LoadFromFileAsync("./test-suites/functions/comparison/equal.test");

        // Run the tests
        var report = await runner.RunTestSuiteAsync(suite);

        // Display results
        Console.WriteLine($"Passed: {report.PassedCount}/{report.TotalCount}");
        Console.WriteLine($"Pass Rate: {report.PassRate:F1}%");
        Console.WriteLine($"Total Time: {report.TotalExecutionTimeMs}ms");
    }
}
```

## API Reference

### IComplianceEngine Interface

The main interface that query engines must implement.

#### Methods

##### `EngineInfo GetInfo()`

Returns metadata about the engine.

```csharp
public record EngineInfo(
    string Name,
    string Version,
    string Vendor,
    string? Description = null
);
```

##### `EngineCapabilities GetCapabilities()`

Returns the engine's capabilities.

```csharp
public record EngineCapabilities(
    IReadOnlyList<string> SupportedRelations,
    IReadOnlyList<string> SupportedFunctions,
    IReadOnlyList<string> SupportedTypes,
    IReadOnlyDictionary<string, string>? Extensions = null
);
```

##### `Task<ComplianceResult> ExecutePlanAsync(byte[] planBytes, IReadOnlyDictionary<string, TableData> inputData)`

Executes a Substrait plan and returns the result.

##### `Task<ComplianceResult> ValidatePlanAsync(byte[] planBytes)`

Validates a Substrait plan without executing it.

##### Optional Methods

- `Task InitializeAsync()` - Initialize engine resources
- `Task ShutdownAsync()` - Cleanup engine resources
- `bool CanRunTest(string testId)` - Check if engine can handle a specific test

### TableData Class

Represents tabular data with schema and rows.

```csharp
// Create a table
var columns = new[]
{
    new Column("id", ColumnType.Integer, nullable: false),
    new Column("name", ColumnType.String, nullable: true),
    new Column("score", ColumnType.Float, nullable: true)
};

var table = new TableData(columns);

// Add rows
table.AddRow(1, "Alice", 95.5);
table.AddRow(2, "Bob", 87.3);
table.AddRows(new object?[][]
{
    new object?[] { 3, "Charlie", 92.1 },
    new object?[] { 4, "Diana", null }
});

// Query data
Console.WriteLine(table.RowCount);        // 4
Console.WriteLine(table.ColumnCount);     // 3
var row = table.GetRow(0);                // [1, "Alice", 95.5]
var cell = table.GetCell(1, 1);           // "Bob"
Console.WriteLine(table.IsEmpty);         // false

// Clear data
table.Clear();
```

### ComplianceResult Class

Represents the result of a single test execution.

```csharp
public class ComplianceResult
{
    public string TestId { get; }
    public TestStatus Status { get; }
    public TableData? OutputData { get; }
    public string? ErrorMessage { get; }
    public string? ErrorDetails { get; }
    public long ExecutionTimeMs { get; }

    public bool IsPassed { get; }
    public bool IsFailed { get; }
    public bool IsSkipped { get; }
    public bool IsError { get; }
    public bool IsUnsupported { get; }
}

public enum TestStatus
{
    Passed,
    Failed,
    Skipped,
    Error,
    Unsupported
}
```

### ComplianceReport Class

Aggregated report for a test suite execution.

```csharp
var report = await runner.RunTestSuiteAsync(suite);

// Get counts
Console.WriteLine(report.TotalCount);
Console.WriteLine(report.PassedCount);
Console.WriteLine(report.FailedCount);
Console.WriteLine(report.ErrorCount);
Console.WriteLine(report.SkippedCount);
Console.WriteLine(report.UnsupportedCount);

// Get metrics
Console.WriteLine(report.PassRate);              // 0-100
Console.WriteLine(report.TotalExecutionTimeMs);
Console.WriteLine(report.AllPassed);             // bool
Console.WriteLine(report.Summary);               // "Passed: 45/50 (90.0%)"

// Get individual results
foreach (var result in report.Results)
{
    Console.WriteLine($"{result.TestId}: {result.Status}");
}
```

### ComplianceRunner Class

Executes compliance test suites.

```csharp
var runner = new ComplianceRunner(engine, new RunnerOptions
{
    FailFast = false,        // Stop on first failure
    Parallel = true,         // Run tests in parallel
    MaxParallel = 4,         // Max parallel tests
    Tags = new[] { "arithmetic" },   // Filter by tags
    TestIdPattern = "^add_", // Filter by test ID pattern (regex)
    Verbose = true           // Verbose output
});

// Run a single test suite
var report = await runner.RunTestSuiteAsync(suite);

// Run a single test case
var result = await runner.RunTestCaseAsync(testCase);
```

### Test Suite Loader

Load test suites from YAML files.

```csharp
var loader = new TestSuiteLoader();

// Load a single test suite
var suite = await loader.LoadFromFileAsync("./path/to/test.yaml");

// Load all test suites from a directory
var suites = await loader.LoadFromDirectoryAsync("./test-suites/functions");

// Access test suite data
Console.WriteLine(suite.Metadata.Name);
Console.WriteLine(suite.Metadata.Version);
Console.WriteLine(suite.TotalCount);
Console.WriteLine(suite.ActiveCount);
Console.WriteLine(suite.SkippedCount);

// Filter test cases
var arithmeticTests = suite.FilterByTag("arithmetic");
var activeTests = suite.GetActiveTestCases();
```

## Advanced Usage

### Custom Result Comparator

```csharp
public class CustomComparator : ResultComparator
{
    protected override bool CompareValues(object? expected, object? actual)
    {
        // Custom comparison logic
        return base.CompareValues(expected, actual);
    }
}

var runner = new ComplianceRunner(engine);
runner.Comparator = new CustomComparator();
```

### Async Initialization

```csharp
public class MyEngine : IComplianceEngine
{
    private DatabaseConnection? _connection;

    public async Task InitializeAsync()
    {
        _connection = await ConnectToDatabaseAsync();
    }

    public async Task ShutdownAsync()
    {
        if (_connection != null)
        {
            await _connection.CloseAsync();
        }
    }

    // ... other methods
}

var engine = new MyEngine();
await engine.InitializeAsync();

try
{
    var runner = new ComplianceRunner(engine);
    var report = await runner.RunTestSuiteAsync(suite);
}
finally
{
    await engine.ShutdownAsync();
}
```

### Selective Test Execution

```csharp
// Run only tests with specific tags
var runner = new ComplianceRunner(engine, new RunnerOptions
{
    Tags = new List<string> { "arithmetic", "comparison" }
});

// Run tests matching a pattern
var runner = new ComplianceRunner(engine, new RunnerOptions
{
    TestIdPattern = "^(add|subtract)_"
});

// Check if engine can run specific tests
public class MyEngine : IComplianceEngine
{
    public bool CanRunTest(string testId)
    {
        // Skip tests that require unsupported features
        return !testId.Contains("window_function");
    }
    
    // ... other methods
}
```

### Parallel Execution

```csharp
// Run tests in parallel with custom concurrency
var runner = new ComplianceRunner(engine, new RunnerOptions
{
    Parallel = true,
    MaxParallel = 8  // Run up to 8 tests concurrently
});

var report = await runner.RunTestSuiteAsync(suite);
```

## Building and Testing

```bash
# Restore dependencies
dotnet restore

# Build the project
dotnet build

# Run tests
dotnet test

# Run tests with coverage
dotnet test /p:CollectCoverage=true

# Create NuGet package
dotnet pack
```

## Examples

See the [Examples](./Examples) directory for complete examples:

- `BasicEngine.cs` - Basic engine implementation and usage

## Project Structure

```
sdk/csharp/
├── Substrait.Compliance.csproj  # Project file
├── IComplianceEngine.cs         # Main engine interface
├── TableData.cs                 # Data structures
├── ComplianceResult.cs          # Result types
├── TestSuite.cs                 # Test suite definitions
├── TestSuiteLoader.cs           # YAML loader
├── ComplianceRunner.cs          # Test runner
├── Examples/
│   └── BasicEngine.cs           # Example implementation
├── Tests/
│   └── TableDataTests.cs        # Unit tests
└── README.md                    # This file
```

## Contributing

Contributions are welcome! Please see the main project [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines.

## License

Apache License 2.0 - See [LICENSE](../../LICENSE) for details.

## Support

- 📖 [Documentation](https://substrait.io)
- 💬 [Discussions](https://github.com/substrait-io/substrait-compliance/discussions)
- 🐛 [Issue Tracker](https://github.com/substrait-io/substrait-compliance/issues)