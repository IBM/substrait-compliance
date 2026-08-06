package compliance

import (
	"context"
	"os"
	"testing"
)

// ── Pass-through engine ───────────────────────────────────────────────────────

// passThroughEngine returns the pre-registered output for a test, keyed by
// test ID embedded in the plan bytes.
type passThroughEngine struct {
	outputs map[string]*TableData
}

func (e *passThroughEngine) GetInfo() EngineInfo {
	return EngineInfo{Name: "PassThrough", Version: "0.0.0", Vendor: "Test"}
}

func (e *passThroughEngine) GetCapabilities() EngineCapabilities {
	return EngineCapabilities{}
}

func (e *passThroughEngine) ExecutePlan(
	_ context.Context,
	planBytes []byte,
	_ map[string]*TableData,
) (*ComplianceResult, error) {
	testID := string(planBytes)
	result := NewComplianceResult(testID, TestStatusPassed)
	if output, ok := e.outputs[testID]; ok {
		result.OutputData = output
	}
	return result, nil
}

func (e *passThroughEngine) ValidatePlan(_ context.Context, _ []byte) (*ComplianceResult, error) {
	return NewComplianceResult("validation", TestStatusPassed), nil
}

func (e *passThroughEngine) Initialize(_ context.Context) error { return nil }
func (e *passThroughEngine) Shutdown(_ context.Context) error   { return nil }
func (e *passThroughEngine) CanRunTest(_ string) bool           { return true }

// ── Helpers ───────────────────────────────────────────────────────────────────

func makeIntTable(rows [][2]int) *TableData {
	cols := []ColumnMetadata{
		{Name: "a", Type: "integer"},
		{Name: "b", Type: "integer"},
	}
	td := NewTableData(cols)
	for _, r := range rows {
		td.AddRow(Row{r[0], r[1]})
	}
	return td
}

func makeDoubleTable(vals []float64) *TableData {
	cols := []ColumnMetadata{{Name: "val", Type: "double"}}
	td := NewTableData(cols)
	for _, v := range vals {
		td.AddRow(Row{v})
	}
	return td
}

func makeSuiteFromOutputs(outputs map[string]*TableData) *TestSuite {
	suite := NewTestSuite(TestSuiteMetadata{Name: "pass-through", Version: "0.0.0"})
	for id, expected := range outputs {
		tc := NewTestCase(id, []byte(id))
		tc.WithExpectedOutput(expected)
		suite.AddTestCase(tc)
	}
	return suite
}

// ── Tests ─────────────────────────────────────────────────────────────────────

func TestPassThroughEngine_Scores100Percent(t *testing.T) {
	outputs := map[string]*TableData{
		"q01": makeIntTable([][2]int{{1, 2}, {3, 4}}),
		"q02": makeIntTable([][2]int{{10, 20}}),
		"q03": makeIntTable([][2]int{{0, 0}, {1, 1}}),
	}
	suite  := makeSuiteFromOutputs(outputs)
	runner := NewComplianceRunner(&passThroughEngine{outputs: outputs})

	report, err := runner.RunTestSuite(context.Background(), suite)
	if err != nil {
		t.Fatalf("RunTestSuite error: %v", err)
	}

	if report.TotalCount() != 3 {
		t.Errorf("expected 3 tests, got %d", report.TotalCount())
	}
	if report.PassedCount() != 3 {
		t.Errorf("expected 3 passed, got %d (failed: %d)",
			report.PassedCount(), report.FailedCount())
	}
	if rate := report.PassRate(); rate < 99.99 {
		t.Errorf("expected 100%% pass rate, got %.1f%%", rate)
	}
}

func TestValueMismatch_IsDetected(t *testing.T) {
	expected := makeIntTable([][2]int{{1, 2}})
	wrong    := makeIntTable([][2]int{{1, 99}}) // col b is wrong

	suite   := makeSuiteFromOutputs(map[string]*TableData{"mm": expected})
	engine  := &passThroughEngine{outputs: map[string]*TableData{"mm": wrong}}
	runner  := NewComplianceRunner(engine)

	report, err := runner.RunTestSuite(context.Background(), suite)
	if err != nil {
		t.Fatalf("RunTestSuite error: %v", err)
	}

	if report.FailedCount() != 1 {
		t.Errorf("expected 1 failure for wrong value, got %d", report.FailedCount())
	}
}

func TestEpsilon_WithinTolerance_Passes(t *testing.T) {
	expected := makeDoubleTable([]float64{1.0})
	close_   := makeDoubleTable([]float64{1.0 + 5e-10}) // within 1e-9

	suite  := makeSuiteFromOutputs(map[string]*TableData{"c": expected})
	engine := &passThroughEngine{outputs: map[string]*TableData{"c": close_}}
	runner := NewComplianceRunner(engine)

	report, err := runner.RunTestSuite(context.Background(), suite)
	if err != nil {
		t.Fatalf("RunTestSuite error: %v", err)
	}
	if report.PassedCount() != 1 {
		t.Errorf("expected pass within epsilon, got failed=%d", report.FailedCount())
	}
}

func TestEpsilon_OutsideTolerance_Fails(t *testing.T) {
	expected := makeDoubleTable([]float64{1.0})
	far      := makeDoubleTable([]float64{2.0})

	suite  := makeSuiteFromOutputs(map[string]*TableData{"f": expected})
	engine := &passThroughEngine{outputs: map[string]*TableData{"f": far}}
	runner := NewComplianceRunner(engine)

	report, err := runner.RunTestSuite(context.Background(), suite)
	if err != nil {
		t.Fatalf("RunTestSuite error: %v", err)
	}
	if report.FailedCount() != 1 {
		t.Errorf("expected 1 failure outside epsilon, got %d", report.FailedCount())
	}
}

func TestRowCountMismatch_IsDetected(t *testing.T) {
	expected := makeIntTable([][2]int{{1, 2}, {3, 4}})
	shorter  := makeIntTable([][2]int{{1, 2}}) // row missing

	suite  := makeSuiteFromOutputs(map[string]*TableData{"r": expected})
	engine := &passThroughEngine{outputs: map[string]*TableData{"r": shorter}}
	runner := NewComplianceRunner(engine)

	report, err := runner.RunTestSuite(context.Background(), suite)
	if err != nil {
		t.Fatalf("RunTestSuite error: %v", err)
	}
	if report.FailedCount() != 1 {
		t.Errorf("expected 1 failure for row-count mismatch, got %d", report.FailedCount())
	}
}

func TestLoadCSV_TypedHeader(t *testing.T) {
	// Write a temp file and parse it
	content := "a:integer|b:double\n1|1.5\n2|2.5\n"
	tmpFile := t.TempDir() + "/test.csv"
	if err := writeFile(tmpFile, content); err != nil {
		t.Fatal(err)
	}

	td, err := LoadCSV(tmpFile)
	if err != nil {
		t.Fatalf("LoadCSV error: %v", err)
	}
	if td.ColumnCount() != 2 {
		t.Errorf("expected 2 columns, got %d", td.ColumnCount())
	}
	if td.RowCount() != 2 {
		t.Errorf("expected 2 rows, got %d", td.RowCount())
	}
	if td.Columns[0].Name != "a" || td.Columns[0].Type != "integer" {
		t.Errorf("unexpected column 0: %+v", td.Columns[0])
	}
	if td.Columns[1].Name != "b" || td.Columns[1].Type != "double" {
		t.Errorf("unexpected column 1: %+v", td.Columns[1])
	}
}

func writeFile(path, content string) error {
	return os.WriteFile(path, []byte(content), 0o644)
}
