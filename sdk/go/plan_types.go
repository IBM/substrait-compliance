package compliance

import (
	"encoding/json"
	"errors"
	"os"
	"strings"
)

// The output column types a Substrait plan declares, from its JSON form, so decimal columns
// can be compared at the scale the plan declares (DescribeMismatch). Mirrors output_types /
// declared_scales in scripts/run_plan_compliance.py.
//
// Types are propagated relation by relation: a read's base schema; filter, sort and fetch
// keep their input's columns, project adds its expressions; an aggregate yields its grouping
// expressions, then its measures; joins concatenate both sides; emit mappings select and
// reorder. An expression's type is its declared outputType (functions, aggregates), the
// referenced column's type, a cast's target or a literal's type.

var errUnknownPlan = errors.New("plan output types cannot be worked out")

type jsonObj = map[string]interface{}

// DeclaredScales returns, per output column, the scale of the decimal type the plan declares
// (nil for other columns). It fails when the plan's output types cannot be worked out. The
// JSON may start with // comment lines, as the suites' plan files do.
func DeclaredScales(planJSON []byte) ([]*int, error) {
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
	types, err := planOutputTypes(plan)
	if err != nil {
		return nil, err
	}
	scales := make([]*int, len(types))
	for i, t := range types {
		if dec, ok := field(t, "decimal"); ok {
			if s, ok := field(dec, "scale"); ok {
				if n, ok := s.(float64); ok {
					v := int(n)
					scales[i] = &v
				}
			}
		}
	}
	return scales, nil
}

// DeclaredScalesFile reads DeclaredScales from a plan JSON file.
func DeclaredScalesFile(path string) ([]*int, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	return DeclaredScales(data)
}

func field(v interface{}, key string) (interface{}, bool) {
	m, ok := v.(jsonObj)
	if !ok {
		return nil, false
	}
	x, ok := m[key]
	return x, ok && x != nil
}

func list(v interface{}, key string) []interface{} {
	x, _ := field(v, key)
	l, _ := x.([]interface{})
	return l
}

func onlyEntry(v interface{}) (string, interface{}, error) {
	m, ok := v.(jsonObj)
	if !ok || len(m) == 0 {
		return "", nil, errUnknownPlan
	}
	for k, x := range m {
		return k, x, nil
	}
	return "", nil, errUnknownPlan
}

func kindType(kind string) interface{} { return jsonObj{kind: jsonObj{}} }

func planOutputTypes(plan interface{}) ([]interface{}, error) {
	for _, r := range list(plan, "relations") {
		if root, ok := field(r, "root"); ok {
			input, ok := field(root, "input")
			if !ok {
				return nil, errUnknownPlan
			}
			return relTypes(input)
		}
	}
	return nil, errUnknownPlan
}

func relTypes(node interface{}) ([]interface{}, error) {
	kind, rel, err := onlyEntry(node)
	if err != nil {
		return nil, err
	}
	var cols []interface{}
	sub := func(key string) ([]interface{}, error) {
		x, ok := field(rel, key)
		if !ok {
			return nil, errUnknownPlan
		}
		return relTypes(x)
	}
	switch kind {
	case "read":
		schema, ok := field(rel, "baseSchema")
		st, ok2 := field(schema, "struct")
		types, ok3 := field(st, "types")
		l, ok4 := types.([]interface{})
		if !ok || !ok2 || !ok3 || !ok4 {
			return nil, errUnknownPlan
		}
		cols = append(cols, l...)
	case "filter", "sort", "fetch":
		if cols, err = sub("input"); err != nil {
			return nil, err
		}
	case "project":
		base, err := sub("input")
		if err != nil {
			return nil, err
		}
		cols = append(cols, base...)
		for _, e := range list(rel, "expressions") {
			t, err := exprType(e, base)
			if err != nil {
				return nil, err
			}
			cols = append(cols, t)
		}
	case "aggregate":
		base, err := sub("input")
		if err != nil {
			return nil, err
		}
		groupings := list(rel, "groupings")
		keys := list(rel, "groupingExpressions") // 0.107: aggregate-level expressions
		if len(keys) == 0 {
			if len(groupings) > 1 {
				return nil, errUnknownPlan // grouping sets in the older encoding
			}
			if len(groupings) == 1 {
				keys = list(groupings[0], "groupingExpressions")
			}
		}
		for _, e := range keys {
			t, err := exprType(e, base)
			if err != nil {
				return nil, err
			}
			cols = append(cols, t)
		}
		for _, m := range list(rel, "measures") {
			measure, ok := field(m, "measure")
			if !ok {
				return nil, errUnknownPlan
			}
			t, _ := field(measure, "outputType")
			cols = append(cols, t)
		}
	case "join", "cross":
		left, err := sub("left")
		if err != nil {
			return nil, err
		}
		right, err := sub("right")
		if err != nil {
			return nil, err
		}
		cols = append(append(cols, left...), right...)
	default:
		return nil, errUnknownPlan
	}
	common, _ := field(rel, "common")
	emit, _ := field(common, "emit")
	if mapping, ok := field(emit, "outputMapping"); ok {
		idx, ok := mapping.([]interface{})
		if !ok {
			return nil, errUnknownPlan
		}
		out := make([]interface{}, 0, len(idx))
		for _, i := range idx {
			n, ok := i.(float64)
			if !ok || int(n) < 0 || int(n) >= len(cols) {
				return nil, errUnknownPlan
			}
			out = append(out, cols[int(n)])
		}
		return out, nil
	}
	return cols, nil
}

