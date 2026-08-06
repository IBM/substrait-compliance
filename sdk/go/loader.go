package compliance

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"strings"

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

// ── YAML schema ───────────────────────────────────────────────────────────────

type testSuiteYAML struct {
	Name        string          `yaml:"name"`
	Version     string          `yaml:"version"`
	Description string          `yaml:"description"`
	TestCases   []testCaseYAML  `yaml:"testCases"`
}

type testCaseYAML struct {
	ID             string `yaml:"id"`
	Description    string `yaml:"description"`
	PlanBinary     string `yaml:"planBinary"`
	// Explicit override for expected-output CSV path (relative to YAML dir).
	// Defaults to expected/<id>.csv when omitted.
	ExpectedOutput string `yaml:"expectedOutput"`
}

// ── Loader impl ───────────────────────────────────────────────────────────────

// Load loads a test suite from a YAML metadata file.
// For each test case it reads the plan binary and, if present, the
// expected-output CSV from expected/<id>.csv (or the explicit path in the YAML).
func (l *YAMLTestSuiteLoader) Load(path string) (*TestSuite, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read metadata file: %w", err)
	}

	var def testSuiteYAML
	if err := yaml.Unmarshal(data, &def); err != nil {
		return nil, fmt.Errorf("failed to parse metadata: %w", err)
	}

	baseDir := filepath.Dir(path)

	suite := NewTestSuite(TestSuiteMetadata{
		Name:        def.Name,
		Version:     def.Version,
		Description: def.Description,
	})

	for _, tc := range def.TestCases {
		// Read plan bytes
		planPath := filepath.Join(baseDir, tc.PlanBinary)
		planBytes, err := os.ReadFile(planPath)
		if err != nil {
			return nil, fmt.Errorf("test case %s: failed to read plan %s: %w", tc.ID, planPath, err)
		}

		testCase := NewTestCase(tc.ID, planBytes).WithDescription(tc.Description)

		// Resolve expected-output CSV
		csvPath := filepath.Join(baseDir, "expected", tc.ID+".csv")
		if tc.ExpectedOutput != "" {
			csvPath = filepath.Join(baseDir, tc.ExpectedOutput)
		}
		if _, statErr := os.Stat(csvPath); statErr == nil {
			expected, csvErr := LoadCSV(csvPath)
			if csvErr != nil {
				return nil, fmt.Errorf("test case %s: %w", tc.ID, csvErr)
			}
			testCase.WithExpectedOutput(expected)
		}

		suite.AddTestCase(testCase)
	}

	return suite, nil
}

// Supports checks if the loader supports the given file
func (l *YAMLTestSuiteLoader) Supports(path string) bool {
	ext := filepath.Ext(path)
	return ext == ".yaml" || ext == ".yml"
}

// ── CSV parsing ───────────────────────────────────────────────────────────────

// LoadCSV parses a pipe-delimited CSV file into a TableData.
//
// Typed header format (detected automatically):
//   colname:type|colname:type|...
//
// If every field in the first line contains ':', it is treated as a typed
// header and consumed.  Otherwise columns are named column_1, column_2, …
// and the first line is treated as a data row.
func LoadCSV(path string) (*TableData, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("failed to open CSV %s: %w", path, err)
	}
	defer f.Close()

	var lines []string
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line != "" {
			lines = append(lines, line)
		}
	}
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("reading CSV %s: %w", path, err)
	}
	if len(lines) == 0 {
		return NewTableData(nil), nil
	}

	// Detect typed header
	fields := strings.Split(lines[0], "|")
	hasTypedHeader := true
	for _, f := range fields {
		if !strings.Contains(f, ":") {
			hasTypedHeader = false
			break
		}
	}

	var columns []ColumnMetadata
	dataStart := 0

	if hasTypedHeader {
		for _, f := range fields {
			f = strings.TrimSpace(f)
			idx := strings.LastIndex(f, ":")
			name := strings.TrimSpace(f[:idx])
			typStr := strings.TrimSpace(f[idx+1:])
			columns = append(columns, ColumnMetadata{Name: name, Type: normalizeCSVType(typStr)})
		}
		dataStart = 1
	} else {
		for i := range fields {
			columns = append(columns, ColumnMetadata{
				Name: fmt.Sprintf("column_%d", i+1),
				Type: "string",
			})
		}
	}

	td := NewTableData(columns)
	for _, line := range lines[dataStart:] {
		parts := strings.Split(line, "|")
		row := make(Row, len(parts))
		for i, p := range parts {
			row[i] = strings.TrimSpace(p)
		}
		td.AddRow(row)
	}
	return td, nil
}

// normalizeCSVType maps CSV type strings to canonical names.
func normalizeCSVType(s string) string {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "integer", "int", "int32", "i32", "smallint", "int4":
		return "integer"
	case "bigint", "int64", "i64":
		return "bigint"
	case "double", "fp64", "float8", "numeric", "decimal", "number":
		return "double"
	case "boolean", "bool":
		return "boolean"
	default:
		return "string"
	}
}

// ── Convenience functions ─────────────────────────────────────────────────────

// LoadPlan loads a Substrait plan from a file.
func LoadPlan(path string) ([]byte, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read plan file: %w", err)
	}
	return data, nil
}

// LoadTestSuite is a convenience function to load a test suite.
func LoadTestSuite(path string) (*TestSuite, error) {
	return NewYAMLTestSuiteLoader().Load(path)
}

// AutoTestSuiteLoader tries multiple loaders
type AutoTestSuiteLoader struct {
	loaders []TestSuiteLoader
}

// NewAutoTestSuiteLoader creates a new auto-detecting loader
func NewAutoTestSuiteLoader() *AutoTestSuiteLoader {
	return &AutoTestSuiteLoader{
		loaders: []TestSuiteLoader{NewYAMLTestSuiteLoader()},
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
