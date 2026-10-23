package compliance

import (
	"fmt"
	"reflect"
	"testing"
)

// The decimal scales a plan declares, as scripts/run_plan_compliance.py works them out.

func scalesText(s []*int) string {
	out := "["
	for i, x := range s {
		if i > 0 {
			out += ", "
		}
		if x == nil {
			out += "null"
		} else {
			out += fmt.Sprint(*x)
		}
	}
	return out + "]"
}

func TestQ01DeclaresDecimalAggregates(t *testing.T) {
	s, err := DeclaredScalesFile("../../test-suites/tpch/plans/q01.json")
	if err != nil {
		t.Fatal(err)
	}
	if got := scalesText(s); got != "[null, null, 2, 2, 4, 6, 2, 2, 2, null]" {
		t.Errorf("q01 scales %s", got)
	}
}

func TestBothEncodingsDeclareTheSameScales(t *testing.T) {
	for q := 1; q <= 22; q++ {
		name := fmt.Sprintf("q%02d.json", q)
		current, err := DeclaredScalesFile("../../test-suites/tpch/plans/" + name)
		if err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		legacy, err := DeclaredScalesFile("../../test-suites/tpch/plans-substrait-0.81/" + name)
		if err != nil {
			t.Fatalf("%s (0.81): %v", name, err)
		}
		if !reflect.DeepEqual(scalesText(current), scalesText(legacy)) {
			t.Errorf("%s: %s vs %s", name, scalesText(current), scalesText(legacy))
		}
	}
}

func TestUnknownRelationGivesNoScales(t *testing.T) {
	if _, err := DeclaredScales([]byte(`{"relations":[{"root":{"input":{"exchange":{}}}}]}`)); err == nil {
		t.Error("expected an error for an unknown relation")
	}
}

func TestLoaderAttachesScales(t *testing.T) {
	suite, err := LoadTestSuite("../../test-suites/tpch/metadata.yaml")
	if err != nil {
		t.Fatal(err)
	}
	q01 := suite.FindTest("q01")
	if q01 == nil || len(q01.DeclaredScales) != 10 || q01.DeclaredScales[6] == nil || *q01.DeclaredScales[6] != 2 {
		t.Errorf("q01 scales not attached: %v", q01)
	}
}