// exprType is the expression's type, or nil when it cannot be determined.
func exprType(e interface{}, inputs []interface{}) (interface{}, error) {
	if sel, ok := field(e, "selection"); ok {
		if _, outer := field(sel, "outerReference"); outer {
			return nil, nil
		}
		direct, _ := field(sel, "directReference")
		sf, ok := field(direct, "structField")
		if !ok {
			if m, isMap := direct.(jsonObj); isMap {
				if x, present := m["structField"]; present && x != nil {
					sf, ok = x, true
				}
			}
		}
		if !ok {
			return nil, nil
		}
		if _, child := field(sf, "child"); child {
			return nil, nil
		}
		i := 0
		if n, ok := field(sf, "field"); ok {
			if f, ok := n.(float64); ok {
				i = int(f)
			}
		}
		if i < len(inputs) {
			return inputs[i], nil
		}
		return nil, nil
	}
	for _, key := range []string{"scalarFunction", "windowFunction"} {
		if f, ok := field(e, key); ok {
			t, _ := field(f, "outputType")
			return t, nil
		}
	}
	if lit, ok := field(e, "literal"); ok {
		return literalType(lit), nil
	}
	if c, ok := field(e, "cast"); ok {
		t, _ := field(c, "type")
		return t, nil
	}
	if it, ok := field(e, "ifThen"); ok {
		if ifs := list(it, "ifs"); len(ifs) > 0 {
			then, ok := field(ifs[0], "then")
			if !ok {
				return nil, errUnknownPlan
			}
			return exprType(then, inputs)
		}
		els, ok := field(it, "else")
		if !ok {
			return nil, errUnknownPlan
		}
		return exprType(els, inputs)
	}
	if _, ok := field(e, "singularOrList"); ok {
		return kindType("bool"), nil
	}
	if sq, ok := field(e, "subquery"); ok {
		if scalar, ok := field(sq, "scalar"); ok {
			input, ok := field(scalar, "input")
			if !ok {
				return nil, errUnknownPlan
			}
			out, err := relTypes(input)
			if err != nil {
				return nil, err
			}
			if len(out) > 0 {
				return out[0], nil
			}
			return nil, nil
		}
		return kindType("bool"), nil
	}
	return nil, nil
}

func literalType(lit interface{}) interface{} {
	key, value, err := onlyEntry(lit)
	if err != nil {
		return nil
	}
	switch key {
	case "boolean":
		return kindType("bool")
	case "i8", "i16", "i32", "i64", "fp32", "fp64", "string", "date", "timestamp":
		return kindType(key)
	case "decimal":
		p, ok1 := field(value, "precision")
		s, ok2 := field(value, "scale")
		if !ok1 || !ok2 {
			return nil
		}
		return jsonObj{"decimal": jsonObj{"precision": p, "scale": s}}
	case "varChar":
		return kindType("varchar")
	case "fixedChar":
		return kindType("fixedChar")
	case "null":
		return value
	}
	return nil
}
