//! The output column types a Substrait plan declares, from its JSON form, so decimal
//! columns can be compared at the scale the plan declares. Mirrors `output_types` /
//! `declared_scales` in scripts/run_plan_compliance.py and `PlanOutputTypes` in the Java SDK.
//!
//! Types are propagated relation by relation: a read's base schema; filter, sort and fetch
//! keep their input's columns, project adds its expressions; an aggregate yields its grouping
//! expressions, then its measures; joins concatenate both sides; emit mappings select and
//! reorder. An expression's type is its declared outputType (functions, aggregates), the
//! referenced column's type, a cast's target or a literal's type.
//!
//! JSON is read with serde_yaml (JSON is valid YAML), so this needs no extra dependency.

use serde_yaml::{Mapping, Value};

type Types = Vec<Option<Value>>;

/// A plan's JSON form, parsed; leading `//` comment lines, as the suites' plan files have,
/// are ignored.
pub fn parse_plan_json(plan_json: &str) -> Result<Value, String> {
    let text: String = plan_json
        .lines()
        .filter(|l| !l.trim_start().starts_with("//"))
        .collect::<Vec<_>>()
        .join("\n");
    serde_yaml::from_str(&text).map_err(|e| e.to_string())
}

/// For every named table the plan reads, the columns it reads it with: (name, Substrait
/// type). Fails when one table is read with two different schemas.
pub fn named_table_schemas(
    plan: &Value,
) -> Result<std::collections::HashMap<String, Vec<(String, Value)>>, String> {
    fn walk(
        node: &Value,
        found: &mut std::collections::HashMap<String, Vec<(String, Value)>>,
    ) -> Result<(), String> {
        match node {
            Value::Mapping(m) => {
                if let Some(read) = node.get("read") {
                    if let (Some(named), Some(schema)) =
                        (read.get("namedTable"), read.get("baseSchema"))
                    {
                        let name = seq(named.get("names"))
                            .iter()
                            .map(|n| n.as_str().unwrap_or_default().to_string())
                            .collect::<Vec<_>>()
                            .join(".");
                        let types = seq(schema.get("struct").and_then(|s| s.get("types")));
                        let columns: Vec<(String, Value)> = seq(schema.get("names"))
                            .iter()
                            .enumerate()
                            .map(|(i, n)| {
                                (
                                    n.as_str().unwrap_or_default().to_string(),
                                    types.get(i).cloned().unwrap_or(Value::Null),
                                )
                            })
                            .collect();
                        if let Some(prev) = found.get(&name) {
                            if prev.iter().map(|c| &c.0).ne(columns.iter().map(|c| &c.0)) {
                                return Err(format!(
                                    "table {name} is read with two different schemas"
                                ));
                            }
                        }
                        found.insert(name, columns);
                    }
                }
                for v in m.values() {
                    walk(v, found)?;
                }
            }
            Value::Sequence(s) => {
                for v in s {
                    walk(v, found)?;
                }
            }
            _ => {}
        }
        Ok(())
    }
    let mut found = std::collections::HashMap::new();
    walk(plan, &mut found)?;
    Ok(found)
}

/// The scale of each output column the plan declares as decimal (None for other columns),
/// or None when the plan's output types cannot be worked out. `plan_json` may start with
/// `//` comment lines, as the suites' plan files do.
pub fn declared_scales(plan_json: &str) -> Option<Vec<Option<u32>>> {
    let plan = parse_plan_json(plan_json).ok()?;
    let types = output_types(&plan)?;
    Some(
        types
            .iter()
            .map(|t| {
                t.as_ref()
                    .and_then(|t| t.get("decimal"))
                    .and_then(|d| d.get("scale"))
                    .and_then(Value::as_u64)
                    .map(|s| s as u32)
            })
            .collect(),
    )
}

/// The plan root's output types (an entry is None where it cannot be determined).
pub fn output_types(plan: &Value) -> Option<Types> {
    let root = plan
        .get("relations")?
        .as_sequence()?
        .iter()
        .find_map(|r| r.get("root"))?;
    rel_types(root.get("input")?)
}

fn only_entry(node: &Value) -> Option<(&str, &Value)> {
    let map: &Mapping = node.as_mapping()?;
    let (k, v) = map.iter().next()?;
    Some((k.as_str()?, v))
}

fn seq(v: Option<&Value>) -> &[Value] {
    v.and_then(Value::as_sequence)
        .map(|s| s.as_slice())
        .unwrap_or(&[])
}

