package compliance

import (
	"context"
	"testing"
)

type mockEngine struct{}

func (e *mockEngine) GetInfo() EngineInfo {
	return EngineInfo{
		Name:    "MockEngine",
		Version: "1.0.0",
		Vendor:  "Test",
	}
}

func (e *mockEngine) GetCapabilities() EngineCapabilities {
	return EngineCapabilities{
		SupportedRelations: []string{"read", "filter"},
		SupportedFunctions: []string{"add", "subtract"},
		SupportedTypes:     []string{"i32", "i64"},
	}
}

func (e *mockEngine) ExecutePlan(ctx context.Context, planBytes []byte, inputData map[string]*TableData) (*ComplianceResult, error) {
	return NewComplianceResult("test", TestStatusPassed), nil
}

func (e *mockEngine) ValidatePlan(ctx context.Context, planBytes []byte) (*ComplianceResult, error) {
	return NewComplianceResult("validation", TestStatusPassed), nil
}

func (e *mockEngine) Initialize(ctx context.Context) error {
	return nil
}

func (e *mockEngine) Shutdown(ctx context.Context) error {
	return nil
}

func (e *mockEngine) CanRunTest(testID string) bool {
	return true
}

func TestEngineInfo(t *testing.T) {
	engine := &mockEngine{}
	info := engine.GetInfo()

	if info.Name != "MockEngine" {
		t.Errorf("Expected name 'MockEngine', got '%s'", info.Name)
	}
	if info.Version != "1.0.0" {
		t.Errorf("Expected version '1.0.0', got '%s'", info.Version)
	}
}

func TestEngineCapabilities(t *testing.T) {
	engine := &mockEngine{}
	caps := engine.GetCapabilities()

	if !caps.SupportsRelation("read") {
		t.Error("Expected to support 'read' relation")
	}
	if caps.SupportsRelation("join") {
		t.Error("Should not support 'join' relation")
	}

	if !caps.SupportsFunction("add") {
		t.Error("Expected to support 'add' function")
	}
	if caps.SupportsFunction("multiply") {
		t.Error("Should not support 'multiply' function")
	}
}

func TestExecutePlan(t *testing.T) {
	engine := &mockEngine{}
	ctx := context.Background()

	result, err := engine.ExecutePlan(ctx, []byte{}, nil)
	if err != nil {
		t.Fatalf("ExecutePlan failed: %v", err)
	}

	if !result.IsPassed() {
		t.Error("Expected test to pass")
	}
}

// Made with Bob
