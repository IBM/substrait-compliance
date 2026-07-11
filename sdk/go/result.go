package compliance

import (
	"fmt"
	"time"
)

// TestStatus represents the execution status of a test
type TestStatus string

const (
	TestStatusPassed      TestStatus = "PASSED"
	TestStatusFailed      TestStatus = "FAILED"
	TestStatusSkipped     TestStatus = "SKIPPED"
	TestStatusError       TestStatus = "ERROR"
	TestStatusUnsupported TestStatus = "UNSUPPORTED"
)

// ComplianceResult represents the result of a single test case execution
type ComplianceResult struct {
	TestID         string
	Status         TestStatus
	OutputData     *TableData
	ErrorMessage   string
	ErrorDetails   string
	ExecutionTime  time.Duration
}

// NewComplianceResult creates a new result with the given test ID and status
func NewComplianceResult(testID string, status TestStatus) *ComplianceResult {
	return &ComplianceResult{
		TestID: testID,
		Status: status,
	}
}

// WithOutput sets the output data
func (r *ComplianceResult) WithOutput(data *TableData) *ComplianceResult {
	r.OutputData = data
	return r
}

// WithError sets the error message
func (r *ComplianceResult) WithError(message string) *ComplianceResult {
	r.ErrorMessage = message
	return r
}

// WithErrorDetails sets detailed error information
func (r *ComplianceResult) WithErrorDetails(details string) *ComplianceResult {
	r.ErrorDetails = details
	return r
}

// WithExecutionTime sets the execution time
func (r *ComplianceResult) WithExecutionTime(duration time.Duration) *ComplianceResult {
	r.ExecutionTime = duration
	return r
}

// IsPassed checks if the test passed
func (r *ComplianceResult) IsPassed() bool {
	return r.Status == TestStatusPassed
}

// IsFailed checks if the test failed
func (r *ComplianceResult) IsFailed() bool {
	return r.Status == TestStatusFailed
}

// IsSkipped checks if the test was skipped
func (r *ComplianceResult) IsSkipped() bool {
	return r.Status == TestStatusSkipped
}

// IsError checks if the test had an error
func (r *ComplianceResult) IsError() bool {
	return r.Status == TestStatusError
}

// IsUnsupported checks if the test is unsupported
func (r *ComplianceResult) IsUnsupported() bool {
	return r.Status == TestStatusUnsupported
}

// ComplianceReport aggregates results for a test suite execution
type ComplianceReport struct {
	Results []*ComplianceResult
}

// NewComplianceReport creates a new empty report
func NewComplianceReport() *ComplianceReport {
	return &ComplianceReport{
		Results: make([]*ComplianceResult, 0),
	}
}

// AddResult adds a test result to the report
func (r *ComplianceReport) AddResult(result *ComplianceResult) {
	r.Results = append(r.Results, result)
}

// TotalCount returns the total number of tests
func (r *ComplianceReport) TotalCount() int {
	return len(r.Results)
}

// PassedCount returns the number of passed tests
func (r *ComplianceReport) PassedCount() int {
	return r.countByStatus(TestStatusPassed)
}

// FailedCount returns the number of failed tests
func (r *ComplianceReport) FailedCount() int {
	return r.countByStatus(TestStatusFailed)
}

// SkippedCount returns the number of skipped tests
func (r *ComplianceReport) SkippedCount() int {
	return r.countByStatus(TestStatusSkipped)
}

// ErrorCount returns the number of tests with errors
func (r *ComplianceReport) ErrorCount() int {
	return r.countByStatus(TestStatusError)
}

// UnsupportedCount returns the number of unsupported tests
func (r *ComplianceReport) UnsupportedCount() int {
	return r.countByStatus(TestStatusUnsupported)
}

// PassRate calculates the pass rate as a percentage (0-100)
func (r *ComplianceReport) PassRate() float64 {
	if len(r.Results) == 0 {
		return 0.0
	}
	return (float64(r.PassedCount()) / float64(len(r.Results))) * 100.0
}

// TotalExecutionTime returns the sum of all test execution times
func (r *ComplianceReport) TotalExecutionTime() time.Duration {
	var total time.Duration
	for _, result := range r.Results {
		total += result.ExecutionTime
	}
	return total
}

// AllPassed checks if all tests passed
func (r *ComplianceReport) AllPassed() bool {
	return r.PassedCount() == r.TotalCount()
}

// Summary returns a summary string
func (r *ComplianceReport) Summary() string {
	return fmt.Sprintf("Passed: %d/%d (%.1f%%)",
		r.PassedCount(), r.TotalCount(), r.PassRate())
}

func (r *ComplianceReport) countByStatus(status TestStatus) int {
	count := 0
	for _, result := range r.Results {
		if result.Status == status {
			count++
		}
	}
	return count
}

