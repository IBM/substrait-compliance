# Substrait Compliance SDK - Go

Go SDK for decentralized Substrait compliance testing with built-in concurrency support.

## Features

- **Idiomatic Go**: Clean interfaces, error handling, and context support
- **Concurrent Execution**: Built-in goroutines for parallel test execution
- **Type Safety**: Strong typing with interfaces
- **Context Support**: Proper context propagation for cancellation and timeouts
- **Zero Dependencies**: Minimal external dependencies
- **Cross-Platform**: Works on Linux, macOS, and Windows

## Requirements

- Go 1.21 or higher
- Protocol Buffers (for Substrait plans)
- yaml-cpp (for test suite loading)

## Installation

```bash
go get github.com/IBM/substrait-compliance/sdk/go
```

## Quick Start

```go
package main

import (
    "context"
    "fmt"
    "log"
    
    compliance "github.com/IBM/substrait-compliance/sdk/go"
)

// 1. Implement the ComplianceEngine interface
type MyEngine struct{}

func (e *MyEngine) GetInfo() compliance.EngineInfo {
    return compliance.EngineInfo{
        Name:        "MyEngine",
        Version:     "1.0.0",
        Vendor:      "MyCompany",
        Description: "My Substrait query engine",
    }
}

func (e *MyEngine) GetCapabilities() compliance.EngineCapabilities {
    return compliance.EngineCapabilities{
        SupportedRelations: []string{"read", "filter", "project"},
        SupportedFunctions: []string{"add", "subtract", "multiply"},
        SupportedTypes:     []string{"i32", "i64", "string"},
    }
}

func (e *MyEngine) ExecutePlan(
    ctx context.Context,
    planBytes []byte,
    inputData map[string]*compliance.TableData,
) (*compliance.ComplianceResult, error) {
    // Execute your Substrait plan here
    output := compliance.NewTableData([]compliance.ColumnMetadata{
        {Name: "result", Type: "INTEGER"},
    })
    output.AddRow(compliance.Row{42})
    
    return compliance.NewComplianceResult("test", compliance.TestStatusPassed).
        WithOutput(output), nil
}

func (e *MyEngine) ValidatePlan(
    ctx context.Context,
    planBytes []byte,
) (*compliance.ComplianceResult, error) {
    // Validate plan structure
    if len(planBytes) == 0 {
        return compliance.NewComplianceResult("validation", compliance.TestStatusFailed).
            WithError("Plan is empty"), nil
    }
    return compliance.NewComplianceResult("validation", compliance.TestStatusPassed), nil
}

func (e *MyEngine) Initialize(ctx context.Context) error {
    // Optional: Setup resources
    return nil
}

func (e *MyEngine) Shutdown(ctx context.Context) error {
    // Optional: Cleanup resources
    return nil
}

func (e *MyEngine) CanRunTest(testID string) bool {
    return true // Run all tests
}

func main() {
    // 2. Create engine instance
    engine := &MyEngine{}
    
    // 3. Load test suite
    suite, err := compliance.LoadTestSuite("test-suites/tpch/metadata.yaml")
    if err != nil {
        log.Fatal(err)
    }
    
    // 4. Create and configure runner
    runner := compliance.NewRunnerBuilder(engine).
        ValidatePlans(true).
        Parallel(4).  // Run 4 tests concurrently
        OnProgress(func(testID string, current, total int) {
            fmt.Printf("[%d/%d] Running: %s\n", current, total, testID)
        }).
        Build()
    
    // 5. Run tests
    ctx := context.Background()
    report, err := runner.RunTestSuite(ctx, suite)
    if err != nil {
        log.Fatal(err)
    }
    
    // 6. Check results
    fmt.Printf("\n=== Results ===\n")
    fmt.Printf("Passed: %d/%d\n", report.PassedCount(), report.TotalCount())
    fmt.Printf("Pass Rate: %.1f%%\n", report.PassRate())
    fmt.Printf("Total Time: %v\n", report.TotalExecutionTime())
    
    // Print failed tests
    for _, result := range report.Results {
        if !result.IsPassed() {
            fmt.Printf("Failed: %s - %s\n", result.TestID, result.ErrorMessage)
        }
    }
}
```

## Building Your Project

### go.mod

```go
module myengine

go 1.21

require (
    github.com/IBM/substrait-compliance/sdk/go v0.1.0
)
```

### Build and Run

```bash
# Build
go build -o myengine main.go

# Run
./myengine

# Run with specific test suite
./myengine test-suites/functions/metadata.yaml
```

## Advanced Usage

### Concurrent Execution

```go
// Run tests in parallel with 8 goroutines
runner := compliance.NewRunnerBuilder(engine).
    Parallel(8).
    Build()

report, err := runner.RunTestSuite(ctx, suite)
```

### Context and Timeouts

