package compliance

import (
	"fmt"
	"math"
	"strconv"
	"strings"
)

// Absolute tolerance near zero; relative tolerance for larger magnitudes.
const (
	NumericAbsTolerance = 1e-9
	NumericRelTolerance = 1e-9
)

// DescribeMismatch compares an engine's output with the expected output, as the
// leaderboard's comparators do (sdk/conformance/comparison-vectors.json): same row and
// column counts, column names case-insensitively, column types by family (all numerics are
// one family; dates and times compare as text), rows in order, numbers within
// max(1e-9, 1e-9 * magnitude), everything else as text. NULL (nil, or an empty or "null"
// text value as the CSV loader leaves it) matches only NULL; a numeric string matches the
// number.
//
// declaredScales, when given, holds per column the scale of the decimal type the plan
// declares (nil for other columns); such a column also matches within 10^-scale.
//
// It returns "" when the outputs match, otherwise a description of the first difference.
func DescribeMismatch(expected, actual *TableData, declaredScales []*int) string {
	if expected == nil || actual == nil {
		if expected == actual {
			return ""
		}
		return "engine returned no output"
	}
	if expected.RowCount() != actual.RowCount() {
		return fmt.Sprintf("expected %d rows, got %d", expected.RowCount(), actual.RowCount())
	}
	names := func(t *TableData) []string {
		out := make([]string, len(t.Columns))
		for i, c := range t.Columns {
			out[i] = c.Name
		}
		return out
	}
	if expected.ColumnCount() != actual.ColumnCount() {
		return fmt.Sprintf("expected %d columns %v, got %d %v",
			expected.ColumnCount(), names(expected), actual.ColumnCount(), names(actual))
	}
	for i := range expected.Columns {
		if !strings.EqualFold(strings.TrimSpace(expected.Columns[i].Name), strings.TrimSpace(actual.Columns[i].Name)) {
			return fmt.Sprintf("expected column names %v, got %v", names(expected), names(actual))
		}
	}
	for i := range expected.Columns {
		if typeFamily(expected.Columns[i].Type) != typeFamily(actual.Columns[i].Type) {
			types := func(t *TableData) []string {
				out := make([]string, len(t.Columns))
				for j, c := range t.Columns {
					out[j] = c.Type
				}
				return out
			}
			return fmt.Sprintf("expected column types %v, got %v", types(expected), types(actual))
		}
	}
	for r := range expected.Rows {
		eRow, aRow := expected.Rows[r], actual.Rows[r]
		for c := 0; c < len(eRow) && c < len(aRow); c++ {
			var scale *int
			if c < len(declaredScales) {
				scale = declaredScales[c]
			}
			numeric := typeFamily(expected.Columns[c].Type) == "numeric"
			if !valuesMatch(eRow[c], aRow[c], scale, numeric) {
				return fmt.Sprintf("row %d, column %s: expected %s, got %s",
					r, expected.Columns[c].Name, showValue(eRow[c]), showValue(aRow[c]))
			}
		}
	}
	return ""
}

// typeFamily is the family a column type is compared by.
func typeFamily(t string) string {
	base := strings.ToLower(strings.TrimSpace(t))
	if i := strings.Index(base, "("); i > 0 {
		base = strings.TrimSpace(base[:i])
	}
	switch base {
	case "int", "integer", "i8", "i16", "i32", "int2", "int4", "smallint", "tinyint",
		"long", "bigint", "i64", "int8", "hugeint", "int128", "utinyint", "usmallint",
		"uinteger", "ubigint", "uhugeint", "float", "fp32", "real", "float4", "double",
		"fp64", "decimal", "numeric", "float8", "number":
		return "numeric"
	case "bool", "boolean":
		return "boolean"
	case "varchar", "char", "text", "string", "utf8", "date", "timestamp", "timestamptz", "time":
		return "string"
	default:
		return base
	}
}

// NumbersClose reports whether two numbers match: within the tolerance and, for a declared
// decimal, within 10^-scale. NaN matches NaN and infinity only the same infinity.
func NumbersClose(expected, actual float64, declaredScale *int) bool {
	if math.IsNaN(expected) || math.IsNaN(actual) {
		return math.IsNaN(expected) && math.IsNaN(actual)
	}
	if math.IsInf(expected, 0) || math.IsInf(actual, 0) {
		return expected == actual
	}
	magnitude := math.Max(math.Abs(expected), math.Abs(actual))
	tolerance := math.Max(NumericAbsTolerance, NumericRelTolerance*magnitude)
	if declaredScale != nil {
		tolerance = math.Max(tolerance, math.Pow(10, -float64(*declaredScale))*(1+NumericRelTolerance))
	}
	return math.Abs(expected-actual) <= tolerance
}

func isNullValue(v CellValue) bool {
	if v == nil {
		return true
	}
	if s, ok := v.(string); ok {
		t := strings.TrimSpace(s)
		return t == "" || strings.EqualFold(t, "null")
	}
	return false
}

// valuesMatch compares two cells. Text is read as a number only in a numeric column (the CSV
// loader keeps every value as text) or when the other value is a number, as the Java runner
// does, so "001" and "1" in a text column stay different.
func valuesMatch(expected, actual CellValue, declaredScale *int, numericColumn bool) bool {
	if isNullValue(expected) || isNullValue(actual) {
		return isNullValue(expected) && isNullValue(actual)
	}
	_, eText := expected.(string)
	_, aText := actual.(string)
	if numericColumn || !eText || !aText {
		e, eNum := toFloat64(expected)
		a, aNum := toFloat64(actual)
		if eNum && aNum {
			return NumbersClose(e, a, declaredScale)
		}
	}
	eb, eBool := toBool(expected)
	ab, aBool := toBool(actual)
	if eBool && aBool {
		return eb == ab
	}
	return fmt.Sprintf("%v", expected) == fmt.Sprintf("%v", actual)
}

// toFloat64 reads a number, or a string holding one.
func toFloat64(value CellValue) (float64, bool) {
	switch v := value.(type) {
	case int:
		return float64(v), true
	case int8:
		return float64(v), true
	case int16:
		return float64(v), true
	case int32:
		return float64(v), true
	case int64:
		return float64(v), true
	case uint:
		return float64(v), true
	case uint8:
		return float64(v), true
	case uint16:
		return float64(v), true
	case uint32:
		return float64(v), true
	case uint64:
		return float64(v), true
	case float32:
		return float64(v), true
	case float64:
		return v, true
	case string:
		f, err := strconv.ParseFloat(strings.TrimSpace(v), 64)
		return f, err == nil
	default:
		return 0, false
	}
}

func toBool(value CellValue) (bool, bool) {
	switch v := value.(type) {
	case bool:
		return v, true
	case string:
		switch strings.ToLower(strings.TrimSpace(v)) {
		case "true":
			return true, true
		case "false":
			return false, true
		}
	}
	return false, false
}

func showValue(v CellValue) string {
	if v == nil {
		return "NULL"
	}
	if s, ok := v.(string); ok {
		return strconv.Quote(s)
	}
	return fmt.Sprintf("%v", v)
}
