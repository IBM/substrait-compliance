package compliance

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"
)

// Input tables, read as scripts/run_plan_compliance.py reads them for the leaderboard: typed
// by the schema the plan reads them with, keyed by the table name the plan reads.

// SchemaColumn is one column of a table a plan reads: its name and Substrait type (the JSON
// form, e.g. {"decimal": {"precision": 15, "scale": 2}}).
type SchemaColumn struct {
	Name string
	Type interface{}
}

// ParsePlanJSON parses a plan's JSON form; leading // comment lines, as the suites' plan
// files have, are ignored.
func ParsePlanJSON(planJSON []byte) (interface{}, error) {
	var lines []string
	for _, line := range strings.Split(string(planJSON), "\n") {
		if !strings.HasPrefix(strings.TrimSpace(line), "//") {
			lines = append(lines, line)
		}
	}
	var plan interface{}
	if err := json.Unmarshal([]byte(strings.Join(lines, "\n")), &plan); err != nil {
		return nil, err
	}
	return plan, nil
}

// NamedTableSchemas returns, for every named table the plan reads, the columns it reads it
// with. It fails when one table is read with two different schemas.
func NamedTableSchemas(plan interface{}) (map[string][]SchemaColumn, error) {
	found := map[string][]SchemaColumn{}
	var walk func(node interface{}) error
	walk = func(node interface{}) error {
		switch n := node.(type) {
		case jsonObj:
			if read, ok := field(n, "read"); ok {
				named, ok1 := field(read, "namedTable")
				schema, ok2 := field(read, "baseSchema")
				if ok1 && ok2 {
					var names []string
					for _, s := range list(named, "names") {
						names = append(names, fmt.Sprint(s))
					}
					st, _ := field(schema, "struct")
					types := list(st, "types")
					var cols []SchemaColumn
					for i, c := range list(schema, "names") {
						var t interface{}
						if i < len(types) {
							t = types[i]
						}
						cols = append(cols, SchemaColumn{Name: fmt.Sprint(c), Type: t})
					}
					name := strings.Join(names, ".")
					if prev, seen := found[name]; seen && !sameNames(prev, cols) {
						return fmt.Errorf("table %s is read with two different schemas", name)
					}
					found[name] = cols
				}
			}
			for _, v := range n {
				if err := walk(v); err != nil {
					return err
				}
			}
		case []interface{}:
			for _, v := range n {
				if err := walk(v); err != nil {
					return err
				}
			}
		}
		return nil
	}
	if err := walk(plan); err != nil {
		return nil, err
	}
	return found, nil
}

func sameNames(a, b []SchemaColumn) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i].Name != b[i].Name {
			return false
		}
	}
	return true
}

// typeKind is the Substrait type's kind: "i32", "decimal", "varchar", ...
func typeKind(t interface{}) string {
	if m, ok := t.(jsonObj); ok {
		for k := range m {
			return k
		}
	}
	return "string"
}

// columnTypeName is the column's type as TableData names it; a decimal keeps its precision
// and scale (decimal(15,2)), which an engine needs to build the column.
func columnTypeName(t interface{}) string {
	switch k := typeKind(t); k {
	case "i8", "i16", "i32":
		return "integer"
	case "i64":
		return "bigint"
	case "fp32":
		return "float"
	case "fp64":
		return "double"
	case "decimal":
		d, _ := field(t, "decimal")
		p, _ := field(d, "precision")
		s, _ := field(d, "scale")
		return fmt.Sprintf("decimal(%v,%v)", p, s)
	case "bool":
		return "boolean"
	case "date", "time", "timestamp":
		return k
	case "timestampTz", "precisionTimestamp", "precisionTimestampTz":
		return "timestamp"
	default:
		return "string"
	}
}

