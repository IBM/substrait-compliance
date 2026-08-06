import { ComplianceEngine, EngineInfo, EngineCapabilities } from '../src/engine';
import { ComplianceResult, TestStatus } from '../src/result';
import { TableData, Column, ColumnType } from '../src/table-data';
import { ComplianceRunner } from '../src/runner';
import { TestSuite, TestCase } from '../src/test-suite';

// ── Pass-through engine ───────────────────────────────────────────────────────

/**
 * Returns the pre-registered expected output for a test, keyed by test ID
 * encoded in the plan bytes.  This lets the runner compare actual vs expected
 * and verify the comparator path.
 */
class PassThroughEngine implements ComplianceEngine {
  constructor(private outputs: Map<string, TableData>) {}

  getInfo(): EngineInfo {
    return { name: 'PassThrough', version: '0.0.0', vendor: 'Test' };
  }

  getCapabilities(): EngineCapabilities {
    return { supportedRelations: [], supportedFunctions: [], supportedTypes: [] };
  }

  async executePlan(
    planBytes: Uint8Array,
    _inputData: Map<string, TableData>
  ): Promise<ComplianceResult> {
    const testId = Buffer.from(planBytes).toString('utf-8');
    const output = this.outputs.get(testId);
    return new ComplianceResult(testId, TestStatus.PASSED, output);
  }

  async validatePlan(_planBytes: Uint8Array): Promise<ComplianceResult> {
    return new ComplianceResult('validation', TestStatus.PASSED);
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeIntTable(rows: [number, number][]): TableData {
  const cols: Column[] = [
    { name: 'a', type: ColumnType.INTEGER, nullable: true },
    { name: 'b', type: ColumnType.INTEGER, nullable: true },
  ];
  const data = rows.map(([a, b]) => [a, b]);
  return new TableData(cols, data);
}

function makeDoubleTable(vals: number[]): TableData {
  const cols: Column[] = [{ name: 'val', type: ColumnType.FLOAT, nullable: true }];
  return new TableData(cols, vals.map(v => [v]));
}

/** Build an in-memory TestSuite from a map of id → expected TableData. */
function makeSuite(cases: Map<string, TableData>): TestSuite {
  const testCases: TestCase[] = [];
  for (const [id, expected] of cases) {
    const planPath = id; // runner reads this as a file path — we intercept in engine
    testCases.push({
      id,
      plan: planPath,
      expectedOutput: expected,
    });
  }
  return new TestSuite(
    { name: 'pass-through', version: '0.0.0' },
    testCases
  );
}

/**
 * Subclass the runner to skip the file-read step; inject plan bytes directly.
 * We override runTestCase to pass plan bytes as the encoded test ID.
 */
class InMemoryRunner extends ComplianceRunner {
  async runTestCase(testCase: TestCase): Promise<ComplianceResult> {
    const planBytes = Buffer.from(testCase.id, 'utf-8');
    const inputMap = testCase.inputData
      ? new Map(Object.entries(testCase.inputData))
      : new Map<string, TableData>();

    const result = await (this as any).engine.executePlan(planBytes, inputMap);

    if (testCase.expectedOutput && result.outputData) {
      const matches = (this as any).comparator.compare(testCase.expectedOutput, result.outputData);
      if (!matches && result.status === TestStatus.PASSED) {
        return new ComplianceResult(
          testCase.id, TestStatus.FAILED, result.outputData,
          'Output does not match expected result'
        );
      }
    }

    return new ComplianceResult(
      testCase.id, result.status, result.outputData,
      result.errorMessage, result.errorDetails, result.executionTimeMs
    );
  }
}

function makeRunner(outputs: Map<string, TableData>): InMemoryRunner {
  return new InMemoryRunner(new PassThroughEngine(outputs));
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ComplianceRunner — pass-through integration', () => {

  test('pass-through engine scores 100%', async () => {
    const cases = new Map<string, TableData>([
      ['q01', makeIntTable([[1, 2], [3, 4]])],
      ['q02', makeIntTable([[10, 20]])],
      ['q03', makeIntTable([[0, 0], [1, 1]])],
    ]);
    const suite  = makeSuite(cases);
    const runner = makeRunner(cases);

    const report = await runner.runTestSuite(suite);

    expect(report.totalCount()).toBe(3);
    expect(report.passedCount()).toBe(3);
    expect(report.failedCount()).toBe(0);
    expect(report.passRate()).toBeCloseTo(100, 1);
  });

  test('value mismatch is detected as FAILED', async () => {
    const expected = makeIntTable([[1, 2]]);
    const wrong    = makeIntTable([[1, 99]]);   // col b is wrong

    const suite  = makeSuite(new Map([['mismatch', expected]]));
    const runner = makeRunner(new Map([['mismatch', wrong]]));

    const report = await runner.runTestSuite(suite);

    expect(report.failedCount()).toBe(1);
    expect(report.passedCount()).toBe(0);
  });

  test('epsilon comparison passes for values within 1e-10', async () => {
    const expected = makeDoubleTable([1.0]);
    const close    = makeDoubleTable([1.0 + 5e-11]);  // within 1e-10 tolerance

    const suite  = makeSuite(new Map([['close', expected]]));
    const runner = makeRunner(new Map([['close', close]]));

    const report = await runner.runTestSuite(suite);

    expect(report.passedCount()).toBe(1);
  });

  test('epsilon comparison fails for values outside 1e-10', async () => {
    const expected = makeDoubleTable([1.0]);
    const far      = makeDoubleTable([2.0]);   // clearly different

    const suite  = makeSuite(new Map([['far', expected]]));
    const runner = makeRunner(new Map([['far', far]]));

    const report = await runner.runTestSuite(suite);

    expect(report.failedCount()).toBe(1);
  });

  test('row-count mismatch is detected', async () => {
    const expected = makeIntTable([[1, 2], [3, 4]]);
    const shorter  = makeIntTable([[1, 2]]);         // one row missing

    const suite  = makeSuite(new Map([['rows', expected]]));
    const runner = makeRunner(new Map([['rows', shorter]]));

    const report = await runner.runTestSuite(suite);

    expect(report.failedCount()).toBe(1);
  });

});