fn rel_types(node: &Value) -> Option<Types> {
    let (kind, rel) = only_entry(node)?;
    let cols: Types = match kind {
        "read" => rel
            .get("baseSchema")?
            .get("struct")?
            .get("types")?
            .as_sequence()?
            .iter()
            .cloned()
            .map(Some)
            .collect(),
        "filter" | "sort" | "fetch" => rel_types(rel.get("input")?)?,
        "project" => {
            let base = rel_types(rel.get("input")?)?;
            let mut cols = base.clone();
            for e in seq(rel.get("expressions")) {
                cols.push(expr_type(e, &base)?);
            }
            cols
        }
        "aggregate" => {
            let base = rel_types(rel.get("input")?)?;
            let groupings = seq(rel.get("groupings"));
            let level = seq(rel.get("groupingExpressions"));
            let keys: &[Value] = if !level.is_empty() {
                level // 0.107: aggregate-level expressions
            } else if groupings.len() <= 1 {
                groupings
                    .first()
                    .map(|g| seq(g.get("groupingExpressions")))
                    .unwrap_or(&[])
            } else {
                return None; // grouping sets in the older encoding
            };
            let mut cols = Vec::new();
            for e in keys {
                cols.push(expr_type(e, &base)?);
            }
            for m in seq(rel.get("measures")) {
                cols.push(m.get("measure")?.get("outputType").cloned());
            }
            cols
        }
        "join" | "cross" => {
            let mut cols = rel_types(rel.get("left")?)?;
            cols.extend(rel_types(rel.get("right")?)?);
            cols
        }
        _ => return None,
    };
    match rel
        .get("common")
        .and_then(|c| c.get("emit"))
        .and_then(|e| e.get("outputMapping"))
    {
        Some(mapping) => mapping
            .as_sequence()?
            .iter()
            .map(|i| cols.get(i.as_u64()? as usize).cloned())
            .collect(),
        None => Some(cols),
    }
}

fn kind_type(kind: &str) -> Value {
    let mut m = Mapping::new();
    m.insert(Value::from(kind), Value::Mapping(Mapping::new()));
    Value::Mapping(m)
}

/// The expression's type: Some(None) when it cannot be determined, None when the plan is
/// malformed.
fn expr_type(e: &Value, inputs: &Types) -> Option<Option<Value>> {
    if let Some(sel) = e.get("selection") {
        if sel.get("outerReference").is_some() {
            return Some(None);
        }
        let field = match sel
            .get("directReference")
            .and_then(|d| d.get("structField"))
        {
            Some(f) if f.get("child").is_none() => f,
            _ => return Some(None),
        };
        let i = field.get("field").and_then(Value::as_u64).unwrap_or(0) as usize;
        return Some(inputs.get(i).cloned().flatten());
    }
    for key in ["scalarFunction", "windowFunction"] {
        if let Some(f) = e.get(key) {
            return Some(f.get("outputType").cloned());
        }
    }
    if let Some(lit) = e.get("literal") {
        return Some(literal_type(lit));
    }
    if let Some(cast) = e.get("cast") {
        return Some(cast.get("type").cloned());
    }
    if let Some(if_then) = e.get("ifThen") {
        return match seq(if_then.get("ifs")).first() {
            Some(first) => expr_type(first.get("then")?, inputs),
            None => expr_type(if_then.get("else")?, inputs),
        };
    }
    if e.get("singularOrList").is_some() {
        return Some(Some(kind_type("bool")));
    }
    if let Some(sq) = e.get("subquery") {
        if let Some(scalar) = sq.get("scalar") {
            return Some(
                rel_types(scalar.get("input")?)?
                    .into_iter()
                    .next()
                    .flatten(),
            );
        }
        return Some(Some(kind_type("bool")));
    }
    Some(None)
}

fn literal_type(lit: &Value) -> Option<Value> {
    let (key, value) = only_entry(lit)?;
    match key {
        "boolean" => Some(kind_type("bool")),
        "i8" | "i16" | "i32" | "i64" | "fp32" | "fp64" | "string" | "date" | "timestamp" => {
            Some(kind_type(key))
        }
        "decimal" => {
            let mut d = Mapping::new();
            d.insert(Value::from("precision"), value.get("precision")?.clone());
            d.insert(Value::from("scale"), value.get("scale")?.clone());
            let mut m = Mapping::new();
            m.insert(Value::from("decimal"), Value::Mapping(d));
            Some(Value::Mapping(m))
        }
        "varChar" => Some(kind_type("varchar")),
        "fixedChar" => Some(kind_type("fixedChar")),
        "null" => Some(value.clone()),
        _ => None,
    }
}