// planValue converts one input value to the column's type: int32 for i8/i16/i32, int64 for
// i64, float32/float64 for fp32/fp64, bool for bool; a decimal stays exact as its text, and
// dates and strings stay text. Values are trimmed; an empty value or "null" is NULL.
func planValue(raw string, t interface{}) (CellValue, error) {
	v := strings.TrimSpace(raw)
	if v == "" || strings.EqualFold(v, "null") {
		return nil, nil
	}
	switch typeKind(t) {
	case "i8", "i16", "i32":
		n, err := strconv.ParseInt(v, 10, 32)
		return int32(n), err
	case "i64":
		return strconv.ParseInt(v, 10, 64)
	case "fp32":
		f, err := strconv.ParseFloat(v, 32)
		return float32(f), err
	case "fp64":
		return strconv.ParseFloat(v, 64)
	case "decimal":
		if _, err := strconv.ParseFloat(v, 64); err != nil {
			return nil, err
		}
		return v, nil
	case "bool":
		return strings.EqualFold(v, "true"), nil
	default:
		return v, nil
	}
}

// LoadInputTable reads a CSV typed by the columns the plan reads it with. A first row that
// names the plan's columns is a header and skipped.
func LoadInputTable(path string, columns []SchemaColumn) (*TableData, error) {
	lines, err := readLines(path)
	if err != nil {
		return nil, err
	}
	meta := make([]ColumnMetadata, len(columns))
	for i, c := range columns {
		meta[i] = ColumnMetadata{Name: c.Name, Type: columnTypeName(c.Type), Nullable: true}
	}
	td := NewTableData(meta)
	if len(lines) == 0 {
		return td, nil
	}
	delimiter := detectDelimiter(lines[0])
	data := lines
	header := splitLine(lines[0], delimiter)
	if len(header) == len(columns) {
		isHeader := true
		for i, h := range header {
			if !strings.EqualFold(strings.SplitN(strings.TrimSpace(h), ":", 2)[0], columns[i].Name) {
				isHeader = false
				break
			}
		}
		if isHeader {
			data = lines[1:]
		}
	}
	td.Rows = make([]Row, 0, len(data))
	for i, line := range data {
		if strings.TrimSpace(line) == "" {
			continue
		}
		values := splitLine(line, delimiter)
		if len(values) != len(columns) {
			return nil, fmt.Errorf("%s row %d has %d values, expected %d", path, i+1, len(values), len(columns))
		}
		row := make(Row, len(values))
		for j, v := range values {
			if row[j], err = planValue(v, columns[j].Type); err != nil {
				return nil, fmt.Errorf("%s row %d, column %s: %w", path, i+1, columns[j].Name, err)
			}
		}
		td.Rows = append(td.Rows, row)
	}
	return td, nil
}

// ── CSV helpers ───────────────────────────────────────────────────────────────

func readLines(path string) ([]string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to open CSV %s: %w", path, err)
	}
	lines := strings.Split(strings.ReplaceAll(string(data), "\r\n", "\n"), "\n")
	for len(lines) > 0 && lines[len(lines)-1] == "" {
		lines = lines[:len(lines)-1]
	}
	return lines, nil
}

func detectDelimiter(line string) byte {
	if strings.Count(line, "|") > strings.Count(line, ",") {
		return '|'
	}
	return ','
}

// splitLine splits one line on the delimiter outside double quotes ("" inside quotes is a
// quote), trimming each value and removing surrounding quotes.
func splitLine(line string, delimiter byte) []string {
	var values []string
	var current strings.Builder
	inQuotes := false
	for i := 0; i < len(line); i++ {
		ch := line[i]
		switch {
		case ch == '"':
			if inQuotes && i+1 < len(line) && line[i+1] == '"' {
				current.WriteByte('"')
				i++
			} else {
				inQuotes = !inQuotes
			}
		case ch == delimiter && !inQuotes:
			values = append(values, strings.TrimSpace(current.String()))
			current.Reset()
		default:
			current.WriteByte(ch)
		}
	}
	values = append(values, strings.TrimSpace(current.String()))
	for i, v := range values {
		if len(v) >= 2 && v[0] == '"' && v[len(v)-1] == '"' {
			values[i] = v[1 : len(v)-1]
		}
	}
	return values
}
