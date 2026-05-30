package compliance

import (
	"context"
	"fmt"
	"sync"
	"time"
)

// RunnerConfig contains configuration for test execution
type RunnerConfig struct {
	StopOnFirstFailure bool
	ValidatePlans      bool
	CompareResults     bool
	MaxParallelTests   int
	Timeout            time.Duration
}

// DefaultRunnerConfig returns a default configuration
func DefaultRunnerConfig() RunnerConfig {
	return RunnerConfig{
		StopOnFirstFailure: false,
		ValidatePlans:      true,
		CompareResults:     true,
		MaxParallelTests:   1, // Sequential by default
		Timeout:            5 * time.Minute,
	}
}

// ProgressCallback is called for each test execution
type ProgressCallback func(testID string, current, total int)

// ComplianceRunner executes compliance tests against an engine
type ComplianceRunner struct {
	engine   ComplianceEngine
	config   RunnerConfig
	progress ProgressCallback
}

// NewComplianceRunner creates a new runner with default config
func NewComplianceRunner(engine ComplianceEngine) *ComplianceRunner {
	return &ComplianceRunner{
		engine: engine,
		config: DefaultRunnerConfig(),
	}
}

// NewComplianceRunnerWithConfig creates a runner with custom config
func NewComplianceRunnerWithConfig(engine ComplianceEngine, config RunnerConfig) *ComplianceRunner {
	return &ComplianceRunner{
		engine: engine,
		config: config,
	}
}

// SetProgressCallback sets the progress callback function
func (r *ComplianceRunner) SetProgressCallback(callback ProgressCallback) {
	r.progress = callback
}

// RunTestSuite runs all tests in a test suite
func (r *ComplianceRunner) RunTestSuite(ctx context.Context, suite *TestSuite) (*ComplianceReport, error) {
	report := NewComplianceReport()
	total := suite.Size()

	// Initialize engine
	if err := r.engine.Initialize(ctx); err != nil {
		return nil, fmt.Errorf("failed to initialize engine: %w", err)
	}
	defer r.engine.Shutdown(ctx)

	if r.config.MaxParallelTests > 1 {
		// Parallel execution
		return r.runParallel(ctx, suite, total)
	}

	// Sequential execution
	for i, testCase := range suite.TestCases {
		if r.progress != nil {
			r.progress(testCase.ID, i+1, total)
		}

		result, err := r.RunTestCase(ctx, testCase)
		if err != nil {
			result = NewComplianceResult(testCase.ID, TestStatusError).
				WithError(err.Error())
		}

		report.AddResult(result)

		if r.config.StopOnFirstFailure && !result.IsPassed() {
			break
		}
	}

	return report, nil
}

// runParallel executes tests in parallel
func (r *ComplianceRunner) runParallel(ctx context.Context, suite *TestSuite, total int) (*ComplianceReport, error) {
	report := NewComplianceReport()
	var mu sync.Mutex
	var wg sync.WaitGroup

	// Create a semaphore to limit concurrency
	sem := make(chan struct{}, r.config.MaxParallelTests)
	current := 0

	for _, testCase := range suite.TestCases {
		wg.Add(1)
		go func(tc *TestCase) {
			defer wg.Done()

			// Acquire semaphore
			sem <- struct{}{}
			defer func() { <-sem }()

			mu.Lock()
			current++
			idx := current
			mu.Unlock()

			if r.progress != nil {
				r.progress(tc.ID, idx, total)
			}

			result, err := r.RunTestCase(ctx, tc)
			if err != nil {
				result = NewComplianceResult(tc.ID, TestStatusError).
					WithError(err.Error())
			}

			mu.Lock()
			report.AddResult(result)
			mu.Unlock()
		}(testCase)
	}

	wg.Wait()
	return report, nil
}

// RunTestCase runs a single test case
func (r *ComplianceRunner) RunTestCase(ctx context.Context, testCase *TestCase) (*ComplianceResult, error) {
	// Check if engine can run this test
	if !r.engine.CanRunTest(testCase.ID) {
		return NewComplianceResult(testCase.ID, TestStatusSkipped).
			WithError("Test skipped by engine"), nil
	}

	// Create context with timeout
	if r.config.Timeout > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, r.config.Timeout)
		defer cancel()
	}

	// Validate plan if configured
	if r.config.ValidatePlans {
		validationResult, err := r.engine.ValidatePlan(ctx, testCase.PlanBytes)
		if err != nil {
			return nil, fmt.Errorf("validation error: %w", err)
		}
		if !validationResult.IsPassed() {
			return validationResult, nil
		}
	}

	// Execute the plan
	start := time.Now()
	result, err := r.engine.ExecutePlan(ctx, testCase.PlanBytes, testCase.InputData)
	if err != nil {
		return nil, fmt.Errorf("execution error: %w", err)
	}
	result.ExecutionTime = time.Since(start)

	// Compare results if configured and expected output exists
	if r.config.CompareResults && testCase.ExpectedOutput != nil && result.OutputData != nil {
		if !r.compareOutputs(result.OutputData, testCase.ExpectedOutput) {
			result.Status = TestStatusFailed
			result.ErrorMessage = "Output does not match expected result"
		}
	}

	return result, nil
}

// compareOutputs compares actual and expected outputs
func (r *ComplianceRunner) compareOutputs(actual, expected *TableData) bool {
	// Simple comparison - can be enhanced with more sophisticated logic
	if actual.RowCount() != expected.RowCount() {
		return false
	}
	if actual.ColumnCount() != expected.ColumnCount() {
		return false
	}

	// TODO: Implement detailed cell-by-cell comparison
	// with type-aware comparison and tolerance for floating point

	return true
}

// RunnerBuilder provides a fluent interface for creating runners
type RunnerBuilder struct {
	engine   ComplianceEngine
	config   RunnerConfig
	progress ProgressCallback
}

// NewRunnerBuilder creates a new builder
func NewRunnerBuilder(engine ComplianceEngine) *RunnerBuilder {
	return &RunnerBuilder{
		engine: engine,
		config: DefaultRunnerConfig(),
	}
}

// StopOnFailure sets whether to stop on first failure
func (b *RunnerBuilder) StopOnFailure(stop bool) *RunnerBuilder {
	b.config.StopOnFirstFailure = stop
	return b
}

// ValidatePlans sets whether to validate plans before execution
func (b *RunnerBuilder) ValidatePlans(validate bool) *RunnerBuilder {
	b.config.ValidatePlans = validate
	return b
}

// CompareResults sets whether to compare results with expected output
func (b *RunnerBuilder) CompareResults(compare bool) *RunnerBuilder {
	b.config.CompareResults = compare
	return b
}

// Parallel sets the number of parallel test executions
func (b *RunnerBuilder) Parallel(threads int) *RunnerBuilder {
	b.config.MaxParallelTests = threads
	return b
}

// Timeout sets the timeout for each test
func (b *RunnerBuilder) Timeout(timeout time.Duration) *RunnerBuilder {
	b.config.Timeout = timeout
	return b
}

// OnProgress sets the progress callback
func (b *RunnerBuilder) OnProgress(callback ProgressCallback) *RunnerBuilder {
	b.progress = callback
	return b
}

// Build creates the runner
func (b *RunnerBuilder) Build() *ComplianceRunner {
	runner := NewComplianceRunnerWithConfig(b.engine, b.config)
	if b.progress != nil {
		runner.SetProgressCallback(b.progress)
	}
	return runner
}

// Made with Bob