```go
// Set timeout for entire test suite
ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
defer cancel()

// Set timeout per test
runner := compliance.NewRunnerBuilder(engine).
    Timeout(30 * time.Second).
    Build()

report, err := runner.RunTestSuite(ctx, suite)
```

### Progress Tracking

```go
runner := compliance.NewRunnerBuilder(engine).
    OnProgress(func(testID string, current, total int) {
        percentage := float64(current) / float64(total) * 100
        fmt.Printf("Progress: %.1f%% (%d/%d) - %s\n", 
            percentage, current, total, testID)
    }).
    Build()
```

### Filtering Tests by Tag

```go
suite, _ := compliance.LoadTestSuite("test-suites/functions/metadata.yaml")

// Get only arithmetic tests
arithmeticTests := suite.GetTestsByTag("arithmetic")

// Run filtered tests
for _, test := range arithmeticTests {
    result, err := runner.RunTestCase(ctx, test)
    // Process result...
}
```

### Custom Configuration

```go
config := compliance.RunnerConfig{
    StopOnFirstFailure: true,
    ValidatePlans:      true,
    CompareResults:     true,
    MaxParallelTests:   4,
    Timeout:            5 * time.Minute,
}

runner := compliance.NewComplianceRunnerWithConfig(engine, config)
```

### Error Handling

```go
result, err := engine.ExecutePlan(ctx, planBytes, inputData)
if err != nil {
    // Handle execution error
    return compliance.NewComplianceResult("test", compliance.TestStatusError).
        WithError(err.Error()).
        WithErrorDetails(fmt.Sprintf("Stack trace: %+v", err)), nil
}
```

## API Reference

### Core Interfaces

#### `ComplianceEngine`
Main interface for query engines.

**Methods:**
- `GetInfo() EngineInfo` - Return engine metadata
- `GetCapabilities() EngineCapabilities` - Return supported features
- `ExecutePlan(ctx, planBytes, inputData) (*ComplianceResult, error)` - Execute a plan
- `ValidatePlan(ctx, planBytes) (*ComplianceResult, error)` - Validate a plan
- `Initialize(ctx) error` - Optional setup
- `Shutdown(ctx) error` - Optional cleanup
- `CanRunTest(testID) bool` - Check if test can run

#### `ComplianceRunner`
Executes test suites.

**Methods:**
- `RunTestSuite(ctx, suite) (*ComplianceReport, error)` - Run all tests
- `RunTestCase(ctx, testCase) (*ComplianceResult, error)` - Run single test
- `SetProgressCallback(callback)` - Set progress notification

#### `TestSuite`
Collection of test cases.

**Methods:**
- `AddTestCase(test)` - Add a test
- `Size() int` - Get test count
- `GetTestsByTag(tag) []*TestCase` - Filter by tag
- `FindTest(id) *TestCase` - Find by ID

### Data Types

#### `TableData`
Represents tabular data.

```go
table := compliance.NewTableData([]compliance.ColumnMetadata{
    {Name: "id", Type: "INTEGER", Nullable: false},
    {Name: "name", Type: "STRING", Nullable: true},
})

table.AddRow(compliance.Row{1, "Alice"})
table.AddRow(compliance.Row{2, "Bob"})

fmt.Printf("Rows: %d\n", table.RowCount())
```

#### `ComplianceResult`
Result of test execution.

```go
result := compliance.NewComplianceResult("test1", compliance.TestStatusPassed).
    WithOutput(outputData).
    WithExecutionTime(100 * time.Millisecond)

if result.IsPassed() {
    fmt.Println("Test passed!")
}
```

## Testing

```bash
# Run tests
go test ./...

# Run with coverage
go test -cover ./...

# Run with race detector
go test -race ./...

# Verbose output
go test -v ./...
```

## Examples

See the `examples/` directory for complete examples:

- `basic_engine.go` - Minimal engine implementation
- `advanced_engine.go` - Full-featured engine (coming soon)
- `parallel_execution.go` - Concurrent testing (coming soon)

## Performance Tips

1. **Use goroutines**: Set `Parallel()` to match your CPU cores
2. **Context cancellation**: Always use context for cancellation support
3. **Buffered channels**: Use buffered channels for high-throughput scenarios
4. **Profiling**: Use `pprof` to identify bottlenecks
5. **Memory**: Pre-allocate slices when size is known

## Benchmarking

```go
func BenchmarkExecutePlan(b *testing.B) {
    engine := &MyEngine{}
    ctx := context.Background()
    planBytes := loadTestPlan()
    inputData := loadTestData()
    
    b.ResetTimer()
    for i := 0; i < b.N; i++ {
        engine.ExecutePlan(ctx, planBytes, inputData)
    }
}
```

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](../../CONTRIBUTING.md).

## License

Apache License 2.0

## Support

- GitHub Issues: https://github.com/IBM/substrait-compliance/issues
- Documentation: https://substrait.io/compliance
- Slack: #substrait-compliance