/**
 * Query-output comparison: the vectors shared with the leaderboard's comparators
 * (sdk/conformance/comparison-vectors.json), checked against ResultComparator.
 */
import { readFileSync } from 'fs';
import { join } from 'path';
import { ResultComparator } from '../src/runner';
import { CellValue, TableData, columnTypeFromName } from '../src/table-data';

interface VectorTable { names: string[]; types: string[]; rows: unknown[][] }
interface Vector { name: string; declaredScales?: (number | null)[]; expected: VectorTable; actual: VectorTable; match: boolean }

const vectors: Vector[] = JSON.parse(
  readFileSync(join(__dirname, '..', '..', 'conformance', 'comparison-vectors.json'), 'utf8')
).vectors;

function decode(v: unknown): CellValue {
  if (v !== null && typeof v === 'object') {
    const o = v as { double?: string; decimal?: string };
    if (o.double !== undefined) return Number(o.double);
    if (o.decimal !== undefined) return Number(o.decimal);
    throw new Error(`unexpected value ${JSON.stringify(v)}`);
  }
  return v as CellValue;
}

function table(t: VectorTable): TableData {
  return new TableData(
    t.names.map((name, i) => ({ name, type: columnTypeFromName(t.types[i]) })),
    t.rows.map((r) => r.map(decode))
  );
}

describe('ResultComparator and the shared comparison vectors', () => {
  it('reads the vectors', () => {
    expect(vectors.length).toBeGreaterThan(0);
  });

  it.each(vectors.map((v) => [v.name, v] as [string, Vector]))('%s', (_name, v) => {
    const mismatch = new ResultComparator().describeMismatch(table(v.expected), table(v.actual), v.declaredScales ?? []);
    expect(mismatch === null).toBe(v.match);
  });
});
