/** The decimal scales a plan declares, as scripts/run_plan_compliance.py works them out. */
import { readFileSync } from 'fs';
import { join } from 'path';
import { declaredScales, declaredScalesBeside } from '../src/plan-types';

const tpch = join(__dirname, '..', '..', '..', 'test-suites', 'tpch');
const plan = (rel: string) => readFileSync(join(tpch, rel), 'utf8');

describe('declaredScales', () => {
  it("gives q01's decimal aggregates", () => {
    expect(declaredScales(plan('plans/q01.json'))).toEqual([null, null, 2, 2, 4, 6, 2, 2, 2, null]);
  });

  it('gives the same scales for both TPC-H encodings', () => {
    for (let q = 1; q <= 22; q++) {
      const name = `q${String(q).padStart(2, '0')}.json`;
      const current = declaredScales(plan(`plans/${name}`));
      expect(current).not.toBeNull();
      expect(declaredScales(plan(`plans-substrait-0.81/${name}`))).toEqual(current);
    }
  });

  it('gives null for a relation it does not know', () => {
    expect(declaredScales('{"relations":[{"root":{"input":{"exchange":{}}}}]}')).toBeNull();
  });

  it('reads the JSON beside a plan binary', () => {
    expect(declaredScalesBeside(join(tpch, 'plans', 'q01.bin'))?.[6]).toBe(2);
    expect(declaredScalesBeside(join(tpch, 'plans', 'no-such-plan.bin'))).toBeNull();
  });
});
