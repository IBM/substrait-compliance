package compliance

import (
	"testing"
	"time"
)

func TestComplianceResult(t *testing.T) {
	result := NewComplianceResult("test1", TestStatusPassed)

	if result.TestID != "test1" {
		t.Errorf("Expected TestID 'test1', got '%s'", result.TestID)
	}
	if result.Status != TestStatusPassed {
		t.Errorf("Expected status PASSED, got %v", result.Status)
	}
	if !result.IsPassed() {
		t.Error("Expected IsPassed() to be true")
	}
}

func TestComplianceResultWithError(t *testing.T) {
	result := NewComplianceResult("test2", TestStatusFailed).
		WithError("Something went wrong")

	if !result.IsFailed() {
		t.Error("Expected IsFailed() to be true")
	}
	if result.ErrorMessage != "Something went wrong" {
		t.Errorf("Expected error message, got '%s'", result.ErrorMessage)
	}
}

func TestComplianceResultWithOutput(t *testing.T) {
	output := NewTableData([]ColumnMetadata{
		{Name: "id", Type: "INTEGER"},
	})
	output.AddRow(Row{1})

	result := NewComplianceResult("test3", TestStatusPassed).
		WithOutput(output)

	if result.OutputData == nil {
		t.Error("Expected output data to be set")
	}
	if result.OutputData.RowCount() != 1 {
		t.Errorf("Expected 1 row, got %d", result.OutputData.RowCount())
	}
}

func TestComplianceReport(t *testing.T) {
	report := NewComplianceReport()

	report.AddResult(NewComplianceResult("test1", TestStatusPassed))
	report.AddResult(NewComplianceResult("test2", TestStatusFailed))
	report.AddResult(NewComplianceResult("test3", TestStatusPassed))

	if report.TotalCount() != 3 {
		t.Errorf("Expected 3 total tests, got %d", report.TotalCount())
	}
	if report.PassedCount() != 2 {
		t.Errorf("Expected 2 passed tests, got %d", report.PassedCount())
	}
	if report.FailedCount() != 1 {
		t.Errorf("Expected 1 failed test, got %d", report.FailedCount())
	}

	expectedRate := 66.66666666666667
	if report.PassRate() != expectedRate {
		t.Errorf("Expected pass rate %.2f%%, got %.2f%%", expectedRate, report.PassRate())
	}

	if report.AllPassed() {
		t.Error("Expected AllPassed() to be false")
	}
}

func TestComplianceReportAllPassed(t *testing.T) {
	report := NewComplianceReport()

	report.AddResult(NewComplianceResult("test1", TestStatusPassed))
	report.AddResult(NewComplianceResult("test2", TestStatusPassed))

	if !report.AllPassed() {
		t.Error("Expected AllPassed() to be true")
	}
	if report.PassRate() != 100.0 {
		t.Errorf("Expected 100%% pass rate, got %.2f%%", report.PassRate())
	}
}

func TestComplianceReportExecutionTime(t *testing.T) {
	report := NewComplianceReport()

	report.AddResult(NewComplianceResult("test1", TestStatusPassed).
		WithExecutionTime(100 * time.Millisecond))
	report.AddResult(NewComplianceResult("test2", TestStatusPassed).
		WithExecutionTime(200 * time.Millisecond))

	expected := 300 * time.Millisecond
	if report.TotalExecutionTime() != expected {
		t.Errorf("Expected total time %v, got %v", expected, report.TotalExecutionTime())
	}
}

func TestTestStatus(t *testing.T) {
	tests := []struct {
		status   TestStatus
		expected string
	}{
		{TestStatusPassed, "PASSED"},
		{TestStatusFailed, "FAILED"},
		{TestStatusSkipped, "SKIPPED"},
		{TestStatusError, "ERROR"},
		{TestStatusUnsupported, "UNSUPPORTED"},
	}

	for _, tt := range tests {
		if string(tt.status) != tt.expected {
			t.Errorf("Expected status string '%s', got '%s'", tt.expected, string(tt.status))
		}
	}
}

// Made with Bob
