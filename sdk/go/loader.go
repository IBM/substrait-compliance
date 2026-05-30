package compliance

import (
	"fmt"
	"os"
	"path/filepath"

	"gopkg.in/yaml.v3"
)

// TestSuiteLoader is the interface for loading test suites
type TestSuiteLoader interface {
	Load(path string) (*TestSuite, error)
	Supports(path string) bool
}

// YAMLTestSuiteLoader loads test suites from YAML files
type YAMLTestSuiteLoader struct{}

// NewYAMLTestSuiteLoader creates a new YAML loader
func NewYAMLTestSuiteLoader() *YAMLTestSuiteLoader {
	return &YAMLTestSuiteLoader{}
}

// Load loads a test suite from a YAML metadata file
func (l *YAMLTestSuiteLoader) Load(path string) (*TestSuite, error) {
	// Read the metadata file
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read metadata file: %w", err)
	}

	// Parse metadata
	var metadata TestSuiteMetadata
	if err := yaml.Unmarshal(data, &metadata); err != nil {
		return nil, fmt.Errorf("failed to parse metadata: %w", err)
	}

	suite := NewTestSuite(metadata)

	// TODO: Load test cases from the directory
	// This would involve:
	// 1. Reading plan files (.bin or .json)
	// 2. Reading input data files (.csv)
	// 3. Reading expected output files
	// 4. Creating TestCase objects

	return suite, nil
}

// Supports checks if the loader supports the given file
func (l *YAMLTestSuiteLoader) Supports(path string) bool {
	ext := filepath.Ext(path)
	return ext == ".yaml" || ext == ".yml"
}

// LoadPlan loads a Substrait plan from a file
func LoadPlan(path string) ([]byte, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read plan file: %w", err)
	}
	return data, nil
}

// LoadCSV loads table data from a CSV file
func LoadCSV(path string) (*TableData, error) {
	// TODO: Implement CSV loading
	// This would parse CSV files and create TableData
	return nil, fmt.Errorf("CSV loading not yet implemented")
}

// AutoTestSuiteLoader tries multiple loaders
type AutoTestSuiteLoader struct {
	loaders []TestSuiteLoader
}

// NewAutoTestSuiteLoader creates a new auto-detecting loader
func NewAutoTestSuiteLoader() *AutoTestSuiteLoader {
	return &AutoTestSuiteLoader{
		loaders: []TestSuiteLoader{
			NewYAMLTestSuiteLoader(),
		},
	}
}

// RegisterLoader adds a custom loader
func (l *AutoTestSuiteLoader) RegisterLoader(loader TestSuiteLoader) {
	l.loaders = append(l.loaders, loader)
}

// Load loads using the first compatible loader
func (l *AutoTestSuiteLoader) Load(path string) (*TestSuite, error) {
	for _, loader := range l.loaders {
		if loader.Supports(path) {
			return loader.Load(path)
		}
	}
	return nil, fmt.Errorf("no loader found for file: %s", path)
}

// Supports checks if any loader supports the file
func (l *AutoTestSuiteLoader) Supports(path string) bool {
	for _, loader := range l.loaders {
		if loader.Supports(path) {
			return true
		}
	}
	return false
}

// LoadTestSuite is a convenience function to load a test suite
func LoadTestSuite(path string) (*TestSuite, error) {
	loader := NewAutoTestSuiteLoader()
	return loader.Load(path)
}

// Made with Bob
