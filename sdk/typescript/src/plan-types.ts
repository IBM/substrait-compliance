import { existsSync, readFileSync } from 'fs';

/**
 * The output column types a Substrait plan declares, from its JSON form, so decimal columns
 * can be compared at the scale the plan declares (ResultComparator). Mirrors output_types /
 * declared_scales in scripts/run_plan_compliance.py.
 *
 * Types are propagated relation by relation: a read's base schema; filter, sort and fetch
 * keep their input's columns, project adds its expressions; an aggregate yields its grouping
 * expressions, then its measures; joins concatenate both sides; emit mappings select and
 * reorder. An expression's type is its declared outputType (functions, aggregates), the
 * referenced column's type, a cast's target or a literal's type.
 */

type Json = any; // eslint-disable-line @typescript-eslint/no-explicit-any

class UnknownPlan extends Error {}

function has(v: Json, key: string): boolean {
  return v !== null && typeof v === 'object' && !Array.isArray(v) && v[key] !== undefined && v[key] !== null;
}

function req(v: Json, key: string): Json {
  if (!has(v, key)) throw new UnknownPlan(key);
  return v[key];
}

function list(v: Json, key: string): Json[] {
  return has(v, key) && Array.isArray(v[key]) ? v[key] : [];
}

function onlyEntry(v: Json): [string, Json] {
  if (v === null || typeof v !== 'object' || Array.isArray(v)) throw new UnknownPlan('relation');
  const keys = Object.keys(v);
  if (keys.length === 0) throw new UnknownPlan('relation');
  return [keys[0], v[keys[0]]];
}

const kindType = (kind: string): Json => ({ [kind]: {} });

function relTypes(node: Json): (Json | null)[] {
  const [kind, rel] = onlyEntry(node);
  let cols: (Json | null)[];
  switch (kind) {
    case 'read': {
      const types = req(req(req(rel, 'baseSchema'), 'struct'), 'types');
      if (!Array.isArray(types)) throw new UnknownPlan('types');
      cols = [...types];
      break;
    }
    case 'filter':
    case 'sort':
    case 'fetch':
      cols = relTypes(req(rel, 'input'));
      break;
    case 'project': {
      const base = relTypes(req(rel, 'input'));
      cols = [...base, ...list(rel, 'expressions').map((e) => exprType(e, base))];
      break;
    }
    case 'aggregate': {
      const base = relTypes(req(rel, 'input'));
      const groupings = list(rel, 'groupings');
      let keys = list(rel, 'groupingExpressions'); // 0.107: aggregate-level expressions
      if (keys.length === 0) {
        if (groupings.length > 1) throw new UnknownPlan('grouping sets in the older encoding');
        keys = groupings.length === 1 ? list(groupings[0], 'groupingExpressions') : [];
      }
      cols = [
        ...keys.map((e) => exprType(e, base)),
        ...list(rel, 'measures').map((m) => (has(req(m, 'measure'), 'outputType') ? m.measure.outputType : null)),
      ];
      break;
    }
    case 'join':
    case 'cross':
      cols = [...relTypes(req(rel, 'left')), ...relTypes(req(rel, 'right'))];
      break;
    default:
      throw new UnknownPlan(kind);
  }
  const mapping = has(rel, 'common') && has(rel.common, 'emit') && has(rel.common.emit, 'outputMapping')
    ? rel.common.emit.outputMapping
    : undefined;
  if (mapping === undefined) return cols;
  if (!Array.isArray(mapping)) throw new UnknownPlan('outputMapping');
  return mapping.map((i: Json) => {
    if (typeof i !== 'number' || i < 0 || i >= cols.length) throw new UnknownPlan('outputMapping');
    return cols[i];
  });
}

function exprType(e: Json, inputs: (Json | null)[]): Json | null {
  if (has(e, 'selection')) {
    const sel = e.selection;
    const sf = has(sel, 'directReference') && has(sel.directReference, 'structField') ? sel.directReference.structField : undefined;
    if (has(sel, 'outerReference') || sf === undefined || has(sf, 'child')) return null;
    const i = typeof sf.field === 'number' ? sf.field : 0;
    return i < inputs.length ? inputs[i] : null;
  }
  for (const key of ['scalarFunction', 'windowFunction']) {
    if (has(e, key)) return has(e[key], 'outputType') ? e[key].outputType : null;
  }
  if (has(e, 'literal')) return literalType(e.literal);
  if (has(e, 'cast')) return has(e.cast, 'type') ? e.cast.type : null;
  if (has(e, 'ifThen')) {
    const ifs = list(e.ifThen, 'ifs');
    return ifs.length > 0 ? exprType(req(ifs[0], 'then'), inputs) : exprType(req(e.ifThen, 'else'), inputs);
  }
  if (has(e, 'singularOrList')) return kindType('bool');
  if (has(e, 'subquery')) {
    if (has(e.subquery, 'scalar')) {
      const out = relTypes(req(e.subquery.scalar, 'input'));
      return out.length > 0 ? out[0] : null;
    }
    return kindType('bool');
  }
  return null;
}

function literalType(lit: Json): Json | null {
  const [key, value] = onlyEntry(lit);
  switch (key) {
    case 'boolean':
      return kindType('bool');
    case 'i8': case 'i16': case 'i32': case 'i64': case 'fp32': case 'fp64': case 'string': case 'date': case 'timestamp':
      return kindType(key);
    case 'decimal':
      return has(value, 'precision') && has(value, 'scale')
        ? { decimal: { precision: value.precision, scale: value.scale } }
        : null;
    case 'varChar':
      return kindType('varchar');
    case 'fixedChar':
      return kindType('fixedChar');
    case 'null':
      return value;
    default:
      return null;
  }
}

/** The plan root's output types (an entry is null where it cannot be determined). */
export function outputTypes(plan: Json): (Json | null)[] {
  for (const r of list(plan, 'relations')) {
    if (has(r, 'root')) return relTypes(req(r.root, 'input'));
  }
  throw new UnknownPlan('root');
}

/**
 * The scale of each output column the plan declares as decimal (null for other columns), or
 * null when the plan's output types cannot be worked out. The JSON may start with // comment
 * lines, as the suites' plan files do.
 */
export function declaredScales(planJson: string): (number | null)[] | null {
  try {
    const text = planJson.split('\n').filter((l) => !l.trim().startsWith('//')).join('\n');
    return outputTypes(JSON.parse(text)).map((t) =>
      has(t, 'decimal') && typeof t.decimal.scale === 'number' ? t.decimal.scale : null
    );
  } catch {
    return null;
  }
}

/**
 * The declared scales of the plan whose binary is at planPath, read from the JSON form beside
 * it (plans/q01.bin -> plans/q01.json), or null if there is none.
 */
export function declaredScalesBeside(planPath: string): (number | null)[] | null {
  if (!planPath.endsWith('.bin')) return null;
  const json = planPath.slice(0, -4) + '.json';
  return existsSync(json) ? declaredScales(readFileSync(json, 'utf8')) : null;
}
