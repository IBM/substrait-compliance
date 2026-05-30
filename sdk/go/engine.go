package compliance

import (
	"context"
)

// EngineInfo contains metadata about a query engine
type EngineInfo struct {
	Name        string
	Version     string
	Vendor      string
	Description string
}

// EngineCapabilities describes what features an engine supports
type EngineCapabilities struct {
	SupportedRelations []string
	SupportedFunctions []string
	SupportedTypes     []string
	Extensions         map[string]string
}

// SupportsRelation checks if a relation is supported
func (c *EngineCapabilities) SupportsRelation(relation string) bool {
	for _, r := range c.SupportedRelations {
		if r == relation {
			return true
		}
	}
	return false
}

// SupportsFunction checks if a function is supported
func (c *EngineCapabilities) SupportsFunction(function string) bool {
	for _, f := range c.SupportedFunctions {
		if f == function {
			return true
		}
	}
	return false
}

// SupportsType checks if a type is supported
func (c *EngineCapabilities) SupportsType(dataType string) bool {
	for _, t := range c.SupportedTypes {
		if t == dataType {
			return true
		}
	}
	return false
}

// ComplianceEngine is the main interface that query engines must implement
type ComplianceEngine interface {
	// GetInfo returns engine metadata
	GetInfo() EngineInfo

	// GetCapabilities returns supported features
	GetCapabilities() EngineCapabilities

	// ExecutePlan executes a Substrait plan with input data
	ExecutePlan(ctx context.Context, planBytes []byte, inputData map[string]*TableData) (*ComplianceResult, error)

	// ValidatePlan validates a Substrait plan without executing it
	ValidatePlan(ctx context.Context, planBytes []byte) (*ComplianceResult, error)

	// Initialize performs any necessary setup before running tests
	// This is optional and can be a no-op
	Initialize(ctx context.Context) error

	// Shutdown performs cleanup after all tests complete
	// This is optional and can be a no-op
	Shutdown(ctx context.Context) error

	// CanRunTest checks if the engine can handle a specific test
	// Return false to skip tests that are known to be unsupported
	CanRunTest(testID string) bool
}

// Made with Bob
