package compliance

import (
	"encoding/json"
	"os"
	"strconv"
	"testing"
)

// Query-output comparison: the vectors shared with the leaderboard's comparators
// (sdk/conformance/comparison-vectors.json), checked against DescribeMismatch.

type vectorTable struct {
	Names []string        `json:"names"`
	Types []string        `json:"types"`
	Rows  [][]interface{} `json:"rows"`
}

type comparisonVector struct {
	Name           string      `json:"name"`
	DeclaredScales []*int      `json:"declaredScales"`
	Expected       vectorTable `json:"expected"`
	Actual         vectorTable `json:"actual"`
	Match          bool        `json:"match"`
}

func decodeVectorValue(t *testing.T, v interface{}) CellValue {
	if m, ok := v.(map[string]interface{}); ok {
		for _, key := range []string{"double", "decimal"} {
			if s, ok := m[key].(string); ok {
				f, err := strconv.ParseFloat(s, 64)
				if err != nil {
					t.Fatalf("bad %s value %q", key, s)
				}
				return f
			}
		}
		t.Fatalf("unexpected value %v", v)
	}
	return v // nil, bool, float64 or string
}

func vectorTableData(t *testing.T, vt vectorTable) *TableData {
	cols := make([]ColumnMetadata, len(vt.Names))
	for i := range vt.Names {
		cols[i] = ColumnMetadata{Name: vt.Names[i], Type: vt.Types[i]}
	}
	td := NewTableData(cols)
	for _, r := range vt.Rows {
		row := make(Row, len(r))
		for i, v := range r {
			row[i] = decodeVectorValue(t, v)
		}
		td.AddRow(row)
	}
	return td
}

func TestComparisonVectors(t *testing.T) {
	data, err := os.ReadFile("../conformance/comparison-vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	var doc struct {
		Vectors []comparisonVector `json:"vectors"`
	}
	if err := json.Unmarshal(data, &doc); err != nil {
		t.Fatal(err)
	}
	if len(doc.Vectors) == 0 {
		t.Fatal("no vectors read")
	}
	for _, v := range doc.Vectors {
		mismatch := DescribeMismatch(vectorTableData(t, v.Expected), vectorTableData(t, v.Actual), v.DeclaredScales)
		if (mismatch == "") != v.Match {
			t.Errorf("%s: match=%v, mismatch=%q", v.Name, v.Match, mismatch)
		}
	}
}

func TestTextIsReadAsNumbersOnlyInNumericColumns(t *testing.T) {
	text := func(v string) *TableData {
		td := NewTableData([]ColumnMetadata{{Name: "code", Type: "varchar"}})
		td.AddRow(Row{v})
		return td
	}
	if DescribeMismatch(text("001"), text("1"), nil) == "" {
		t.Error(`"001" and "1" in a text column should differ`)
	}
}
