import { mkdtempSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import * as path from 'path';
import { loadTestSuite, ColumnType } from '../src';
import { loadInputTable, loadTypedCsv } from '../src/suite-metadata';

const TPCH = path.resolve(__dirname, '../../../test-suites/tpch/metadata.yaml');

describe('repository suite metadata', () => {
  it('reads input tables typed by the plan, keyed by its table names', async () => {
    const suite = await loadTestSuite(TPCH);
    const q01 = suite.testCases.find((t) => t.id === 'q01')!;
    const lineitem = q01.inputData!['LINEITEM'];
    expect(lineitem.rowCount()).toBe(60175);
    const quantity = lineitem.getColumns()[4];
    expect(quantity).toMatchObject({ name: 'L_QUANTITY', type: ColumnType.DECIMAL, precision: 15, scale: 2 });
    expect(typeof lineitem.getRows()[0][0]).toBe('number');
    expect(typeof lineitem.getRows()[0][4]).toBe('string'); // a decimal stays exact
    expect(q01.expectedOutput!.rowCount()).toBe(4);
    expect(q01.declaredScales).toEqual([null, null, 2, 2, 4, 6, 2, 2, 2, null]);
    expect(q01.plan.endsWith(path.join('plans', 'q01.bin'))).toBe(true);
    // Cases reading the same file with the same schema share the table.
    expect(suite.testCases.find((t) => t.id === 'q06')!.inputData!['LINEITEM']).toBe(lineitem);
  });

  it('skips a header row, honours quotes and reads NULLs', () => {
    const file = path.join(mkdtempSync(path.join(tmpdir(), 'sc-')), 't.csv');
    writeFileSync(file, 'id,name,ok\n1,"a, b",true\n2,null,\n');
    const t = loadInputTable(file, [
      { name: 'ID', type: { i32: {} } },
      { name: 'NAME', type: { string: {} } },
      { name: 'OK', type: { bool: {} } },
    ]);
    expect(t.getRows()).toEqual([
      [1, 'a, b', true],
      [2, null, null],
    ]);
    expect(() => loadInputTable(file, [{ name: 'ID', type: { i32: {} } }])).toThrow(/expected 1/);
  });

  it('parses expected values by their header type', () => {
    const file = path.join(mkdtempSync(path.join(tmpdir(), 'sc-')), 'e.csv');
    writeFileSync(file, 'n:bigint|x:double|b:boolean|s:varchar\n7|2.5|TRUE|001\n|null|false|x\n');
    expect(loadTypedCsv(file).getRows()).toEqual([
      [7, 2.5, true, '001'],
      [null, null, false, 'x'],
    ]);
  });
});
