package compliance

import (
	"testing"
)

func TestLoaderReadsInputTablesTypedByThePlan(t *testing.T) {
	suite, err := LoadTestSuite("../../test-suites/tpch/metadata.yaml")
	if err != nil {
		t.Fatal(err)
	}
	q01 := suite.FindTest("q01")
	lineitem := q01.InputData["LINEITEM"]
	if lineitem == nil {
		t.Fatalf("q01 input tables keyed by the plan's names: %v", keys(q01.InputData))
	}
	if lineitem.RowCount() != 60175 || lineitem.ColumnCount() != 16 {
		t.Fatalf("lineitem: %d rows, %d columns", lineitem.RowCount(), lineitem.ColumnCount())
	}
	if c := lineitem.Columns[4]; c.Name != "L_QUANTITY" || c.Type != "decimal(15,2)" {
		t.Errorf("column 4: %+v", c)
	}
	if v, ok := lineitem.Rows[0][0].(int32); !ok || v != 1 {
		t.Errorf("l_orderkey is i32: %#v", lineitem.Rows[0][0])
	}
	if v, ok := lineitem.Rows[0][4].(string); !ok || v == "" {
		t.Errorf("a decimal stays exact as text: %#v", lineitem.Rows[0][4])
	}
	// Cases reading the same file with the same schema share the table.
	q06 := suite.FindTest("q06")
	if q06.InputData["LINEITEM"] != lineitem {
		t.Error("lineitem loaded twice")
	}
}

func TestInputTableHeaderQuotesAndNulls(t *testing.T) {
	path := t.TempDir() + "/t.csv"
	if err := writeFile(path, "id,name,ok\n1,\"a, b\",true\n2,null,\n"); err != nil {
		t.Fatal(err)
	}
	td, err := LoadInputTable(path, []SchemaColumn{
		{"ID", jsonObj{"i32": jsonObj{}}}, {"NAME", jsonObj{"string": jsonObj{}}}, {"OK", jsonObj{"bool": jsonObj{}}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if td.RowCount() != 2 || td.Rows[0][0] != int32(1) || td.Rows[0][1] != "a, b" || td.Rows[0][2] != true {
		t.Errorf("row 0: %#v", td.Rows)
	}
	if td.Rows[1][1] != nil || td.Rows[1][2] != nil {
		t.Errorf("'null' and '' are NULL: %#v", td.Rows[1])
	}
	if _, err := LoadInputTable(path, []SchemaColumn{{"ID", jsonObj{"i32": jsonObj{}}}}); err == nil {
		t.Error("a row with the wrong number of values is an error")
	}
}

func TestLoadCSVParsesValuesByHeaderType(t *testing.T) {
	path := t.TempDir() + "/e.csv"
	if err := writeFile(path, "n:bigint|x:double|b:boolean|s:varchar\n7|2.5|TRUE|001\n|null|false|x\n"); err != nil {
		t.Fatal(err)
	}
	td, err := LoadCSV(path)
	if err != nil {
		t.Fatal(err)
	}
	if td.Rows[0][0] != int64(7) || td.Rows[0][1] != 2.5 || td.Rows[0][2] != true || td.Rows[0][3] != "001" {
		t.Errorf("row 0: %#v", td.Rows[0])
	}
	if td.Rows[1][0] != nil || td.Rows[1][1] != nil {
		t.Errorf("row 1: %#v", td.Rows[1])
	}
}

func keys(m map[string]*TableData) []string {
	var out []string
	for k := range m {
		out = append(out, k)
	}
	return out
}
