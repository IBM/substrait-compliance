package main

import (
	"context"
	"fmt"
	"log"
	"os"

	compliance "github.com/substrait-io/substrait-compliance/sdk/go"
)

// BasicEngine is a simple example implementation
type BasicEngine struct{}

func (e *BasicEngine) GetInfo() compliance.EngineInfo {
	return compliance.EngineInfo{
		Name:        "BasicEngine",
		Version:     "1.0.0",
		Vendor:      "Example Corp",
		Description: "A basic example Substrait engine",
	}
}

func (e *BasicEngine) GetCapabilities() compliance.EngineCapabilities {
	return compliance.EngineCapabilities{
		SupportedRelations: []string{"read", "filter", "project", "aggregate"},
		SupportedFunctions: []string{"add", "subtract", "multiply", "divide"},
		SupportedTypes:     []string{"i32", "i64", "fp32", "fp64", "string"},
		Extensions:         make(map[string]string),
	}
}

func (e *BasicEngine) ExecutePlan(ctx context.Context, planBytes []byte, inputData map[string]*compliance.TableData) (*compliance.ComplianceResult, error) {
	if len(planBytes) == 0 {
		return compliance.NewComplianceResult("test", compliance.TestStatusFailed).
			WithError("plan is empty"), nil
	}

	if len(inputData) == 0 {
		output := compliance.NewTableData([]compliance.ColumnMetadata{
			{Name: "result", Type: "INTEGER", Nullable: false},
		})
		output.AddRow(compliance.Row{len(planBytes)})

		return compliance.NewComplianceResult("test", compliance.TestStatusPassed).
			WithOutput(output), nil
	}

	for tableName, table := range inputData {
		if table == nil {
			continue
		}

		output := compliance.NewTableData(cloneColumns(table.Columns))
		output.AddRows(cloneRows(table.Rows))
		return compliance.NewComplianceResult("test", compliance.TestStatusPassed).
			WithOutput(output).
			WithErrorDetails(fmt.Sprintf("echoed input table %s", tableName)), nil
	}

	output := compliance.NewTableData([]compliance.ColumnMetadata{
		{Name: "result", Type: "INTEGER", Nullable: false},
	})
	output.AddRow(compliance.Row{0})

	return compliance.NewComplianceResult("test", compliance.TestStatusPassed).
		WithOutput(output), nil
}

func (e *BasicEngine) ValidatePlan(ctx context.Context, planBytes []byte) (*compliance.ComplianceResult, error) {
	if len(planBytes) == 0 {
		return compliance.NewComplianceResult("validation", compliance.TestStatusFailed).
			WithError("Plan is empty"), nil
	}
	return compliance.NewComplianceResult("validation", compliance.TestStatusPassed), nil
}

func (e *BasicEngine) Initialize(ctx context.Context) error {
	log.Println("Initializing engine...")
	return nil
}

func (e *BasicEngine) Shutdown(ctx context.Context) error {
	log.Println("Shutting down engine...")
	return nil
}

func (e *BasicEngine) CanRunTest(testID string) bool {
	return true
}

func cloneColumns(columns []compliance.ColumnMetadata) []compliance.ColumnMetadata {
	cloned := make([]compliance.ColumnMetadata, len(columns))
	copy(cloned, columns)
	return cloned
}

func cloneRows(rows []compliance.Row) []compliance.Row {
	cloned := make([]compliance.Row, len(rows))
	for i, row := range rows {
		rowCopy := make(compliance.Row, len(row))
		copy(rowCopy, row)
		cloned[i] = rowCopy
	}
	return cloned
}

func main() {
	fmt.Println("=== Substrait Compliance Test - Basic Engine ===")

	// Create engine
	engine := &BasicEngine{}
	info := engine.GetInfo()
	fmt.Printf("\nEngine: %s v%s\n", info.Name, info.Version)
	fmt.Printf("Vendor: %s\n", info.Vendor)

	// Get test suite path
	suitePath := "test-suites/tpch/metadata.yaml"
	if len(os.Args) > 1 {
		suitePath = os.Args[1]
	}

	// Load test suite
	fmt.Printf("\nLoading test suite: %s\n", suitePath)
	suite, err := compliance.LoadTestSuite(suitePath)
	if err != nil {
		log.Fatalf("Failed to load test suite: %v", err)
	}
	fmt.Printf("Loaded %d test cases\n", suite.Size())

	// Create runner
	runner := compliance.NewRunnerBuilder(engine).
		ValidatePlans(true).
		Parallel(4).
		OnProgress(func(testID string, current, total int) {
			fmt.Printf("[%d/%d] Running: %s\n", current, total, testID)
		}).
		Build()

	// Run tests
	fmt.Println("\n=== Running Tests ===")
	ctx := context.Background()
	report, err := runner.RunTestSuite(ctx, suite)
	if err != nil {
		log.Fatalf("Failed to run tests: %v", err)
	}

	// Print results
	fmt.Println("\n=== Test Results ===")
	fmt.Printf("Total: %d\n", report.TotalCount())
	fmt.Printf("Passed: %d ✓\n", report.PassedCount())
	fmt.Printf("Failed: %d ✗\n", report.FailedCount())
	fmt.Printf("Skipped: %d ⊘\n", report.SkippedCount())
	fmt.Printf("Errors: %d ⚠\n", report.ErrorCount())
	fmt.Printf("Pass Rate: %.1f%%\n", report.PassRate())
	fmt.Printf("Total Time: %v\n", report.TotalExecutionTime())

	// Print failed tests
	if report.FailedCount() > 0 {
		fmt.Println("\n=== Failed Tests ===")
		for _, result := range report.Results {
			if result.IsFailed() || result.IsError() {
				fmt.Printf("\n%s\n", result.TestID)
				if result.ErrorMessage != "" {
					fmt.Printf("  Error: %s\n", result.ErrorMessage)
				}
			}
		}
	}

	// Exit with appropriate code
	if report.AllPassed() {
		os.Exit(0)
	} else {
		os.Exit(1)
	}
}

