package compliance

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
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
	Name        string         `yaml:"name"`
	Version     string         `yaml:"version"`
	Description string         `yaml:"description"`
	TestCases   []testCaseYAML `yaml:"testCases"`
}

type testCaseYAML struct {
	ID          string `yaml:"id"`
	Description string `yaml:"description"`
	PlanBinary  string `yaml:"planBinary"`
	// Optional plan JSON, for the output types the plan declares.
	PlanJSON string `yaml:"planJson"`
	// Explicit override for expected-output CSV path (relative to YAML dir).
	// Defaults to expected/<id>.csv when omitted.
	ExpectedOutput string           `yaml:"expectedOutput"`
	InputTables    []inputTableYAML `yaml:"inputTables"`
}

type inputTableYAML struct {
	Name string `yaml:"name"`
	File string `yaml:"file"`
}

// ── Loader impl ───────────────────────────────────────────────────────────────

// Load loads a test suite from a YAML metadata file in this repository's format
// (test-suites/tpch/metadata.yaml and the like). For each test case it reads:
//
//   - the plan binary;
//   - the input tables (inputTables), typed by the schema the plan reads them with (planJson)
//     and keyed by the table name the plan reads; without planJson, an input table is read
//     like an expected output. A data file read by several cases with the same schema is
//     loaded once, and the cases share that TableData;
//   - the expected-output CSV from expected/<id>.csv (or the explicit path in the YAML), with
//     values parsed by the types in its header;
//   - the decimal scales the plan declares (planJson).
//
// Inputs and expected outputs are read as scripts/run_plan_compliance.py reads them for the
// leaderboard.
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

	tables := map[string]*TableData{}
	for _, tc := range def.TestCases {
		// Read plan bytes
		planPath := filepath.Join(baseDir, tc.PlanBinary)
		planBytes, err := os.ReadFile(planPath)
		if err != nil {
			return nil, fmt.Errorf("test case %s: failed to read plan %s: %w", tc.ID, planPath, err)
		}

		testCase := NewTestCase(tc.ID, planBytes).WithDescription(tc.Description)
		schemas := map[string][]SchemaColumn{}
		if tc.PlanJSON != "" {
			planJSON, err := os.ReadFile(filepath.Join(baseDir, tc.PlanJSON))
			if err != nil {
				return nil, fmt.Errorf("test case %s: %w", tc.ID, err)
			}
			if scales, err := DeclaredScales(planJSON); err == nil {
				testCase.DeclaredScales = scales
			}
			plan, err := ParsePlanJSON(planJSON)
			if err != nil {
				return nil, fmt.Errorf("test case %s: %s: %w", tc.ID, tc.PlanJSON, err)
			}
			if schemas, err = NamedTableSchemas(plan); err != nil {
				return nil, fmt.Errorf("test case %s: %w", tc.ID, err)
			}
		}
		for _, input := range tc.InputTables {
			file := filepath.Clean(filepath.Join(baseDir, input.File))
			name, columns := input.Name, []SchemaColumn(nil)
			for planName, cols := range schemas {
				if strings.EqualFold(planName, input.Name) {
					name, columns = planName, cols
				}
			}
			key := file + "\x00" + fmt.Sprint(columns)
			table, ok := tables[key]
			if !ok {
				if columns != nil {
					table, err = LoadInputTable(file, columns)
				} else {
					table, err = LoadCSV(file)
				}
				if err != nil {
					return nil, fmt.Errorf("test case %s: input table %s: %w", tc.ID, input.Name, err)
				}
				tables[key] = table
			}
			testCase.InputData[name] = table
		}

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

// LoadCSV parses a CSV file (pipe- or comma-delimited, detected from the first line) into
// a TableData, as the Java SDK's loader reads expected outputs.
//
// Typed header format (detected automatically):
//
//	colname:type|colname:type|...
//
// If every field in the first line has the form name:type, it is a typed header: values are
// parsed by type (int64 for integer types; float64 for float, double and decimal; bool for
// booleans), the rest kept as text, and a value that does not parse is kept as text.
// Otherwise columns are named column_1, column_2, … and hold text, and the first line is a
// data row. Values are trimmed; an empty value or "null" is NULL.
func LoadCSV(path string) (*TableData, error) {
	lines, err := readLines(path)
	if err != nil {
		return nil, err
	}
	if len(lines) == 0 {
		return NewTableData(nil), nil
	}
	delimiter := detectDelimiter(lines[0])
	fields := splitLine(lines[0], delimiter)
	hasTypedHeader := true
	for _, f := range fields {
		if i := strings.Index(f, ":"); i <= 0 || i >= len(f)-1 {
			hasTypedHeader = false
			break
		}
	}

	var columns []ColumnMetadata
	var types []string
	data := lines
	if hasTypedHeader {
		for _, f := range fields {
			parts := strings.SplitN(f, ":", 2)
			typ := strings.ToLower(strings.TrimSpace(parts[1]))
			columns = append(columns, ColumnMetadata{Name: strings.TrimSpace(parts[0]), Type: normalizeCSVType(typ), Nullable: true})
			types = append(types, typ)
		}
		data = lines[1:]
	} else {
		for i := range fields {
			columns = append(columns, ColumnMetadata{Name: fmt.Sprintf("column_%d", i+1), Type: "string", Nullable: true})
			types = append(types, "string")
		}
	}

	td := NewTableData(columns)
	for n, line := range data {
		if strings.TrimSpace(line) == "" {
			continue
		}
		values := splitLine(line, delimiter)
		if len(values) != len(columns) {
			return nil, fmt.Errorf("CSV row %d in %s has %d values, expected %d", n+1, path, len(values), len(columns))
		}
		row := make(Row, len(values))
		for i, v := range values {
			row[i] = headerValue(v, types[i])
		}
		td.AddRow(row)
	}
	return td, nil
}

// headerValue parses one expected-output value by its header type.
func headerValue(raw, typ string) CellValue {
	v := strings.TrimSpace(raw)
	if v == "" || strings.EqualFold(v, "null") {
		return nil
	}
	switch typ {
	case "int", "integer", "i8", "i16", "i32", "long", "bigint", "i64":
		if n, err := strconv.ParseInt(v, 10, 64); err == nil {
			return n
		}
	case "float", "fp32", "double", "fp64", "decimal":
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			return f
		}
	case "bool", "boolean":
		return strings.EqualFold(v, "true")
	}
	return v
}

// normalizeCSVType maps CSV type strings to canonical names.
// Type parameters are ignored: decimal(15,2) is a decimal.
func normalizeCSVType(s string) string {
	base := strings.ToLower(strings.TrimSpace(s))
	if i := strings.Index(base, "("); i > 0 {
		base = strings.TrimSpace(base[:i])
	}
	switch base {
	case "integer", "int", "int32", "i32", "i8", "i16", "smallint", "tinyint", "int2", "int4":
		return "integer"
	case "bigint", "long", "int64", "i64", "int8", "hugeint":
		return "bigint"
	case "double", "fp64", "float8", "float", "fp32", "real", "float4", "number":
		return "double"
	case "decimal", "numeric":
		return "decimal"
	case "date", "timestamp", "timestamptz", "time":
		return base
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
