import { readFileSync } from 'fs';
import * as path from 'path';
import { TableData, Column, ColumnType, CellValue, Row, columnTypeFromName } from './table-data';
import { TestCase, TestSuite } from './test-suite';
import { declaredScales, namedTableSchemas, parsePlanJson, SchemaColumn } from './plan-types';

/**
 * Loads a suite in this repository's metadata format (test-suites/tpch/metadata.yaml and the
 * like): `name`, `version`, `description` and `testCases`, each with `id`, `planBinary`,
 * optional `planJson`, `inputTables` (`name`, `file`) and `expectedOutput`.
 *
 * Inputs and expected outputs are read as scripts/run_plan_compliance.py reads them for the
 * leaderboard:
 *
 * - Input tables are typed from the plan's read schemas (planJson) and keyed by the table name
 *   the plan reads. A first row that names the plan's columns is a header and skipped. Values
 *   are trimmed; an empty value or `null` is NULL. Integers and floats are numbers, booleans
 *   booleans; a decimal stays exact as its text (its column carries precision and scale);
 *   dates and strings are text. Without planJson, an input table is read like an expected
 *   output.
 * - Expected outputs have a typed header (`name:type|...`); values are parsed by type
 *   (integers, floats, booleans), the rest kept as text, as the Java SDK's loader does.
 * - The decimal scales the plan declares (planJson) are attached to each case.
 *
 * A data file read by several cases with the same schema is loaded once, and the cases share
 * that TableData.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Json = any;

/** Whether parsed YAML is in this format (it has a top-level `testCases` list). */
export function isSuiteMetadata(data: Json): boolean {
  return data !== null && typeof data === 'object' && Array.isArray(data.testCases);
}

/** The suite described by parsed metadata, with files relative to basePath. */
export function loadSuiteMetadata(data: Json, basePath: string): TestSuite {
  const tables = new Map<string, TableData>();
  const testCases = (data.testCases as Json[]).map((tc) => loadCase(tc, basePath, tables));
  return new TestSuite(
    {
      name: data.name ?? 'Unnamed Test Suite',
      version: String(data.version ?? '1.0.0'),
      description: data.description,
      category: data.category,
      tags: data.tags,
    },
    testCases
  );
}

function loadCase(tc: Json, base: string, tables: Map<string, TableData>): TestCase {
  const id = tc.id;
  if (!id) throw new Error('test case without id');
  try {
    if (!tc.planBinary) throw new Error('planBinary is required');
    let schemas = new Map<string, SchemaColumn[]>();
    let scales: (number | null)[] | undefined;
    if (tc.planJson) {
      const text = readFileSync(path.resolve(base, tc.planJson), 'utf8');
      schemas = namedTableSchemas(parsePlanJson(text));
      scales = declaredScales(text) ?? undefined;
    }
    const inputData: Record<string, TableData> = {};
    for (const t of (tc.inputTables ?? []) as Json[]) {
      if (!t.name) throw new Error('input table without name');
      if (!t.file) throw new Error(`no file for table ${t.name}`);
      const file = path.resolve(base, t.file);
      const entry = [...schemas.entries()].find(([n]) => n.toLowerCase() === String(t.name).toLowerCase());
      const [name, columns] = entry ?? [t.name, undefined];
      const key = `${file}\u0000${columns ? JSON.stringify(columns) : ''}`;
      let table = tables.get(key);
      if (!table) {
        table = columns ? loadInputTable(file, columns) : loadTypedCsv(file);
        tables.set(key, table);
      }
      inputData[name] = table;
    }
    const expectedPath = tc.expectedOutput ? path.resolve(base, tc.expectedOutput) : undefined;
    let expectedOutput: TableData | undefined;
    if (expectedPath) {
      try {
        expectedOutput = loadTypedCsv(expectedPath);
      } catch (e) {
        if ((e as NodeJS.ErrnoException).code !== 'ENOENT') throw e;
      }
    }
    return {
      id,
      description: tc.description,
      plan: path.resolve(base, tc.planBinary),
      inputData,
      expectedOutput,
      tags: tc.tags,
      skip: tc.skip ?? false,
      skipReason: tc.skipReason,
      declaredScales: scales,
    };
  } catch (e) {
    throw new Error(`test case ${id}: ${(e as Error).message}`);
  }
}

// ── input tables, typed by the plan ────────────────────────────────────────

const kind = (t: Json): string =>
  t !== null && typeof t === 'object' && Object.keys(t).length > 0 ? Object.keys(t)[0] : 'string';

function planColumn(c: SchemaColumn): Column {
  const k = kind(c.type);
  const type: ColumnType =
    {
      i8: ColumnType.INTEGER, i16: ColumnType.INTEGER, i32: ColumnType.INTEGER, i64: ColumnType.INTEGER,
      fp32: ColumnType.FLOAT, fp64: ColumnType.FLOAT, decimal: ColumnType.DECIMAL, bool: ColumnType.BOOLEAN,
      date: ColumnType.DATE, time: ColumnType.TIME, precisionTime: ColumnType.TIME,
      timestamp: ColumnType.TIMESTAMP, timestampTz: ColumnType.TIMESTAMP,
      precisionTimestamp: ColumnType.TIMESTAMP, precisionTimestampTz: ColumnType.TIMESTAMP,
      binary: ColumnType.BINARY, fixedBinary: ColumnType.BINARY,
    }[k] ?? ColumnType.STRING;
  const column: Column = { name: c.name, type, nullable: true };
  if (k === 'decimal') {
    column.precision = Number(c.type.decimal.precision);
    column.scale = Number(c.type.decimal.scale);
  }
  return column;
}

const INTEGER = /^[+-]?\d+$/;
const FLOAT = /^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/;

function parseFloatText(v: string): number | undefined {
  const l = v.toLowerCase().replace(/^\+/, '');
  if (l === 'nan' || l === '-nan') return NaN;
  if (l === 'inf' || l === 'infinity') return Infinity;
  if (l === '-inf' || l === '-infinity') return -Infinity;
  return FLOAT.test(v) ? Number(v) : undefined;
}

function planValue(raw: string, t: Json, where: string): CellValue {
  const v = raw.trim();
  if (v === '' || v.toLowerCase() === 'null') return null;
  const bad = (): never => {
    throw new Error(`${where}: '${v}' is not a ${kind(t)}`);
  };
  switch (kind(t)) {
    case 'i8': case 'i16': case 'i32': case 'i64':
      return INTEGER.test(v) ? Number(v) : bad();
    case 'fp32': case 'fp64': {
      const f = parseFloatText(v);
      return f === undefined ? bad() : kind(t) === 'fp32' ? Math.fround(f) : f;
    }
    case 'decimal':
      return FLOAT.test(v) ? v : bad();
    case 'bool':
      return v.toLowerCase() === 'true';
    default:
      return v;
  }
}

/** A CSV typed by the plan's read schema for the table. */
export function loadInputTable(file: string, columns: SchemaColumn[]): TableData {
  const lines = readLines(file);
  const table = new TableData(columns.map(planColumn));
  if (lines.length === 0) return table;
  const delimiter = detectDelimiter(lines[0]);
  const header = splitLine(lines[0], delimiter).map((h) => h.split(':')[0].toLowerCase());
  const isHeader = header.length === columns.length && header.every((h, i) => h === columns[i].name.toLowerCase());
  const rows: Row[] = [];
  (isHeader ? lines.slice(1) : lines).forEach((line, i) => {
    if (line.trim() === '') return;
    const values = splitLine(line, delimiter);
    if (values.length !== columns.length) {
      throw new Error(`${file} row ${i + 1} has ${values.length} values, expected ${columns.length}`);
    }
    rows.push(values.map((v, j) => planValue(v, columns[j].type, `${file} row ${i + 1}, column ${columns[j].name}`)));
  });
  table.addRows(rows);
  return table;
}

// ── expected outputs (typed header, as the Java SDK's loader reads them) ─────

function headerValue(raw: string, t: string): CellValue {
  const v = raw.trim();
  if (v === '' || v.toLowerCase() === 'null') return null;
  switch (t) {
    case 'int': case 'integer': case 'i8': case 'i16': case 'i32': case 'long': case 'bigint': case 'i64':
      return INTEGER.test(v) ? Number(v) : v;
    case 'float': case 'fp32': case 'double': case 'fp64': case 'decimal':
      return parseFloatText(v) ?? v;
    case 'bool': case 'boolean':
      return v.toLowerCase() === 'true';
    default:
      return v;
  }
}

/** A CSV with a typed header (`name:type|...`), or untyped columns column_1.. of text. */
export function loadTypedCsv(file: string): TableData {
  const lines = readLines(file);
  if (lines.length === 0) throw new Error(`CSV file is empty: ${file}`);
  const delimiter = detectDelimiter(lines[0]);
  const first = splitLine(lines[0], delimiter);
  const typed = first.every((h) => {
    const i = h.indexOf(':');
    return i > 0 && i < h.length - 1;
  });
  const names = typed ? first.map((h) => h.slice(0, h.indexOf(':')).trim()) : first.map((_, i) => `column_${i + 1}`);
  const types = typed ? first.map((h) => h.slice(h.indexOf(':') + 1).trim().toLowerCase()) : first.map(() => 'string');
  const rows: Row[] = [];
  (typed ? lines.slice(1) : lines).forEach((line, i) => {
    if (line.trim() === '') return;
    const values = splitLine(line, delimiter);
    if (values.length !== names.length) {
      throw new Error(`CSV row ${i + 1} in ${file} has ${values.length} values but expected ${names.length}`);
    }
    rows.push(values.map((v, j) => headerValue(v, types[j])));
  });
  return new TableData(
    names.map((n, i) => ({ name: n, type: columnTypeFromName(types[i]), nullable: true })),
    rows
  );
}

// ── CSV helpers ─────────────────────────────────────────────────────────────

function readLines(file: string): string[] {
  const lines = readFileSync(file, 'utf8').split(/\r?\n/);
  while (lines.length > 0 && lines[lines.length - 1] === '') lines.pop();
  return lines;
}

function detectDelimiter(line: string): string {
  const count = (c: string) => line.split(c).length - 1;
  return count('|') > count(',') ? '|' : ',';
}

/** One line, split on the delimiter outside double quotes; "" inside quotes is a quote. */
function splitLine(line: string, delimiter: string): string[] {
  const values: string[] = [];
  let current = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        current += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (ch === delimiter && !inQuotes) {
      values.push(current.trim());
      current = '';
    } else {
      current += ch;
    }
  }
  values.push(current.trim());
  return values.map((v) => (v.length >= 2 && v.startsWith('"') && v.endsWith('"') ? v.slice(1, -1) : v));
}
